package com.educationerp.hr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    List<LeaveRequest> findByEmployeeIdOrderByStartDateDesc(UUID employeeId);

    List<LeaveRequest> findByStatusOrderByStartDateAsc(LeaveRequest.Status status);

    /**
     * Live requests touching a date range, used to stop two approved leaves for the same
     * employee overlapping each other.
     */
    @Query("""
            select r from LeaveRequest r
            where r.employee.id = :employeeId
              and r.status in (com.educationerp.hr.LeaveRequest$Status.PENDING,
                               com.educationerp.hr.LeaveRequest$Status.APPROVED)
              and r.startDate <= :to
              and r.endDate >= :from
              and (:excludeId is null or r.id <> :excludeId)
            """)
    List<LeaveRequest> findOverlapping(UUID employeeId, LocalDate from, LocalDate to, UUID excludeId);
}