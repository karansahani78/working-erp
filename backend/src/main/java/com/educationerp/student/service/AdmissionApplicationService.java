package com.educationerp.student.service;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.communication.CommunicationEventCode;
import com.educationerp.communication.CommunicationService;
import com.educationerp.communication.MessageEvent;
import com.educationerp.common.api.PageResponse;
import com.educationerp.common.error.AppException;
import com.educationerp.finance.FeeStructure;
import com.educationerp.finance.FeeStructureRepository;
import com.educationerp.finance.service.FeeAssessmentService;
import com.educationerp.student.AdmissionApplication;
import com.educationerp.student.Enrollment;
import com.educationerp.student.EnrollmentRepository;
import com.educationerp.student.Student;
import com.educationerp.student.AdmissionApplicationRepository;
import com.educationerp.student.AdmissionCampaign;
import com.educationerp.student.AdmissionCampaignRepository;
import com.educationerp.student.AdmissionDecision;
import com.educationerp.student.AdmissionDecisionRepository;
import com.educationerp.student.ApplicationDocument;
import com.educationerp.student.ApplicationDocumentRepository;
import com.educationerp.student.dto.StudentDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admission applications and the review workflow.
 *
 * <pre>
 * DRAFT → SUBMITTED → UNDER_REVIEW → DOCUMENT_VERIFICATION → ELIGIBILITY
 *       → ENTRANCE → SELECTED | WAITLISTED | REJECTED → ADMITTED → ENROLLED
 * </pre>
 *
 * <p>Transitions are validated rather than assumed: moving an application backwards or
 * out of a terminal state is rejected. Document verification consults the campaign's
 * configurable requirements, and approval is idempotent so a retried request never
 * creates a second student.
 */
@Service
@RequiredArgsConstructor
public class AdmissionApplicationService {

    /** Allowed next states, keyed by current state. */
    private static final Map<AdmissionApplication.Status, Set<AdmissionApplication.Status>> TRANSITIONS = Map.ofEntries(
            Map.entry(AdmissionApplication.Status.DRAFT, Set.of(AdmissionApplication.Status.SUBMITTED)),
            Map.entry(AdmissionApplication.Status.SUBMITTED, Set.of(AdmissionApplication.Status.UNDER_REVIEW)),
            Map.entry(AdmissionApplication.Status.UNDER_REVIEW, Set.of(
                    AdmissionApplication.Status.DOCUMENT_VERIFICATION,
                    AdmissionApplication.Status.REJECTED)),
            Map.entry(AdmissionApplication.Status.DOCUMENT_VERIFICATION, Set.of(
                    AdmissionApplication.Status.ELIGIBILITY,
                    AdmissionApplication.Status.REJECTED)),
            Map.entry(AdmissionApplication.Status.ELIGIBILITY, Set.of(
                    AdmissionApplication.Status.ENTRANCE,
                    AdmissionApplication.Status.SELECTED,
                    AdmissionApplication.Status.WAITLISTED,
                    AdmissionApplication.Status.REJECTED)),
            Map.entry(AdmissionApplication.Status.ENTRANCE, Set.of(
                    AdmissionApplication.Status.SELECTED,
                    AdmissionApplication.Status.WAITLISTED,
                    AdmissionApplication.Status.REJECTED)),
            Map.entry(AdmissionApplication.Status.SELECTED, Set.of(
                    AdmissionApplication.Status.ADMITTED,
                    AdmissionApplication.Status.WAITLISTED,
                    AdmissionApplication.Status.REJECTED)),
            Map.entry(AdmissionApplication.Status.WAITLISTED, Set.of(
                    AdmissionApplication.Status.SELECTED,
                    AdmissionApplication.Status.REJECTED,
                    AdmissionApplication.Status.ADMITTED)),
            Map.entry(AdmissionApplication.Status.ADMITTED, Set.of(AdmissionApplication.Status.ENROLLED)),
            Map.entry(AdmissionApplication.Status.REJECTED, Set.of()),
            Map.entry(AdmissionApplication.Status.ENROLLED, Set.of()));

