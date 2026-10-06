package com.educationerp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndLeaveYear(UUID employeeId, UUID leaveTypeId,
                                                                    int leaveYear);

    List<LeaveBalance> findByEmployeeIdAndLeaveYearOrderByLeaveYearDesc(UUID employeeId, int leaveYear);
}