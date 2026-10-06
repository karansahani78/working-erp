package com.educationerp.hr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    Optional<Employee> findFirstByEmailIgnoreCase(String email);

    Optional<Employee> findByUserId(UUID userId);

    List<Employee> findByDepartmentIdAndStatusOrderByEmployeeCodeAsc(UUID departmentId, Employee.Status status);

    List<Employee> findByDepartmentIdOrderByEmployeeCodeAsc(UUID departmentId);

    List<Employee> findByStatusOrderByEmployeeCodeAsc(Employee.Status status);

    /**
     * Every member of staff a payroll run should pay: active or on leave, employed by the
     * period end, and holding a published salary structure. The join is explicit so an
     * employee without a structure is visible as a gap rather than silently skipped.
     */
    @Query("""
            select distinct e from Employee e
            left join fetch e.salaryStructure
            where e.status in (com.educationerp.hr.Employee$Status.ACTIVE,
                               com.educationerp.hr.Employee$Status.ON_LEAVE)
              and e.joinDate <= :on
              and (e.exitDate is null or e.exitDate >= :on)
            order by e.employeeCode
            """)
    List<Employee> findPayableBy(java.time.LocalDate on);

    @Query("""
            select e from Employee e
            where e.status in (com.educationerp.hr.Employee$Status.ACTIVE,
                               com.educationerp.hr.Employee$Status.ON_LEAVE)
            order by e.employeeCode
            """)
    List<Employee> findAllPayable();
}