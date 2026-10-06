package com.educationerp.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StudentConcessionRepository extends JpaRepository<StudentConcession, UUID> {

    List<StudentConcession> findByStudentIdAndAcademicYearIdAndStatus(
            UUID studentId, UUID academicYearId, StudentConcession.Status status);
}
