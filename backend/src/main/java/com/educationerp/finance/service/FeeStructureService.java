package com.educationerp.finance.service;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.finance.Discount;
import com.educationerp.finance.DiscountRepository;
import com.educationerp.finance.FeeComponent;
import com.educationerp.finance.FeeComponentRepository;
import com.educationerp.finance.FeeStructure;
import com.educationerp.finance.FeeStructureRepository;
import com.educationerp.finance.Scholarship;
import com.educationerp.finance.ScholarshipRepository;
import com.educationerp.finance.StudentConcession;
import com.educationerp.finance.StudentConcessionRepository;
import com.educationerp.finance.StudentFeeAssessment;
import com.educationerp.finance.StudentFeeAssessmentRepository;
import com.educationerp.finance.dto.FinanceDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Fee structures, discounts, scholarships and the concessions granted from them.
 *
 * <p>Two rules the blueprint insists on are enforced here rather than trusted to callers:
 * a fee structure is immutable once published, and a concession is only granted when a
 * matching fee assessment exists. The second matters because a percentage discount
 * resolves against a specific gross amount; without an assessment there is nothing to
 * resolve it against, and silently deferring would hide a mistake.
 */
@Service
@RequiredArgsConstructor
public class FeeStructureService {

    private final FeeStructureRepository structures;
    private final FeeComponentRepository components;
    private final DiscountRepository discounts;
    private final ScholarshipRepository scholarships;
    private final StudentConcessionRepository concessions;
    private final StudentFeeAssessmentRepository assessments;
    private final AuthorizationChecker auth;

