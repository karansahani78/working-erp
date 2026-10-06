package com.educationerp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeAttendanceRepository extends JpaRepository<EmployeeAttendance, UUID> {

    Optional<EmployeeAttendance> findByEmployeeIdAndAttendanceDate(UUID employeeId, LocalDate attendanceDate);

    List<EmployeeAttendance> findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            UUID employeeId, LocalDate from, LocalDate to);

    /** Overtime minutes actually recorded for one employee across a payroll period. */
    @org.springframework.data.jpa.repository.Query("""
            select coalesce(sum(a.overtimeMinutes), 0) from EmployeeAttendance a
            where a.employee.id = :employeeId
              and a.attendanceDate between :from and :to
            """)
    long sumOvertimeMinutes(UUID employeeId, LocalDate from, LocalDate to);
}