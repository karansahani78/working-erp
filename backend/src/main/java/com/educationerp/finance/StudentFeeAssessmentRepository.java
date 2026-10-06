package com.educationerp.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentFeeAssessmentRepository extends JpaRepository<StudentFeeAssessment, UUID> {

    Optional<StudentFeeAssessment> findByStudentIdAndFeeStructureId(UUID studentId, UUID feeStructureId);

    List<StudentFeeAssessment> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<StudentFeeAssessment> findByStatusOrderByDueDateAsc(StudentFeeAssessment.Status status);

    List<StudentFeeAssessment> findByStudentIdAndAcademicYearIdOrderByCreatedAtDesc(
            UUID studentId, UUID academicYearId);

    long countByFeeStructureId(UUID feeStructureId);

    /**
     * Outstanding receivables: assessments whose net amount still exceeds what has been
     * paid and refunded. Computed in the query so the ageing report never loads every row.
     */
    @Query("""
            select a from StudentFeeAssessment a
            where a.status <> :excluded
              and (a.netAmount - a.paidAmount + a.refundedAmount) > 0
            order by a.dueDate asc
            """)
    List<StudentFeeAssessment> findOutstanding(@Param("excluded") StudentFeeAssessment.Status excluded);
}