    @Transactional(readOnly = true)
    public List<FinanceDtos.FeeStructureResponse> list() {
        auth.requirePermission("FEE_READ");
        return structures.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<FinanceDtos.FeeStructureResponse> listByStatus(FeeStructure.Status status) {
        auth.requirePermission("FEE_READ");
        return structures.findByStatusOrderByNameAsc(status).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FinanceDtos.FeeStructureResponse get(UUID id) {
        auth.requirePermission("FEE_READ");
        return toResponse(requireStructure(id));
    }

    @Transactional
    public FinanceDtos.FeeStructureResponse create(FinanceDtos.CreateFeeStructure request) {
        auth.requirePermission("FEE_CREATE");
        String code = request.code().trim();
        if (structures.existsByCodeIgnoreCase(code)) {
            throw AppException.duplicate("A fee structure with this code already exists.");
        }
        FeeStructure structure = new FeeStructure();
        structure.setName(request.name().trim());
        structure.setCode(code);
        structure.setProgramId(request.programId());
        structure.setSchoolClassId(request.schoolClassId());
        structure.setAcademicYearId(request.academicYearId());
        structure.setTotalAmount(money(request.totalAmount()));
        structure.setCurrency(currency(request.currency()));
        structure.setDescription(request.description());
        structure.setStatus(FeeStructure.Status.DRAFT);
        return toResponse(structures.save(structure));
    }

    @Transactional
    public FinanceDtos.FeeStructureResponse update(UUID id, FinanceDtos.UpdateFeeStructure request) {
        auth.requirePermission("FEE_CREATE");
        FeeStructure structure = requireEditable(id);
        structure.setName(request.name().trim());
        structure.setTotalAmount(money(request.totalAmount()));
        structure.setCurrency(currency(request.currency()));
        structure.setDescription(request.description());
        return toResponse(structures.save(structure));
    }

    @Transactional
    public FinanceDtos.FeeStructureResponse publish(UUID id) {
        auth.requirePermission("FEE_APPROVE");
        FeeStructure structure = requireStructure(id);
        if (structure.getStatus() == FeeStructure.Status.PUBLISHED) {
            return toResponse(structure);
        }
        if (structure.getStatus() == FeeStructure.Status.ARCHIVED) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "An archived fee structure cannot be published.");
        }
        BigDecimal declared = structure.getTotalAmount();
        BigDecimal components = componentTotal(structure.getId());
        // A structure whose components disagree with the headline total would produce
        // two different answers to "what does this cost?", so it is not publishable.
        if (components.compareTo(BigDecimal.ZERO) > 0 && components.compareTo(declared) != 0) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "The components total " + components.toPlainString()
                            + " but the fee structure total is " + declared.toPlainString()
                            + ". Correct the components before publishing.");
        }
        structure.setStatus(FeeStructure.Status.PUBLISHED);
        return toResponse(structures.save(structure));
    }

    @Transactional
    public FinanceDtos.FeeStructureResponse archive(UUID id) {
        auth.requirePermission("FEE_APPROVE");
        FeeStructure structure = requireStructure(id);
        if (assessments.countByFeeStructureId(structure.getId()) > 0) {
            throw new AppException(ErrorCode.CONFLICTING_OPERATION,
                    "This fee structure has already been assessed to students and cannot be archived.");
        }
        structure.setStatus(FeeStructure.Status.ARCHIVED);
        return toResponse(structures.save(structure));
    }

    @Transactional
    public FinanceDtos.FeeStructureResponse setComponents(UUID id, FinanceDtos.ComponentList request) {
        auth.requirePermission("FEE_CREATE");
        FeeStructure structure = requireEditable(id);
        if (request.components() == null) {
            request = new FinanceDtos.ComponentList(List.of());
        }
        for (FeeComponent component : components.findByFeeStructureIdOrderByComponentTypeAsc(id)) {
            components.delete(component);
        }
        for (FinanceDtos.FeeComponentRequest componentRequest : request.components()) {
            FeeComponent component = new FeeComponent();
            component.setFeeStructureId(id);
            component.setComponentType(componentRequest.componentType());
            component.setName(componentRequest.name().trim());
            component.setAmount(money(componentRequest.amount()));
            component.setMandatory(componentRequest.mandatory() == null || componentRequest.mandatory());
            component.setDescription(componentRequest.description());
            components.save(component);
        }
        return toResponse(structures.findById(id).orElseThrow());
    }

    // ------------------------------------------------------------------ discounts

    @Transactional(readOnly = true)
    public List<FinanceDtos.AwardResponse> listDiscounts() {
        auth.requirePermission("FEE_READ");
        return discounts.findAll().stream().map(this::toAward).toList();
    }

    @Transactional
    public FinanceDtos.AwardResponse createDiscount(FinanceDtos.CreateDiscount request) {
        auth.requirePermission("FEE_CREATE");
        String code = request.code().trim();
        if (discounts.existsByCodeIgnoreCase(code)) {
            throw AppException.duplicate("A discount with this code already exists.");
        }
        Discount discount = new Discount();
        discount.setCode(code);
        discount.setName(request.name().trim());
        discount.setDiscountType(request.valueType());
        discount.setValue(money(request.value()));
        discount.setDescription(request.description());
        discount.setStatus(Discount.Status.ACTIVE);
        return toAward(discounts.save(discount));
    }

    // --------------------------------------------------------------- scholarships

    @Transactional(readOnly = true)
    public List<FinanceDtos.AwardResponse> listScholarships() {
        auth.requirePermission("FEE_READ");
        return scholarships.findAll().stream().map(a -> toAward(a)).toList();
    }

    @Transactional
    public FinanceDtos.AwardResponse createScholarship(FinanceDtos.CreateScholarship request) {
        auth.requirePermission("FEE_CREATE");
        String code = request.code().trim();
        if (scholarships.existsByCodeIgnoreCase(code)) {
            throw AppException.duplicate("A scholarship with this code already exists.");
        }
        Scholarship scholarship = new Scholarship();
        scholarship.setCode(code);
        scholarship.setName(request.name().trim());
        scholarship.setAwardType(request.valueType());
        scholarship.setValue(money(request.value()));
        scholarship.setDescription(request.description());
        scholarship.setStatus(Scholarship.Status.ACTIVE);
        return toAward(scholarships.save(scholarship));
    }

    // ---------------------------------------------------------------- concessions

    @Transactional(readOnly = true)
    public List<FinanceDtos.ConcessionResponse> listConcessions(UUID studentId, UUID academicYearId) {
        auth.requirePermission("FEE_READ");
        return concessions
                .findByStudentIdAndAcademicYearIdAndStatus(studentId, academicYearId,
                        StudentConcession.Status.ACTIVE)
                .stream().map(this::toConcession).toList();
    }

    /**
     * Grants a concession to a student and folds it into that student's outstanding
     * assessment, keeping the gross fee intact and the concession separately auditable.
     */
    @Transactional
    public FinanceDtos.ConcessionResponse grantConcession(UUID studentId, UUID academicYearId,
                                                          FinanceDtos.GrantConcession request) {
        auth.requirePermission("FEE_APPROVE");
        boolean hasDiscount = request.discountId() != null;
        boolean hasScholarship = request.scholarshipId() != null;
        if (hasDiscount == hasScholarship) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Grant exactly one of a discount or a scholarship.");
        }
        BigDecimal amount = money(request.amount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "A concession amount must be greater than zero.");
        }
        if (hasDiscount) {
            requireActive(discounts.findById(request.discountId())
                    .orElseThrow(() -> AppException.notFound("Discount")));
        } else {
            requireActive(scholarships.findById(request.scholarshipId())
                    .orElseThrow(() -> AppException.notFound("Scholarship")));
        }
        if (concessions.findByStudentIdAndAcademicYearIdAndStatus(studentId, academicYearId,
                StudentConcession.Status.ACTIVE).stream()
                .anyMatch(existing -> hasDiscount
                        ? request.discountId().equals(existing.getDiscountId())
                        : request.scholarshipId().equals(existing.getScholarshipId()))) {
            throw AppException.duplicate("This student already holds that award for the year.");
        }

        StudentFeeAssessment assessment = openAssessment(studentId, academicYearId);

        BigDecimal net = assessment.getNetAmount().subtract(amount);
        if (net.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "A concession of " + amount.toPlainString()
                            + " exceeds the remaining net amount of " + assessment.getNetAmount().toPlainString() + ".");
        }
        if (hasDiscount) {
            assessment.setDiscountAmount(assessment.getDiscountAmount().add(amount));
        } else {
            assessment.setScholarshipAmount(assessment.getScholarshipAmount().add(amount));
        }
        assessment.setNetAmount(net);
        if (net.compareTo(BigDecimal.ZERO) == 0) {
            assessment.setStatus(StudentFeeAssessment.Status.PAID);
        } else if (assessment.getStatus() == StudentFeeAssessment.Status.PAID) {
            assessment.setStatus(StudentFeeAssessment.Status.PARTIALLY_PAID);
        }
        assessments.save(assessment);

        StudentConcession concession = new StudentConcession();
        concession.setStudentId(studentId);
        concession.setDiscountId(request.discountId());
        concession.setScholarshipId(request.scholarshipId());
        concession.setAcademicYearId(academicYearId);
        concession.setAmount(amount);
        concession.setReason(request.reason());
        concession.setStatus(StudentConcession.Status.ACTIVE);
        return toConcession(concessions.save(concession));
    }

    @Transactional
    public FinanceDtos.ConcessionResponse revokeConcession(UUID id) {
        auth.requirePermission("FEE_APPROVE");
        StudentConcession concession = concessions.findById(id)
                .orElseThrow(() -> AppException.notFound("Concession"));
        if (concession.getStatus() != StudentConcession.Status.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only an active concession can be revoked.");
        }
        StudentFeeAssessment assessment = assessments
                .findByStudentIdAndAcademicYearIdOrderByCreatedAtDesc(
                        concession.getStudentId(), concession.getAcademicYearId()).stream()
                .findFirst()
                .orElseThrow(() -> AppException.notFound("Fee assessment"));
        BigDecimal restored = assessment.getNetAmount().add(concession.getAmount());
        if (concession.getDiscountId() != null) {
            assessment.setDiscountAmount(assessment.getDiscountAmount().subtract(concession.getAmount()));
        } else {
            assessment.setScholarshipAmount(assessment.getScholarshipAmount().subtract(concession.getAmount()));
        }
        assessment.setNetAmount(restored);
        if (assessment.getStatus() == StudentFeeAssessment.Status.PAID) {
            assessment.setStatus(StudentFeeAssessment.Status.PARTIALLY_PAID);
        }
        assessments.save(assessment);
        concession.setStatus(StudentConcession.Status.REVOKED);
        return toConcession(concessions.save(concession));
    }

    // -------------------------------------------------------------------- helpers

    /**
     * The most recent assessment for a student in a year that has not been cancelled. A
     * concession is meaningless without a fee to reduce, so the absence of one is an error
     * rather than something to queue up silently.
     */
    private StudentFeeAssessment openAssessment(UUID studentId, UUID academicYearId) {
        return assessments.findByStudentIdAndAcademicYearIdOrderByCreatedAtDesc(studentId, academicYearId)
                .stream()
                .filter(candidate -> candidate.getStatus() != StudentFeeAssessment.Status.CANCELLED)
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Assess this student's fees before granting a concession."));
    }

    private FeeStructure requireStructure(UUID id) {
        return structures.findById(id).orElseThrow(() -> AppException.notFound("Fee structure"));
    }

    /** A published or archived structure is frozen: prices students have been billed cannot move. */
    private FeeStructure requireEditable(UUID id) {
        FeeStructure structure = requireStructure(id);
        if (structure.getStatus() != FeeStructure.Status.DRAFT) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a draft fee structure can be edited. Published prices are frozen.");
        }
        return structure;
    }

    private void requireActive(Discount discount) {
        if (discount.getStatus() != Discount.Status.ACTIVE) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This discount is not active and cannot be granted.");
        }
    }

    private void requireActive(Scholarship scholarship) {
        if (scholarship.getStatus() != Scholarship.Status.ACTIVE) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This scholarship is not active and cannot be granted.");
        }
    }

    private BigDecimal componentTotal(UUID feeStructureId) {
        return components.findByFeeStructureIdOrderByComponentTypeAsc(feeStructureId).stream()
                .map(FeeComponent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String currency(String value) {
        return value == null || value.isBlank() ? "NPR" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    FinanceDtos.FeeStructureResponse toResponse(FeeStructure structure) {
        List<FeeComponent> parts = components.findByFeeStructureIdOrderByComponentTypeAsc(structure.getId());
        BigDecimal total = parts.stream().map(FeeComponent::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinanceDtos.FeeStructureResponse(
                structure.getId(),
                structure.getName(),
                structure.getCode(),
                structure.getProgramId(),
                structure.getSchoolClassId(),
                structure.getAcademicYearId(),
                structure.getTotalAmount(),
                total,
                total.compareTo(structure.getTotalAmount()) == 0,
                structure.getCurrency(),
                structure.getStatus(),
                structure.getDescription(),
                parts.stream().map(part -> new FinanceDtos.FeeComponentResponse(
                        part.getId(), part.getComponentType(), part.getName(), part.getAmount(),
                        part.isMandatory(), part.getDescription())).toList(),
                structure.getCreatedAt());
    }

    private FinanceDtos.AwardResponse toAward(Discount discount) {
        return new FinanceDtos.AwardResponse(discount.getId(), discount.getCode(), discount.getName(),
                discount.getDiscountType(), discount.getValue(), discount.getDescription(), discount.getStatus());
    }

    private FinanceDtos.AwardResponse toAward(Scholarship scholarship) {
        return new FinanceDtos.AwardResponse(scholarship.getId(), scholarship.getCode(), scholarship.getName(),
                scholarship.getAwardType(), scholarship.getValue(), scholarship.getDescription(),
                scholarship.getStatus());
    }

    private FinanceDtos.ConcessionResponse toConcession(StudentConcession concession) {
        return new FinanceDtos.ConcessionResponse(
                concession.getId(), concession.getStudentId(), concession.getDiscountId(),
                concession.getScholarshipId(), concession.getAcademicYearId(), concession.getAmount(),
                concession.getReason(), concession.getStatus(), concession.getCreatedAt());
    }
}
