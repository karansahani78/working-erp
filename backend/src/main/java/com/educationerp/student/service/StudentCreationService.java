package com.educationerp.student.service;

import com.educationerp.common.error.AppException;
import com.educationerp.student.AdmissionApplication;
import com.educationerp.student.Student;
import com.educationerp.student.StudentNumberGenerator;
import com.educationerp.student.StudentRepository;
import com.educationerp.student.dto.StudentDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Creates student records. Every number is minted server-side, and an applicant who
 * already has a student record is never duplicated.
 */
@Service
@RequiredArgsConstructor
public class StudentCreationService {

    private final StudentRepository repository;
    private final StudentNumberGenerator numberGenerator;

    /**
     * Promotes an approved application to a student. Returns the existing student when
     * the application was already converted, which is what makes approval retries safe.
     */
    @Transactional
    public Student createFromApplication(AdmissionApplication application) {
        if (application.getStudentId() != null) {
            return requireStudent(application.getStudentId());
        }
        Student student = new Student();
        student.setStudentNumber(numberGenerator.next());
        student.setAdmissionId(application.getId());
        student.setFirstName(application.getFirstName());
        student.setMiddleName(application.getMiddleName());
        student.setLastName(application.getLastName());
        student.setDateOfBirth(application.getDateOfBirth());
        student.setGender(application.getGender());
        student.setNationality(application.getNationality());
        student.setPhone(application.getPhone());
        student.setEmail(application.getEmail());
        student.setAddress(application.getAddress());
        student.setPhotoUrl(application.getPhotoUrl());
        student.setStatus(Student.Status.ACTIVE);
        student.setEnrollmentDate(LocalDate.now());
        return repository.save(student);
    }

    /** Creates a student entered directly by staff (walk-in admissions). */
    @Transactional
    public Student create(StudentDtos.StudentRequest request) {
        Student student = new Student();
        student.setStudentNumber(numberGenerator.next());
        apply(student, request);
        if (request.status() != null && !request.status().isBlank()) {
            student.setStatus(parseStatus(request.status()));
        }
        return repository.save(student);
    }

    Student requireStudent(UUID id) {
        return repository.findById(id).orElseThrow(() -> AppException.notFound("Student"));
    }

    void apply(Student student, StudentDtos.StudentRequest request) {
        student.setFirstName(request.firstName().trim());
        student.setMiddleName(request.middleName());
        student.setLastName(request.lastName());
        student.setDateOfBirth(request.dateOfBirth());
        student.setGender(request.gender());
        student.setNationality(request.nationality());
        student.setPhone(request.phone());
        student.setEmail(request.email());
        student.setAddress(request.address());
        student.setPhotoUrl(request.photoUrl());
    }

    Student.Status parseStatus(String status) {
        try {
            return Student.Status.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AppException(com.educationerp.common.error.ErrorCode.VALIDATION_ERROR,
                    "Unknown student status: " + status);
        }
    }

    StudentDtos.StudentResponse toResponse(Student student) {
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
}