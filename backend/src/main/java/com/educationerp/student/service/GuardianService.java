package com.educationerp.student.service;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.auth.user.UserRepository;
import com.educationerp.common.api.PageResponse;
import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.common.error.AppException;
import com.educationerp.student.Enrollment;
import com.educationerp.student.EnrollmentRepository;
import com.educationerp.student.Guardian;
import com.educationerp.student.GuardianRepository;
import com.educationerp.student.Student;
import com.educationerp.student.StudentGuardian;
import com.educationerp.student.StudentGuardianRepository;
import com.educationerp.student.StudentRepository;
import com.educationerp.student.dto.StudentDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Guardians and student-guardian relationships.
 *
 * <p>A guardian is a standalone person record and the relationship to a student is an
 * explicit link row, so one guardian may serve several children and one student may have
 * several guardians.
 */
@Service
@RequiredArgsConstructor
public class GuardianService {

    private final GuardianRepository repository;
    private final StudentGuardianRepository linkRepository;
    private final StudentRepository studentRepository;
    private final UserRepository users;
    private final AuthorizationChecker auth;
    private final AuditService audit;


    /**
     * Attaches a guardian to the sign-in account they will use for the parent portal.
     *
     * <p>The account is found by email rather than taken on trust as a uuid, so a registrar
     * cannot attach the wrong account by mistyping an id, and one account cannot end up
     * belonging to two guardians -- which would quietly merge two families' children under a
     * single login.
     *
     * <p>Unlinking is supported through {@link #unlinkAccount} because a guardian record is
     * often created long before the person is given a login.
     */
    @Transactional
    public StudentDtos.GuardianResponse linkAccount(UUID guardianId,
                                                   StudentDtos.GuardianAccountLinkRequest request) {
        auth.requirePermission("GUARDIAN_MANAGE");
        Guardian guardian = require(guardianId);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (guardian.getEmail() != null
                && !guardian.getEmail().trim().toLowerCase(Locale.ROOT).equals(email)) {
            throw AppException.rule("The sign-in email must match the guardian's email on record ("
                    + guardian.getEmail() + "). Change the guardian's email first if it is wrong.");
        }
        var user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> AppException.notFound("User account for " + email));
        repository.findByUserId(user.getId())
                .filter(other -> !other.getId().equals(guardianId))
                .ifPresent(other -> {
                    throw AppException.duplicate("That account is already linked to guardian "
                            + other.displayName() + ".");
                });
        guardian.setUserId(user.getId());
        Guardian saved = repository.save(guardian);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("Guardian")
                .entityId(saved.getId().toString())
                .entityLabel(saved.displayName())
                .summary("Linked the guardian to the sign-in account " + email)
                .module("STUDENT")
                .succeeded(true)
                .build());
        return toResponse(saved);
    }

    /**
     * Detaches a guardian from their sign-in account. Their children stay linked to them and
     * the account simply stops seeing a parent portal until it is linked again.
     */
    @Transactional
    public StudentDtos.GuardianResponse unlinkAccount(UUID guardianId) {
        auth.requirePermission("GUARDIAN_MANAGE");
        Guardian guardian = require(guardianId);
        if (guardian.getUserId() == null) {
            return toResponse(guardian);
        }
        guardian.setUserId(null);
        Guardian saved = repository.save(guardian);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("Guardian")
                .entityId(saved.getId().toString())
                .entityLabel(saved.displayName())
                .summary("Unlinked the guardian from their sign-in account")
                .module("STUDENT")
                .succeeded(true)
                .build());
        return toResponse(saved);
    }

    // ---------- Guardian records ----------

    @Transactional
    public StudentDtos.GuardianResponse create(StudentDtos.GuardianRequest request) {
        auth.requirePermission("GUARDIAN_MANAGE");
        Guardian guardian = new Guardian();
        apply(guardian, request);
        return toResponse(repository.save(guardian));
    }

    @Transactional
    public StudentDtos.GuardianResponse update(UUID id, StudentDtos.GuardianRequest request) {
        auth.requirePermission("GUARDIAN_MANAGE");
        Guardian guardian = require(id);
        apply(guardian, request);
        return toResponse(repository.save(guardian));
    }

    @Transactional(readOnly = true)
    public StudentDtos.GuardianResponse get(UUID id) {
        auth.requirePermission("GUARDIAN_READ");
        return toResponse(require(id));
    }

    /** Searches guardians; phone and email are the usual identifiers at reception. */
    @Transactional(readOnly = true)
    public PageResponse<StudentDtos.GuardianResponse> search(String term, Pageable pageable) {
        auth.requirePermission("GUARDIAN_READ");
        String search = (term == null || term.isBlank()) ? null : "%" + term.toLowerCase(Locale.ROOT) + "%";
        var page = repository.findAll(pageable);
        if (search == null) {
            return PageResponse.from(page, this::toResponse);
        }
        var filtered = page.getContent().stream()
                .filter(g -> matches(g, search))
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(filtered, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast(), filtered.isEmpty());
    }

    private boolean matches(Guardian guardian, String search) {
        return guardian.displayName().toLowerCase(Locale.ROOT).contains(search)
                || (guardian.getEmail() != null && guardian.getEmail().toLowerCase(Locale.ROOT).contains(search))
                || (guardian.getPhone() != null && guardian.getPhone().contains(search));
    }

    @Transactional
    public void delete(UUID id) {
        auth.requirePermission("GUARDIAN_MANAGE");
        repository.delete(require(id));
    }

    // ---------- Relationships ----------

    /**
     * Links a guardian to a student. Exactly one primary contact is kept per student, so
     * marking a new one primary demotes the previous.
     */
    @Transactional
    public StudentDtos.GuardianLinkResponse link(UUID studentId, StudentDtos.GuardianLinkRequest request) {
        auth.requirePermission("GUARDIAN_MANAGE");
        studentRepository.findById(studentId).orElseThrow(() -> AppException.notFound("Student"));
        Guardian guardian = require(request.guardianId());
        StudentGuardian.Relationship relationship = parseRelationship(request.relationship());

        StudentGuardian link = linkRepository
                .findByStudentIdAndGuardianIdAndRelationship(studentId, guardian.getId(), relationship)
                .orElseGet(StudentGuardian::new);
        if (link.getId() == null) {
            link.setStudentId(studentId);
            link.setGuardianId(guardian.getId());
            link.setRelationship(relationship);
        }
        link.setCanPickup(request.canPickup());
        if (request.primaryContact()) {
            linkRepository.findFirstByStudentIdAndPrimaryContactTrue(studentId).ifPresent(existing -> {
                if (!existing.getId().equals(link.getId())) {
                    existing.setPrimaryContact(false);
                    linkRepository.save(existing);
                }
            });
            link.setPrimaryContact(true);
        }
        return toLinkResponse(linkRepository.save(link), guardian);
    }

    @Transactional(readOnly = true)
    public List<StudentDtos.GuardianLinkResponse> guardiansOf(UUID studentId) {
        auth.requireStudentAccess(studentId);
        return linkRepository.findByStudentIdOrderByCreatedAtAsc(studentId).stream()
                .map(link -> toLinkResponse(link, require(link.getGuardianId())))
                .toList();
    }

    /** Every student this guardian is responsible for. */
    @Transactional(readOnly = true)
    public PageResponse<StudentDtos.StudentResponse> childrenOf(UUID guardianId, Pageable pageable) {
        require(guardianId);
        auth.requirePermission("GUARDIAN_READ");
        List<UUID> studentIds = linkRepository.findByGuardianId(guardianId).stream()
                .map(StudentGuardian::getStudentId)
                .toList();
        if (studentIds.isEmpty()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(),
                    0, 0, true, true, true);
        }
        var page = studentRepository.findAllById(studentIds);
        return new PageResponse<>(
                page.stream().map(this::toStudentResponse).toList(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                page.size(),
                (int) Math.ceil(page.size() / (double) Math.max(1, pageable.getPageSize())),
                pageable.getPageNumber() == 0,
                true,
                page.isEmpty());
    }

    @Transactional
    public void unlink(UUID studentId, UUID linkId) {
        auth.requirePermission("GUARDIAN_MANAGE");
        StudentGuardian link = linkRepository.findById(linkId)
                .orElseThrow(() -> AppException.notFound("Guardian link"));
        if (!link.getStudentId().equals(studentId)) {
            throw AppException.notFound("Guardian link");
        }
        linkRepository.delete(link);
    }

    Guardian require(UUID id) {
        return repository.findById(id).orElseThrow(() -> AppException.notFound("Guardian"));
    }

    private void apply(Guardian guardian, StudentDtos.GuardianRequest request) {
        guardian.setFirstName(request.firstName().trim());
        guardian.setMiddleName(request.middleName());
        guardian.setLastName(request.lastName());
        guardian.setPhone(request.phone());
        guardian.setEmail(request.email());
        guardian.setOccupation(request.occupation());
        guardian.setAddress(request.address());
    }

    private StudentGuardian.Relationship parseRelationship(String value) {
        try {
            return StudentGuardian.Relationship.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AppException(com.educationerp.common.error.ErrorCode.VALIDATION_ERROR,
                    "Unknown guardian relationship: " + value);
        }
    }

    private StudentDtos.StudentResponse toStudentResponse(Student student) {
        return new StudentDtos.StudentResponse(
                student.getId(),
                student.getStudentNumber(),
                student.getUserId(),
                student.getAdmissionId(),
                student.getFirstName(),
                student.getMiddleName(),
                student.getLastName(),
                student.displayName(),
                student.getDateOfBirth(),
                student.getGender(),
                student.getNationality(),
                student.getPhone(),
                student.getEmail(),
                student.getAddress(),
                student.getPhotoUrl(),
                student.getEnrollmentDate(),
                student.getStatus().name(),
                student.getCreatedAt());
    }

    StudentDtos.GuardianResponse toResponse(Guardian guardian) {
        return new StudentDtos.GuardianResponse(
                guardian.getId(),
                guardian.getFirstName(),
                guardian.getMiddleName(),
                guardian.getLastName(),
                guardian.displayName(),
                guardian.getPhone(),
                guardian.getEmail(),
                guardian.getOccupation(),
                guardian.getAddress(),
                guardian.getUserId(),
                guardian.getUserId() != null);
    }

    StudentDtos.GuardianLinkResponse toLinkResponse(StudentGuardian link, Guardian guardian) {
        return new StudentDtos.GuardianLinkResponse(
                link.getId(),
                link.getGuardianId(),
                guardian.displayName(),
                guardian.getPhone(),
                guardian.getEmail(),
                link.getRelationship().name(),
                link.isPrimaryContact(),
                link.isCanPickup());
    }
}