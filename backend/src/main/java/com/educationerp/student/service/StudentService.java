package com.educationerp.student.service;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.api.PageResponse;
import com.educationerp.common.error.AppException;
import com.educationerp.student.Student;
import com.educationerp.student.StudentNumberGenerator;
import com.educationerp.student.StudentNumberSetting;
import com.educationerp.student.StudentNumberSettingRepository;
import com.educationerp.student.StudentRepository;
import com.educationerp.student.dto.StudentDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Student CRUD and status changes. The student number is server-generated and
 * immutable, so no update path can change it.
 */
@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository repository;
    private final StudentCreationService creationService;
    private final StudentNumberSettingRepository settingRepository;
    private final AuthorizationChecker auth;

    @Transactional(readOnly = true)
    public PageResponse<StudentDtos.StudentResponse> search(String term, String status, Pageable pageable) {
        auth.requirePermission("STUDENT_READ");
        Student.Status parsed = (status == null || status.isBlank()) ? null : creationService.parseStatus(status);
        String search = (term == null || term.isBlank()) ? null : "%" + term.toLowerCase(Locale.ROOT) + "%";
        return PageResponse.from(repository.search(parsed, search, pageable), creationService::toResponse);
    }

    @Transactional(readOnly = true)
    public StudentDtos.StudentResponse get(UUID id) {
        auth.requireStudentAccess(id);
        return creationService.toResponse(creationService.requireStudent(id));
    }

    /** Looks a student up by their generated number. */
    @Transactional(readOnly = true)
    public StudentDtos.StudentResponse getByNumber(String studentNumber) {
        auth.requirePermission("STUDENT_READ");
        return repository.findByStudentNumber(studentNumber)
                .map(creationService::toResponse)
                .orElseThrow(() -> AppException.notFound("Student"));
    }

    @Transactional
    public StudentDtos.StudentResponse create(StudentDtos.StudentRequest request) {
        auth.requirePermission("STUDENT_CREATE");
        if (request.email() != null && !request.email().isBlank()
                && repository.findFirstByEmailIgnoreCase(request.email().trim()).isPresent()) {
            throw AppException.duplicate("A student with this email address already exists.");
        }
        return creationService.toResponse(creationService.create(request));
    }

    @Transactional
    public StudentDtos.StudentResponse update(UUID id, StudentDtos.StudentRequest request) {
        auth.requirePermission("STUDENT_UPDATE");
        Student student = creationService.requireStudent(id);
        creationService.apply(student, request);
        if (request.status() != null && !request.status().isBlank()) {
            student.setStatus(creationService.parseStatus(request.status()));
        }
        return creationService.toResponse(repository.save(student));
    }

    /** Dedicated status transition; distinct permission from profile edits. */
    @Transactional
    public StudentDtos.StudentResponse changeStatus(UUID id, String status) {
        auth.requirePermission("STUDENT_STATUS_CHANGE");
        Student student = creationService.requireStudent(id);
        student.setStatus(creationService.parseStatus(status));
        return creationService.toResponse(repository.save(student));
    }

    /**
     * Removes a student record. Students that already have enrolment history are
     * deactivated instead, because deleting them would orphan academic records.
     */
    @Transactional
    public void delete(UUID id) {
        auth.requirePermission("STUDENT_DELETE");
        Student student = creationService.requireStudent(id);
        if (student.getStatus() == Student.Status.ACTIVE) {
            student.setStatus(Student.Status.WITHDRAWN);
            repository.save(student);
            return;
        }
        repository.delete(student);
    }

    /** Resolves a student id from their generated number. */
    @Transactional(readOnly = true)
    public UUID requireIdByNumber(String studentNumber) {
        auth.requirePermission("STUDENT_READ");
        return repository.findByStudentNumber(studentNumber)
                .map(Student::getId)
                .orElseThrow(() -> AppException.notFound("Student"));
    }

    /** Reads the configured number format so operators can preview it. */
    @Transactional(readOnly = true)
    public StudentNumberFormat numberFormat() {
        StudentNumberSetting setting = settingRepository.findFirstByActiveTrue().orElse(null);
        return new StudentNumberFormat(setting == null ? "{PREFIX}-{YEAR}-{SEQ:5}" : setting.getFormat());
    }

    @Transactional
    public StudentNumberFormat updateNumberFormat(String format) {
        auth.requirePermission("STUDENT_UPDATE");
        if (format == null || format.isBlank()) {
            throw new AppException(com.educationerp.common.error.ErrorCode.VALIDATION_ERROR,
                    "The student number format cannot be empty.");
        }
        StudentNumberSetting setting = settingRepository.findFirstByActiveTrue()
                .orElseGet(() -> new StudentNumberSetting());
        setting.setFormat(format.trim());
        setting.setActive(true);
        settingRepository.save(setting);
        return new StudentNumberFormat(setting.getFormat());
    }

    /** Preview of the next number, useful for verifying a format change. */
    @Transactional(readOnly = true)
    public String previewNumber() {
        return numberFormat().format();
    }

    public record StudentNumberFormat(String format) {
    }
}