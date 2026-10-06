package com.educationerp.asset;

import com.educationerp.academic.Department;
import com.educationerp.academic.DepartmentRepository;
import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.auth.user.User;
import com.educationerp.auth.user.UserRepository;
import com.educationerp.common.api.PageResponse;
import com.educationerp.common.error.AppException;
import com.educationerp.common.numbering.DocumentSequence;
import com.educationerp.common.numbering.SequenceNumberGenerator;
import com.educationerp.hr.Employee;
import com.educationerp.hr.EmployeeRepository;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import com.educationerp.student.Student;
import com.educationerp.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * The register of things the school owns.
 *
 * <p>Two things are kept true here, and both cost something to maintain. An asset is either on
 * somebody's desk or on the shelf, never both, because the database refuses to hold a row that
 * claims otherwise. And every spell of custody is a row, because a register that only remembers
 * the present cannot answer the question that matters when something goes missing.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AssetService {

    private final AssetCategoryRepository categories;
    private final AssetRepository assets;
    private final AssetAssignmentRepository assignments;
    private final AssetMaintenanceRepository maintenance;
    private final UserRepository users;
    private final EmployeeRepository employees;
    private final StudentRepository students;
    private final DepartmentRepository departments;
    private final SequenceNumberGenerator numbers;
    private final InstitutionService institutions;
    private final AuthorizationChecker auth;
    private final AuditService audit;

    // ---------------------------------------------------------------- categories

    public AssetDtos.CategoryRow createCategory(AssetDtos.CategoryRequest request) {
        guard("ASSET_MANAGE");
        requireCategoryCodeFree(request.code(), null);
        AssetCategory category = new AssetCategory();
        category.setCode(request.code().trim());
        category.setName(request.name().trim());
        category.setDepreciationRate(request.depreciationRate());
        category.setUsefulLifeYears(request.usefulLifeYears());
        return categoryRow(categories.save(category));
    }

    public AssetDtos.CategoryRow updateCategory(UUID id, AssetDtos.CategoryRequest request) {
        guard("ASSET_MANAGE");
        AssetCategory category = category(id);
        requireCategoryCodeFree(request.code(), id);
        category.setCode(request.code().trim());
        category.setName(request.name().trim());
        category.setDepreciationRate(request.depreciationRate());
        category.setUsefulLifeYears(request.usefulLifeYears());
        return categoryRow(category);
    }

    private void requireCategoryCodeFree(String code, UUID self) {
        categories.findByCodeIgnoreCase(code.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(self)) {
                throw AppException.duplicate("An asset category with code " + code.trim()
                        + " already exists.");
            }
        });
    }

    public List<AssetDtos.CategoryRow> listCategories() {
        guard("ASSET_READ");
        return categories.findAll().stream().map(this::categoryRow).toList();
    }

    // -------------------------------------------------------------------- assets

    public AssetDtos.AssetRow createAsset(AssetDtos.AssetRequest request) {
        guard("ASSET_MANAGE");
        Asset asset = new Asset();
        asset.setAssetNumber(numbers.next(DocumentSequence.Kind.ASSET));
        asset.setStatus(Asset.Status.AVAILABLE);
        apply(asset, request);
        AssetDtos.AssetRow saved = assetRow(assets.save(asset));
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .module("ASSETS")
                .entityType("Asset")
                .entityId(asset.getId().toString())
                .summary("Asset " + asset.getAssetNumber() + " (" + asset.getName()
                        + ") added to the register")
                .build());
        return saved;
    }

    public AssetDtos.AssetRow updateAsset(UUID id, AssetDtos.AssetRequest request) {
        guard("ASSET_MANAGE");
        Asset asset = asset(id);
        // Editing the description of something that is out on loan is fine; quietly
        // re-labelling a held asset is not, so the holder fields are not editable here.
        apply(asset, request);
        if (request.conditionStatus() != null) {
            asset.setConditionStatus(request.conditionStatus());
        }
        return assetRow(asset);
    }

    private void apply(Asset asset, AssetDtos.AssetRequest request) {
        asset.setName(request.name().trim());
        asset.setDescription(request.description());
        asset.setSerialNumber(request.serialNumber());
        asset.setBrand(request.brand());
        asset.setModel(request.model());
        asset.setPurchaseDate(request.purchaseDate());
        asset.setPurchaseCost(request.purchaseCost());
        asset.setWarrantyExpiry(request.warrantyExpiry());
        asset.setLocation(request.location());
        asset.setNotes(request.notes());
        asset.setCategory(request.categoryId() == null ? null : category(request.categoryId()));
        asset.setDepartment(request.departmentId() == null ? null
                : department(request.departmentId()));
    }

    public PageResponse<AssetDtos.AssetRow> searchAssets(UUID categoryId, Asset.Status status,
                                                         UUID departmentId, String term,
                                                         Pageable pageable) {
        guard("ASSET_READ");
        String like = term == null || term.isBlank() ? null
                : "%" + term.trim().toLowerCase() + "%";
        Page<Asset> page = assets.search(categoryId, status, departmentId, like, pageable);
        return PageResponse.from(page, this::assetRow);
    }

    public AssetDtos.AssetDetail detail(UUID id) {
        guard("ASSET_READ");
        Asset asset = asset(id);
        return new AssetDtos.AssetDetail(assetRow(asset),
                assignments.historyFor(id).stream().map(this::assignmentRow).toList(),
                maintenance.findByAssetIdOrderByScheduledForDesc(id).stream()
                        .map(this::maintenanceRow).toList());
    }

    // --------------------------------------------------------------- assignments

    /**
     * Put an asset in somebody's hands.
     *
     * <p>Refused while it is being serviced or has been written off, and refused if it is
     * already out: a second holder would mean two people believe they have the same laptop.
     */
    public AssetDtos.AssignmentRow assign(UUID assetId, AssetDtos.AssignmentRequest request) {
        guard("ASSET_MANAGE");
        Asset asset = asset(assetId);
        if (asset.getStatus() == Asset.Status.ASSIGNED) {
            throw AppException.duplicate(asset.getAssetNumber() + " is already out to "
                    + asset.holderName() + ".");
        }
        if (asset.getStatus() != Asset.Status.AVAILABLE) {
            throw AppException.rule("An asset that is "
                    + asset.getStatus().name().toLowerCase().replace('_', ' ')
                    + " cannot be assigned.");
        }
        AssignmentHolder holder = resolveHolder(request);

        AssetAssignment assignment = new AssetAssignment();
        assignment.setAsset(asset);
        assignment.setHolderType(request.holderType());
        assignment.setHolderUser(holder.user());
        assignment.setHolderEmployee(holder.employee());
        assignment.setHolderStudent(holder.student());
        assignment.setHolderName(holder.name());
        assignment.setAssignedBy(auth.requireUser().userId());
        assignment.setConditionOut(asset.getConditionStatus());
        assignment.setNotes(request.notes());
        assignments.save(assignment);

        asset.setStatus(Asset.Status.ASSIGNED);
        // An asset can only be out once, so there is no stale id to clear here.
        asset.setAssignedUser(holder.user());
        asset.setAssignedEmployee(holder.employee());
        asset.setAssignedStudent(holder.student());
        asset.setAssignedAt(assignment.getAssignedAt());
        // The holder's own department is the useful default for the asset's location.
        if (holder.department() != null) {
            asset.setDepartment(holder.department());
        }
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .module("ASSETS")
                .entityType("Asset")
                .entityId(asset.getId().toString())
                .summary(asset.getAssetNumber() + " assigned to " + holder.name())
                .build());
        return assignmentRow(assignment);
    }

    /** Take an asset back, recording what it looked like on return. */
    public AssetDtos.AssignmentRow returnAsset(UUID assetId, AssetDtos.ReturnRequest request) {
        guard("ASSET_MANAGE");
        Asset asset = asset(assetId);
        if (asset.getStatus() != Asset.Status.ASSIGNED) {
            throw AppException.rule(asset.getAssetNumber() + " is not out to anybody.");
        }
        AssetAssignment assignment = assignments.findByAssetIdAndReturnedAtIsNull(assetId)
                .orElseThrow(() -> AppException.rule("No open assignment for "
                        + asset.getAssetNumber() + "; check the register by hand."));
        assignment.setReturnedAt(Instant.now());
        if (request.conditionIn() != null) {
            assignment.setConditionIn(request.conditionIn());
            asset.setConditionStatus(request.conditionIn());
        }
        if (request.notes() != null) {
            assignment.setNotes(request.notes());
        }
        asset.clearHolder();
        asset.setStatus(Asset.Status.AVAILABLE);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .module("ASSETS")
                .entityType("Asset")
                .entityId(asset.getId().toString())
                .summary(asset.getAssetNumber() + " returned by " + assignment.getHolderName())
                .build());
        return assignmentRow(assignment);
    }

    public List<AssetDtos.AssignmentRow> history(UUID assetId) {
        guard("ASSET_READ");
        asset(assetId);
        return assignments.historyFor(assetId).stream().map(this::assignmentRow).toList();
    }

    // ------------------------------------------------------ lost, found, disposed

    /**
     * Record that the institution cannot find an asset.
     *
     * <p>Only an asset that is on a shelf or in somebody's hands can go missing. A projector
     * nobody can locate may well be in pieces on a bench, so servicing has to be settled first,
     * and there is nothing left to lose once the register has written the thing off.
     */
    public AssetDtos.AssetRow markLost(UUID assetId, AssetDtos.MarkLostRequest request) {
        guard("ASSET_MANAGE");
        Asset asset = asset(assetId);
        switch (asset.getStatus()) {
            case LOST -> throw AppException.rule(asset.getAssetNumber()
                    + " is already reported missing.");
            case DISPOSED -> throw AppException.rule(asset.getAssetNumber()
                    + " has been written off and is no longer on the register.");
            case IN_MAINTENANCE -> throw AppException.rule(asset.getAssetNumber()
                    + " is being serviced; settle the job before reporting it missing.");
            case AVAILABLE, ASSIGNED -> {
                // Nothing to refuse: the two states an asset can actually go missing from.
            }
        }
        String reason = request.reason().trim();
        closeOpenAssignment(asset, "Reported missing: " + reason);
        asset.setStatus(Asset.Status.LOST);
        asset.setLostAt(Instant.now());
        asset.setLostReason(reason);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .module("ASSETS")
                .entityType("Asset")
                .entityId(asset.getId().toString())
                .summary(asset.getAssetNumber() + " reported missing: " + reason)
                .build());
        return assetRow(asset);
    }

    /**
     * Find a lost asset again.
     *
     * <p>The account of the disappearance stays on the row: a laptop that once walked off is a
     * different thing from one that never did, and whoever issues it next should know. The
     * status, not the note, is what says whether the asset is missing right now.
     */
    public AssetDtos.AssetRow markFound(UUID assetId, AssetDtos.FoundRequest request) {
        guard("ASSET_MANAGE");
        Asset asset = asset(assetId);
        if (asset.getStatus() != Asset.Status.LOST) {
            throw AppException.rule(asset.getAssetNumber() + " is not reported missing.");
        }
        asset.setStatus(Asset.Status.AVAILABLE);
        asset.clearHolder();
        if (request.location() != null && !request.location().isBlank()) {
            asset.setLocation(request.location().trim());
        }
        if (request.conditionStatus() != null) {
            asset.setConditionStatus(request.conditionStatus());
        }
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .module("ASSETS")
                .entityType("Asset")
                .entityId(asset.getId().toString())
                .summary(asset.getAssetNumber() + " found again")
                .build());
        return assetRow(asset);
    }

    /**
     * Write an asset off the register for good.
     *
     * <p>The row survives with its disposal on it, because a register that deletes what it has
     * finished with cannot later answer what happened to it or for how much it went. An asset
     * out on loan is written off with the loan closed, so nobody is shown as holding something
     * the institution no longer owns.
     */
    public AssetDtos.AssetRow dispose(UUID assetId, AssetDtos.DisposeRequest request) {
        guard("ASSET_MANAGE");
        Asset asset = asset(assetId);
        if (asset.getStatus() == Asset.Status.DISPOSED) {
            throw AppException.rule(asset.getAssetNumber() + " has already been written off.");
        }
        if (asset.getStatus() == Asset.Status.IN_MAINTENANCE) {
            throw AppException.rule(asset.getAssetNumber()
                    + " is being serviced; finish or cancel the job before writing it off.");
        }
        closeOpenAssignment(asset, "Written off (" + request.method() + ")");
        asset.clearHolder();
        asset.setStatus(Asset.Status.DISPOSED);
        asset.setDisposedAt(Instant.now());
        asset.setDisposalMethod(request.method());
        asset.setDisposalValue(request.value());
        asset.setDisposalNotes(request.notes() == null ? null : request.notes().trim());
        audit.record(AuditEvent.builder()
                .action(AuditAction.DELETE)
                .module("ASSETS")
                .entityType("Asset")
                .entityId(asset.getId().toString())
                .summary(asset.getAssetNumber() + " written off (" + request.method()
                        + (request.value() == null ? "" : ", " + request.value()) + ")")
                .build());
        return assetRow(asset);
    }

    /** Close the loan if the asset is out, so custody history stops at the moment it ended. */
    private void closeOpenAssignment(Asset asset, String how) {
        if (asset.getStatus() != Asset.Status.ASSIGNED) {
            return;
        }
        assignments.findByAssetIdAndReturnedAtIsNull(asset.getId()).ifPresent(assignment -> {
            assignment.setReturnedAt(Instant.now());
            if (assignment.getNotes() == null || assignment.getNotes().isBlank()) {
                assignment.setNotes(how);
            } else {
                assignment.setNotes(assignment.getNotes() + " | " + how);
            }
            assignments.save(assignment);
        });
    }

    /** What one person is currently holding. */
    public PageResponse<AssetDtos.AssetRow> heldBy(AssetDtos.AssignmentRequest query,
                                                   Pageable pageable) {
        guard("ASSET_READ");
        AssignmentHolder holder = resolveHolder(query);
        Page<AssetAssignment> page = openAssignments(query.holderType(), holder, pageable);
        return PageResponse.from(page, open -> assetRow(open.getAsset(), open.getHolderName()));
    }

    /**
     * Whose things to list.
     *
     * <p>The holder identifier has to be matched against the column for its own kind of
     * holder. A single query naming all three id columns cannot be typed by the database
     * when any of them is empty, so the cases are asked separately.
     */
    private Page<AssetAssignment> openAssignments(AssetAssignment.HolderType type,
                                                  AssignmentHolder holder,
                                                  Pageable pageable) {
        UUID id = holder.identifier();
        if (id == null) {
            return assignments.findOpenOfType(type, pageable);
        }
        return switch (type) {
            case USER -> assignments.findOpenHeldByHolderUser(type, id, pageable);
            case EMPLOYEE -> assignments.findOpenHeldByHolderEmployee(type, id, pageable);
            case STUDENT -> assignments.findOpenHeldByHolderStudent(type, id, pageable);
            // An external holder has no directory row, so only the unfiltered list applies.
            case EXTERNAL -> assignments.findOpenOfType(type, pageable);
        };
    }

    // -------------------------------------------------------------- maintenance

    public AssetDtos.MaintenanceRow schedule(UUID assetId, AssetDtos.MaintenanceRequest request) {
        guard("ASSET_MANAGE");
        Asset asset = asset(assetId);
        AssetMaintenance job = new AssetMaintenance();
        job.setAsset(asset);
        job.setType(request.type());
        job.setDescription(request.description());
        job.setVendor(request.vendor());
        job.setCost(request.cost());
        job.setPerformedBy(request.performedBy());
        job.setScheduledFor(request.scheduledFor());
        job.setStatus(AssetMaintenance.Status.SCHEDULED);
        return maintenanceRow(maintenance.save(job));
    }

    /**
     * Begin work.
     *
     * <p>Being in maintenance and being in somebody's hands cannot both be true, so a job on an
     * asset that is out has to wait until the asset comes back.
     */
    public AssetDtos.MaintenanceRow start(UUID jobId, AssetDtos.StartMaintenanceRequest request) {
        guard("ASSET_MANAGE");
        AssetMaintenance job = job(jobId);
        if (job.getStatus() != AssetMaintenance.Status.SCHEDULED) {
            throw AppException.rule("That job is already " + job.getStatus().name().toLowerCase() + ".");
        }
        if (job.getAsset().getStatus() == Asset.Status.ASSIGNED) {
            throw AppException.rule("Take the asset back before sending it for servicing.");
        }
        job.setStatus(AssetMaintenance.Status.IN_PROGRESS);
        job.setStartedAt(Instant.now());
        if (request != null && request.performedBy() != null) {
            job.setPerformedBy(request.performedBy());
        }
        job.getAsset().setStatus(Asset.Status.IN_MAINTENANCE);
        return maintenanceRow(job);
    }

    public AssetDtos.MaintenanceRow complete(UUID jobId, AssetDtos.CompleteMaintenanceRequest request) {
        guard("ASSET_MANAGE");
        AssetMaintenance job = job(jobId);
        if (job.getStatus() != AssetMaintenance.Status.IN_PROGRESS
                && job.getStatus() != AssetMaintenance.Status.SCHEDULED) {
            throw AppException.rule("That job cannot be completed from "
                    + job.getStatus().name().toLowerCase() + ".");
        }
        job.setStatus(AssetMaintenance.Status.COMPLETED);
        job.setCompletedAt(Instant.now());
        job.setStartedAt(job.getStartedAt() == null ? Instant.now() : job.getStartedAt());
        if (request.cost() != null) {
            job.setCost(request.cost());
        }
        job.setPerformedBy(request.performedBy());
        // Back on the shelf, and shabbier if it came back that way.
        job.getAsset().setStatus(Asset.Status.AVAILABLE);
        if (job.getAsset().getConditionStatus() == Asset.Condition.NEW) {
            job.getAsset().setConditionStatus(Asset.Condition.GOOD);
        }
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .module("ASSETS")
                .entityType("Asset")
                .entityId(job.getAsset().getId().toString())
                .summary("Servicing finished on " + job.getAsset().getAssetNumber())
                .build());
        return maintenanceRow(job);
    }

    public AssetDtos.MaintenanceRow cancelJob(UUID jobId) {
        guard("ASSET_MANAGE");
        AssetMaintenance job = job(jobId);
        if (job.getStatus() == AssetMaintenance.Status.COMPLETED) {
            throw AppException.rule("A finished job cannot be cancelled.");
        }
        job.setStatus(AssetMaintenance.Status.CANCELLED);
        if (job.getAsset().getStatus() == Asset.Status.IN_MAINTENANCE) {
            job.getAsset().setStatus(Asset.Status.AVAILABLE);
        }
        return maintenanceRow(job);
    }

    public PageResponse<AssetDtos.MaintenanceRow> listJobs(AssetMaintenance.Status status,
                                                            Pageable pageable) {
        guard("ASSET_READ");
        Page<AssetMaintenance> page = status == null
                ? maintenance.findAllByOrderByScheduledForDesc(pageable)
                : maintenance.findByStatusOrderByScheduledForDesc(status, pageable);
        return PageResponse.from(page, this::maintenanceRow);
    }

    // --------------------------------------------------------------- depreciation

    /**
     * What each asset is worth now, straight-line.
     *
     * <p>A deliberately plain calculation, not a ledger: the point is to spot the laptop that
     * should have been replaced three years ago, not to post an accounting entry.
     */
    public List<AssetDtos.DepreciationRow> depreciation() {
        guard("ASSET_READ");
        return assets.findAll().stream()
                .filter(asset -> asset.getPurchaseCost() != null
                        && asset.getPurchaseDate() != null)
                .map(this::depreciationRow)
                .toList();
    }

    private AssetDtos.DepreciationRow depreciationRow(Asset asset) {
        BigDecimal rate = asset.getCategory() != null ? asset.getCategory().getDepreciationRate() : null;
        Integer life = asset.getCategory() != null ? asset.getCategory().getUsefulLifeYears() : null;
        BigDecimal cost = asset.getPurchaseCost();
        BigDecimal current = cost;
        BigDecimal annual = null;
        // Age only counts when there is a starting date to count from. Both fields are
        // optional on the way in, so an asset recorded without them is still worth listing.
        LocalDate purchased = asset.getPurchaseDate();
        if (cost == null) {
            current = BigDecimal.ZERO;
        } else if (purchased == null) {
            if (rate != null && rate.signum() > 0) {
                annual = cost.multiply(rate);
            } else if (life != null && life > 0) {
                annual = cost.divide(BigDecimal.valueOf(life), 2, RoundingMode.HALF_UP);
            }
        } else if (rate != null && rate.signum() > 0) {
            annual = cost.multiply(rate);
            long age = ChronoUnit.YEARS.between(purchased, LocalDate.now());
            current = cost.subtract(annual.multiply(BigDecimal.valueOf(Math.max(age, 0))));
            current = current.max(BigDecimal.ZERO);
        } else if (life != null && life > 0) {
            annual = cost.divide(BigDecimal.valueOf(life), 2, RoundingMode.HALF_UP);
            long age = ChronoUnit.YEARS.between(purchased, LocalDate.now());
            current = cost.subtract(annual.multiply(BigDecimal.valueOf(Math.min(Math.max(age, 0), life))));
            current = current.max(BigDecimal.ZERO);
        }
        return new AssetDtos.DepreciationRow(asset.getId(), asset.getAssetNumber(),
                asset.getName(), cost, rate, life, annual, current, asset.getWarrantyExpiry(),
                asset.getStatus());
    }

    public AssetDtos.AssetOverview overview() {
        guard("ASSET_READ");
        List<Asset> all = assets.findAll();
        BigDecimal cost = all.stream()
                .map(asset -> asset.getPurchaseCost() == null ? BigDecimal.ZERO : asset.getPurchaseCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal book = all.stream()
                .map(asset -> depreciationRow(asset).currentValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate soon = LocalDate.now().plusDays(90);
        long warranty = assets.findWithWarrantyExpiringBetween(LocalDate.now(), soon).size();
        return new AssetDtos.AssetOverview(all.size(),
                all.stream().filter(a -> a.getStatus() == Asset.Status.AVAILABLE).count(),
                all.stream().filter(a -> a.getStatus() == Asset.Status.ASSIGNED).count(),
                all.stream().filter(a -> a.getStatus() == Asset.Status.IN_MAINTENANCE).count(),
                all.stream().filter(a -> a.getStatus() == Asset.Status.LOST).count(),
                all.stream().filter(a -> a.getStatus() == Asset.Status.DISPOSED).count(),
                cost, book, warranty);
    }

    // ------------------------------------------------------------------ helpers

    private void guard(String permission) {
        institutions.requireModuleEnabled(ModuleKey.ASSETS);
        auth.requirePermission(permission);
    }

    /** The holder, resolved from exactly one of the three identifiers. */
    private record AssignmentHolder(User user, Employee employee, Student student, String name,
                                    Department department, UUID identifier) {
    }

    private AssignmentHolder resolveHolder(AssetDtos.AssignmentRequest request) {
        int given = 0;
        if (request.holderUserId() != null) {
            given++;
        }
        if (request.holderEmployeeId() != null) {
            given++;
        }
        if (request.holderStudentId() != null) {
            given++;
        }
        if (given > 1) {
            throw AppException.rule("An asset is held by one person: give only one identifier.");
        }
        if (request.holderUserId() != null) {
            User user = users.findById(request.holderUserId())
                    .orElseThrow(() -> AppException.notFound("User"));
            return new AssignmentHolder(user, null, null, user.getDisplayName(), null, user.getId());
        }
        if (request.holderEmployeeId() != null) {
            Employee employee = employees.findById(request.holderEmployeeId())
                    .orElseThrow(() -> AppException.notFound("Employee"));
            return new AssignmentHolder(null, employee, null, employee.fullName(),
                    employee.getDepartment(), employee.getId());
        }
        if (request.holderStudentId() != null) {
            Student student = students.findById(request.holderStudentId())
                    .orElseThrow(() -> AppException.notFound("Student"));
            return new AssignmentHolder(null, null, student,
                    student.getFirstName() + " " + student.getLastName(), null, student.getId());
        }
        // Nobody in the directory: still a legitimate holder, just one we cannot link to.
        return new AssignmentHolder(null, null, null, request.holderName().trim(), null, null);
    }

    private AssetDtos.CategoryRow categoryRow(AssetCategory category) {
        return new AssetDtos.CategoryRow(category.getId(), category.getCode(), category.getName(),
                category.getDepreciationRate(), category.getUsefulLifeYears());
    }

    private AssetDtos.AssetRow assetRow(Asset asset) {
        return assetRow(asset, null);
    }

    /**
     * Build the row, optionally naming the holder.
     *
     * <p>The asset table only has columns for people the institution has a record of, so an
     * external holder's name lives on the assignment. The custody list already has that row
     * in hand, so it hands the name over rather than showing a nameless loan.
     */
    private AssetDtos.AssetRow assetRow(Asset asset, String holderName) {
        BigDecimal cost = asset.getPurchaseCost();
        BigDecimal rate = asset.getCategory() == null ? null : asset.getCategory().getDepreciationRate();
        BigDecimal current = cost == null ? null
                : (rate != null && rate.signum() > 0 ? depreciationRow(asset).currentValue() : cost);
        return new AssetDtos.AssetRow(asset.getId(), asset.getAssetNumber(), asset.getName(),
                asset.getDescription(), asset.getCategory() == null ? null : asset.getCategory().getId(),
                asset.getCategory() == null ? null : asset.getCategory().getName(),
                asset.getSerialNumber(), asset.getBrand(), asset.getModel(), asset.getPurchaseDate(),
                cost, rate, current, asset.getWarrantyExpiry(), asset.getLocation(),
                asset.getDepartment() == null ? null : asset.getDepartment().getId(),
                asset.getDepartment() == null ? null : asset.getDepartment().getName(),
                asset.getStatus(), asset.getConditionStatus(),
                asset.getAssignedUser() == null ? null : asset.getAssignedUser().getId(),
                asset.getAssignedEmployee() == null ? null : asset.getAssignedEmployee().getId(),
                asset.getAssignedStudent() == null ? null : asset.getAssignedStudent().getId(),
                holderName != null ? holderName : asset.holderName(),
                asset.getAssignedAt(), asset.getLostAt(), asset.getLostReason(),
                asset.getDisposedAt(), asset.getDisposalMethod(), asset.getDisposalNotes(),
                asset.getDisposalValue(), asset.getNotes());
    }

    private AssetDtos.AssignmentRow assignmentRow(AssetAssignment assignment) {
        return new AssetDtos.AssignmentRow(assignment.getId(), assignment.getAsset().getId(),
                assignment.getAsset().getAssetNumber(), assignment.getAsset().getName(),
                assignment.getHolderType(),
                assignment.getHolderUser() == null ? null : assignment.getHolderUser().getId(),
                assignment.getHolderEmployee() == null ? null : assignment.getHolderEmployee().getId(),
                assignment.getHolderStudent() == null ? null : assignment.getHolderStudent().getId(),
                assignment.getHolderName(), assignment.getAssignedAt(), assignment.getAssignedBy(),
                assignment.getReturnedAt(), assignment.getConditionOut(),
                assignment.getConditionIn(), assignment.getNotes(), assignment.isOpen());
    }

    private AssetDtos.MaintenanceRow maintenanceRow(AssetMaintenance job) {
        return new AssetDtos.MaintenanceRow(job.getId(), job.getAsset().getId(),
                job.getAsset().getAssetNumber(), job.getAsset().getName(), job.getType(),
                job.getDescription(), job.getVendor(), job.getCost(), job.getPerformedBy(),
                job.getScheduledFor(), job.getStartedAt(), job.getCompletedAt(), job.getStatus());
    }

    private AssetCategory category(UUID id) {
        return categories.findById(id).orElseThrow(() -> AppException.notFound("Asset category"));
    }

    private Department department(UUID id) {
        return departments.findById(id).orElseThrow(() -> AppException.notFound("Department"));
    }

    private Asset asset(UUID id) {
        return assets.findById(id).orElseThrow(() -> AppException.notFound("Asset"));
    }

    private AssetMaintenance job(UUID id) {
        return maintenance.findById(id).orElseThrow(() -> AppException.notFound("Maintenance job"));
    }
}
