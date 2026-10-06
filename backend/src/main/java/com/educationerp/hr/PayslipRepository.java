package com.educationerp.hr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

    List<Payslip> findByPayrollRunIdOrderByEmployeeEmployeeCodeAsc(UUID payrollRunId);

    List<Payslip> findByEmployeeIdOrderByPayrollRunPeriodYearDescPayrollRunPeriodMonthDesc(UUID employeeId);

    /**
     * A staff member's own payslips, including their line items, so a payslip view needs
     * one query rather than one per slip.
     */
    @Query("""
            select distinct p from Payslip p
            left join fetch p.items
            where p.employee.id = :employeeId
            order by p.payrollRun.periodYear desc, p.payrollRun.periodMonth desc
            """)
    List<Payslip> findForEmployeeWithItems(UUID employeeId);
}