    private final AdmissionApplicationRepository repository;
    private final AdmissionCampaignRepository campaignRepository;
    private final AdmissionDecisionRepository decisionRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final StudentCreationService studentCreationService;
    private final EnrollmentService enrollmentService;
    private final EnrollmentRepository enrollments;
    private final com.educationerp.academic.SchoolClassRepository classRepository;
    private final FeeStructureRepository feeStructures;
    private final FeeAssessmentService feeAssessments;
    private final AuthorizationChecker auth;
    private final CommunicationService communication;
    private final ApplicationEventPublisher events;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<StudentDtos.ApplicationResponse> search(UUID campaignId, String status, String term, Pageable pageable) {
        auth.requirePermission("ADMISSION_READ");
        AdmissionApplication.Status parsed = parseStatus(status);
        String search = term == null || term.isBlank() ? null : "%" + term.toLowerCase(Locale.ROOT) + "%";
        return PageResponse.from(repository.search(campaignId, parsed, search, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public StudentDtos.ApplicationResponse get(UUID id) {
        auth.requirePermission("ADMISSION_READ");
        return toResponse(require(id));
    }

    @Transactional
    public StudentDtos.ApplicationResponse create(StudentDtos.ApplicationRequest request) {
        auth.requirePermission("ADMISSION_CREATE");
        AdmissionCampaign campaign = campaignRepository.findById(request.campaignId())
                .orElseThrow(() -> AppException.notFound("Admission campaign"));
        AdmissionApplication application = new AdmissionApplication();
        application.setCampaignId(campaign.getId());
        application.setReferenceCode(nextReferenceCode(campaign));
        apply(application, request);
        application.setStatus(AdmissionApplication.Status.DRAFT);
        return toResponse(repository.save(application));
    }

    /** Saves applicant-supplied edits; only meaningful while the application is a draft. */
    @Transactional
    public StudentDtos.ApplicationResponse updateDraft(UUID id, StudentDtos.ApplicationRequest request) {
        auth.requirePermission("ADMISSION_UPDATE");
        AdmissionApplication application = require(id);
        if (application.getStatus() != AdmissionApplication.Status.DRAFT) {
            throw AppException.rule("Only draft applications can be edited. Submit it first.");
        }
        apply(application, request);
        return toResponse(repository.save(application));
    }

    @Transactional
    public StudentDtos.ApplicationResponse update(UUID id, StudentDtos.ApplicationUpdate request) {
        auth.requirePermission("ADMISSION_UPDATE");
        AdmissionApplication application = require(id);
        if (request.phone() != null) {
            application.setPhone(request.phone());
        }
        if (request.email() != null) {
            application.setEmail(request.email());
        }
        if (request.address() != null) {
            application.setAddress(request.address());
        }
        if (request.entranceScore() != null) {
            application.setEntranceScore(request.entranceScore());
        }
        if (request.meritScore() != null) {
            application.setMeritScore(request.meritScore());
        }
        return toResponse(repository.save(application));
    }

    /** DRAFT → SUBMITTED. The campaign must still be open on the submission date. */
    @Transactional
    public StudentDtos.ApplicationResponse submit(UUID id) {
        auth.requirePermission("ADMISSION_CREATE");
        AdmissionApplication application = require(id);
        AdmissionCampaign campaign = requireCampaign(application.getCampaignId());
        LocalDate today = LocalDate.now();
        if (campaign.getStatus() != AdmissionCampaign.Status.OPEN) {
            throw AppException.rule("This admission campaign is not open for submissions.");
        }
        if (campaign.getOpenDate() != null && today.isBefore(campaign.getOpenDate())) {
            throw AppException.rule("This admission campaign has not opened yet.");
        }
        if (campaign.getCloseDate() != null && today.isAfter(campaign.getCloseDate())) {
            throw AppException.rule("This admission campaign has closed.");
        }
        if (application.getEmail() == null || application.getEmail().isBlank()) {
            throw AppException.rule("An email address is required before submitting.");
        }
        transition(application, AdmissionApplication.Status.SUBMITTED);
        application.setSubmittedAt(today);
        return toResponse(repository.save(application));
    }

    @Transactional
    public StudentDtos.ApplicationResponse startReview(UUID id) {
        auth.requirePermission("ADMISSION_REVIEW");
        AdmissionApplication application = require(id);
        transition(application, AdmissionApplication.Status.UNDER_REVIEW);
        return toResponse(repository.save(application));
    }

    /** Records one document and moves the application into document verification. */
    @Transactional
    public StudentDtos.DocumentResponse attachDocument(UUID applicationId, StudentDtos.DocumentRequest request) {
        auth.requirePermission("ADMISSION_UPDATE");
        AdmissionApplication application = require(applicationId);
        if (application.getStatus().isTerminal()) {
            throw AppException.rule("Documents cannot be changed on a closed application.");
        }
        if (documentRepository.existsByApplicationIdAndDocumentType(applicationId, request.documentType())) {
            throw AppException.duplicate("A document of type " + request.documentType() + " is already attached.");
        }
        ApplicationDocument document = new ApplicationDocument();
        document.setApplicationId(applicationId);
        document.setDocumentType(request.documentType());
        document.setFileName(request.fileName());
        document.setStorageKey(request.storageKey());
        document.setContentType(request.contentType());
        document.setSizeBytes(request.sizeBytes());
        documentRepository.save(document);

        transition(application, AdmissionApplication.Status.DOCUMENT_VERIFICATION);
        repository.save(application);
        return toDocumentResponse(document);
    }

    @Transactional(readOnly = true)
    public List<StudentDtos.DocumentResponse> documents(UUID applicationId) {
        auth.requirePermission("ADMISSION_READ");
        require(applicationId);
        return documentRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    /**
     * Verifies attached documents against the campaign's requirements. Any explicitly
     * rejected document moves the application to REJECTED; otherwise a missing mandatory
     * document is reported and the application advances to ELIGIBILITY only when nothing
     * is outstanding.
     */
    @Transactional
    public VerificationResult verifyDocuments(UUID id, List<StudentDtos.DocumentVerification> verifications) {
        auth.requirePermission("ADMISSION_REVIEW");
        AdmissionApplication application = require(id);
        Map<String, ApplicationDocument> byType = documentRepository
                .findByApplicationIdOrderByCreatedAtAsc(id).stream()
                .collect(Collectors.toMap(ApplicationDocument::getDocumentType, Function.identity(),
                        (first, second) -> first));

        for (StudentDtos.DocumentVerification verification : verifications) {
            ApplicationDocument document = byType.get(verification.documentType());
            if (document == null) {
                throw AppException.notFound("Document " + verification.documentType());
            }
            ApplicationDocument.Status status;
            try {
                status = ApplicationDocument.Status.valueOf(verification.status().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new AppException(com.educationerp.common.error.ErrorCode.VALIDATION_ERROR,
                        "Unknown document status: " + verification.status());
            }
            document.setStatus(status);
            document.setReviewNotes(verification.notes());
            document.setReviewedAt(java.time.Instant.now());
            documentRepository.save(document);
        }

        List<ApplicationDocument> current = documentRepository.findByApplicationIdOrderByCreatedAtAsc(id);
        List<String> missing = requiredDocumentTypes(application).stream()
                .filter(type -> current.stream().noneMatch(d -> d.getDocumentType().equals(type)))
                .toList();
        boolean rejected = current.stream().anyMatch(d -> d.getStatus() == ApplicationDocument.Status.REJECTED);

        if (rejected) {
            transition(application, AdmissionApplication.Status.REJECTED);
            application.setDecidedAt(LocalDate.now());
            repository.save(application);
            return new VerificationResult(toResponse(application), missing, true);
        }
        if (!missing.isEmpty()) {
            return new VerificationResult(toResponse(application), missing, false);
        }
        transition(application, AdmissionApplication.Status.ELIGIBILITY);
        return new VerificationResult(toResponse(repository.save(application)), List.of(), false);
    }

    /** Eligibility outcome: SELECTED, WAITLISTED or REJECTED. */
    @Transactional
    public StudentDtos.ApplicationResponse decideEligibility(UUID id, StudentDtos.DecisionRequest request) {
        auth.requirePermission("ADMISSION_REVIEW");
        AdmissionApplication application = require(id);
        AdmissionDecision.DecisionType decision = parseDecision(request.decision());
        AdmissionApplication.Status next = switch (decision) {
            case REJECTED -> AdmissionApplication.Status.REJECTED;
            case WAITLISTED -> AdmissionApplication.Status.WAITLISTED;
            default -> AdmissionApplication.Status.SELECTED;
        };
        transition(application, next);
        if (next == AdmissionApplication.Status.REJECTED) {
            application.setDecidedAt(LocalDate.now());
        }
        application.setDecisionNotes(request.notes());
        recordDecision(application, decision, request.notes());
        return toResponse(repository.save(application));
    }

    /** Entrance/merit scoring moves a selected or waitlisted application to SELECTED. */
    @Transactional
    public StudentDtos.ApplicationResponse recordScores(UUID id, BigDecimalScore scores) {
        auth.requirePermission("ADMISSION_REVIEW");
        AdmissionApplication application = require(id);
        if (scores.entrance() != null) {
            application.setEntranceScore(scores.entrance());
        }
        if (scores.merit() != null) {
            application.setMeritScore(scores.merit());
        }
        if (application.getStatus() == AdmissionApplication.Status.WAITLISTED
                || application.getStatus() == AdmissionApplication.Status.ENTRANCE) {
            transition(application, AdmissionApplication.Status.SELECTED);
        }
        return toResponse(repository.save(application));
    }

    /** Rejects an application at any reviewable stage. */
    @Transactional
    public StudentDtos.ApplicationResponse reject(UUID id, String notes) {
        auth.requirePermission("ADMISSION_REJECT");
        AdmissionApplication application = require(id);
        if (application.getStatus() == AdmissionApplication.Status.REJECTED) {
            return toResponse(application);
        }
        transition(application, AdmissionApplication.Status.REJECTED);
        application.setDecidedAt(LocalDate.now());
        application.setDecisionNotes(notes);
        recordDecision(application, AdmissionDecision.DecisionType.REJECTED, notes);
        return toResponse(repository.save(application));
    }

    /**
     * Approves a selected application, creates the student record and assesses the fees
     * the blueprint expects next in the chain. Idempotent: a retry returns the student
     * created by the first call instead of minting a second one, and the fee assessment
     * already on the student is left alone.
     */
    @Transactional
    public ApprovalResult approve(UUID id, String notes) {
        auth.requirePermission("ADMISSION_APPROVE");
        AdmissionApplication application = require(id);
        if (application.getStudentId() != null) {
            Student existing = studentCreationService.requireStudent(application.getStudentId());
            assessAdmissionFees(application, existing);
            notifyAdmitted(application, existing);
            return new ApprovalResult(toResponse(application), studentCreationService.toResponse(existing), false);
        }
        transition(application, AdmissionApplication.Status.ADMITTED);
        recordDecision(application, AdmissionDecision.DecisionType.APPROVED, notes);
        Student student = studentCreationService.createFromApplication(application);
        application.setStudentId(student.getId());
        if (application.getDecidedAt() == null) {
            application.setDecidedAt(LocalDate.now());
        }
        application.setDecisionNotes(notes);
        StudentDtos.ApplicationResponse response = toResponse(repository.save(application));
        assessAdmissionFees(application, student);
        notifyAdmitted(application, student);
        return new ApprovalResult(response, studentCreationService.toResponse(student), true);
    }


    /**
     * Tells the applicant and their guardians that the place is theirs.
     *
     * <p>Admission is the moment a family is most anxious about the outcome, so it is the one
     * approval in this flow that must reach them without anyone remembering to do it by hand.
     */
    private void notifyAdmitted(AdmissionApplication application, Student student) {
        List<UUID> recipients = communication.recipientsForStudent(student.getId(), null);
        if (recipients.isEmpty()) {
            return;
        }
        String className = application.getApplyingClassId() == null ? ""
                : classRepository.findById(application.getApplyingClassId())
                        .map(com.educationerp.academic.SchoolClass::getName)
                        .orElse("");
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("studentName", student.displayName());
        variables.put("studentNumber", student.getStudentNumber());
        variables.put("applicationNumber", application.getReferenceCode());
        variables.put("className", className);
        events.publishEvent(MessageEvent.of(CommunicationEventCode.ADMISSION_APPROVED,
                recipients, variables, "AdmissionApplication", application.getId()));
    }

    /**
     * The blueprint's fee step: an approved applicant is charged the published structure
     * for their year and class without anyone retyping it. A year with nothing published
     * yet simply leaves the student unassessed rather than failing the approval.
     */
    private void assessAdmissionFees(AdmissionApplication application, Student student) {
        if (application.getStudentId() == null) {
            return;
        }
        AdmissionCampaign campaign = requireCampaign(application.getCampaignId());
        UUID academicYearId = campaign.getAcademicYearId() != null
                ? campaign.getAcademicYearId()
                : application.getApplyingClassId() == null
                ? null
                : classRepository.findById(application.getApplyingClassId())
                        .map(klass -> klass.getAcademicYear().getId())
                        .orElse(null);
        if (academicYearId == null) {
            return;
        }
        List<FeeStructure> assessable = feeStructures.findAssessableFor(FeeStructure.Status.PUBLISHED,
                academicYearId, application.getApplyingClassId());
        if (assessable.isEmpty()) {
            return;
        }
        FeeStructure structure = assessable.get(0);
        // Admission fees fall due a month after the campaign closes; a campaign that is
        // still open is billed from today.
        LocalDate dueDate = campaign.getCloseDate() == null
                ? LocalDate.now().plusMonths(1)
                : campaign.getCloseDate().plusMonths(1);
        feeAssessments.assessAutomatically(student.getId(), structure.getId(), dueDate,
                "Admission fee assessed from " + structure.getName());
    }

    /**
     * The last step of the admission chain: the admitted student is enrolled in the class
     * they applied to and the application is closed as enrolled. Safe to call twice.
     */
    @Transactional
    public EnrolmentResult enrol(UUID id, UUID sectionId, String rollNumber) {
        auth.requirePermission("ENROLLMENT_CREATE");
        AdmissionApplication application = require(id);
        if (application.getStudentId() == null) {
            throw AppException.rule("Approve this application before enrolling the student.");
        }
        Student student = studentCreationService.requireStudent(application.getStudentId());
        UUID academicYearId = academicYearFor(application);
        if (application.getStatus() == AdmissionApplication.Status.ENROLLED
                && enrollments.existsByStudentIdAndAcademicYearIdAndSemesterIdAndSchoolClassId(
                student.getId(), academicYearId, null, application.getApplyingClassId())) {
            Enrollment existing = enrollments
                    .findFirstByStudentIdAndAcademicYearIdAndStatus(student.getId(), academicYearId,
                            Enrollment.Status.ACTIVE)
                    .orElseThrow(() -> AppException.notFound("Enrollment"));
            return new EnrolmentResult(toResponse(application), enrollmentService.toResponse(existing), false);
        }
        transition(application, AdmissionApplication.Status.ENROLLED);
        recordDecision(application, AdmissionDecision.DecisionType.ENROLLED, null);
        StudentDtos.EnrollmentResponse enrollment = enrollmentService.enroll(new StudentDtos.EnrollmentRequest(
                student.getId(), academicYearId, null, application.getApplyingClassId(),
                sectionId, rollNumber));
        return new EnrolmentResult(toResponse(repository.save(application)), enrollment, true);
    }

    private UUID academicYearFor(AdmissionApplication application) {
        AdmissionCampaign campaign = requireCampaign(application.getCampaignId());
        if (campaign.getAcademicYearId() != null) {
            return campaign.getAcademicYearId();
        }
        if (application.getApplyingClassId() == null) {
            throw AppException.rule("This campaign has no academic year, so the student cannot be enrolled yet.");
        }
        return classRepository.findById(application.getApplyingClassId())
                .orElseThrow(() -> AppException.notFound("Class"))
                .getAcademicYear().getId();
    }

    @Transactional(readOnly = true)
    public List<StudentDtos.DecisionResponse> decisions(UUID id) {
        auth.requirePermission("ADMISSION_READ");
        require(id);
        return decisionRepository.findByApplicationIdOrderByDecidedAtDesc(id).stream()
                .map(this::toDecisionResponse)
                .toList();
    }

    /** Admissions history for one student, newest first. */
    @Transactional(readOnly = true)
    public List<StudentDtos.ApplicationResponse> forStudent(UUID studentId) {
        auth.requirePermission("ADMISSION_READ");
        return repository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::toResponse)
                .toList();
    }

    AdmissionApplication require(UUID id) {
        return repository.findById(id).orElseThrow(() -> AppException.notFound("Application"));
    }

    private AdmissionCampaign requireCampaign(UUID id) {
        return campaignRepository.findById(id).orElseThrow(() -> AppException.notFound("Admission campaign"));
    }

    /** Validates a state change against the lifecycle map. */
    void transition(AdmissionApplication application, AdmissionApplication.Status target) {
        AdmissionApplication.Status current = application.getStatus();
        if (current == target) {
            return;
        }
        Set<AdmissionApplication.Status> allowed = TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw AppException.rule("An application cannot move from " + current + " to " + target + ".");
        }
        application.setStatus(target);
    }

    private void apply(AdmissionApplication application, StudentDtos.ApplicationRequest request) {
        application.setFirstName(request.firstName().trim());
        application.setMiddleName(trimToNull(request.middleName()));
        application.setLastName(trimToNull(request.lastName()));
        application.setDateOfBirth(request.dateOfBirth());
        application.setGender(request.gender());
        application.setNationality(request.nationality());
        application.setPhone(request.phone());
        application.setEmail(trimToNull(request.email()));
        application.setAddress(request.address());
        application.setPhotoUrl(request.photoUrl());
        application.setApplyingProgramId(request.applyingProgramId());
        application.setApplyingClassId(request.applyingClassId());
        application.setPreviousSchool(request.previousSchool());
        application.setPreviousQualification(request.previousQualification());
        application.setPreviousPercentage(request.previousPercentage());
        application.setEntranceScore(request.entranceScore());
        application.setMeritScore(request.meritScore());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Reference codes are short, human-quotable and unique per campaign. */
    private String nextReferenceCode(AdmissionCampaign campaign) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = campaign.getCode().substring(0, Math.min(4, campaign.getCode().length()))
                    + "-" + Long.toString(1000 + Math.abs(java.util.UUID.randomUUID().getMostSignificantBits() % 9000));
            if (!repository.existsByReferenceCode(candidate)) {
                return candidate;
            }
        }
        return "APP-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private List<String> requiredDocumentTypes(AdmissionApplication application) {
        AdmissionCampaign campaign = requireCampaign(application.getCampaignId());
        String json = campaign.getDocumentRequirements();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return List.of();
        }
    }

    private void recordDecision(AdmissionApplication application, AdmissionDecision.DecisionType decision, String notes) {
        AdmissionDecision record = new AdmissionDecision();
        record.setApplicationId(application.getId());
        record.setDecision(decision);
        record.setDecidedBy(auth.currentUserOrNull() == null ? null : auth.currentUserOrNull().userId());
        record.setNotes(notes);
        decisionRepository.save(record);
    }

    private AdmissionApplication.Status parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AdmissionApplication.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AppException(com.educationerp.common.error.ErrorCode.VALIDATION_ERROR,
                    "Unknown application status: " + status);
        }
    }

    private AdmissionDecision.DecisionType parseDecision(String decision) {
        try {
            return AdmissionDecision.DecisionType.valueOf(decision.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AppException(com.educationerp.common.error.ErrorCode.VALIDATION_ERROR,
                    "Unknown admission decision: " + decision);
        }
    }

    StudentDtos.ApplicationResponse toResponse(AdmissionApplication application) {
        return new StudentDtos.ApplicationResponse(
                application.getId(),
                application.getCampaignId(),
                application.getReferenceCode(),
                application.getFirstName(),
                application.getMiddleName(),
                application.getLastName(),
                application.getDateOfBirth(),
                application.getGender(),
                application.getNationality(),
                application.getPhone(),
                application.getEmail(),
                application.getAddress(),
                application.getPhotoUrl(),
                application.getApplyingProgramId(),
                application.getApplyingClassId(),
                application.getPreviousSchool(),
                application.getPreviousQualification(),
                application.getPreviousPercentage(),
                application.getEntranceScore(),
                application.getMeritScore(),
                application.getStatus().name(),
                application.getSubmittedAt(),
                application.getDecidedAt(),
                application.getDecisionNotes(),
                application.getStudentId(),
                application.getCreatedAt());
    }

    private StudentDtos.DocumentResponse toDocumentResponse(ApplicationDocument document) {
        return new StudentDtos.DocumentResponse(
                document.getId(),
                document.getApplicationId(),
                document.getDocumentType(),
                document.getFileName(),
                document.getStorageKey(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStatus().name(),
                document.getReviewNotes(),
                document.getReviewedAt());
    }

    private StudentDtos.DecisionResponse toDecisionResponse(AdmissionDecision decision) {
        return new StudentDtos.DecisionResponse(
                decision.getId(),
                decision.getApplicationId(),
                decision.getDecision().name(),
                decision.getDecidedBy(),
                decision.getDecidedAt(),
                decision.getNotes());
    }

    /** Entrance and merit scores accepted together. */
    public record BigDecimalScore(java.math.BigDecimal entrance, java.math.BigDecimal merit) {
    }

    /** Outcome of a verification pass: the application, outstanding documents, rejected. */
    public record VerificationResult(StudentDtos.ApplicationResponse application,
                                    List<String> missingDocuments,
                                    boolean rejected) {
    }

    /** Outcome of approval: the application, the student, and whether it was newly created. */
    public record ApprovalResult(StudentDtos.ApplicationResponse application,
                                 StudentDtos.StudentResponse student,
                                 boolean created) {
    }

    /** Outcome of the enrolment step: the application, the enrolment, and whether it is new. */
    public record EnrolmentResult(StudentDtos.ApplicationResponse application,
                                  StudentDtos.EnrollmentResponse enrollment,
                                  boolean created) {
    }
}