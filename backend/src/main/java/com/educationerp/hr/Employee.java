package com.educationerp.hr;

import com.educationerp.academic.Department;
import com.educationerp.auth.user.User;
import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A member of staff. Kept separate from {@link User}: many staff have no login at all,
 * and a login can exist without an employee record behind it.
 */
@Entity
@Table(name = "employees",
        uniqueConstraints = @UniqueConstraint(name = "uk_employees_code", columnNames = "employee_code"))
@Getter
@Setter
public class Employee extends BaseEntity {

    @Column(name = "employee_code", nullable = false, length = 40)
    private String employeeCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designation_id")
    private Designation designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salary_structure_id")
    private SalaryStructure salaryStructure;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "nationality", length = 80)
    private String nationality;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "email", length = 180)
    private String email;

    @Column(name = "address", length = 400)
    private String address;

    @Column(name = "photo_url", length = 400)
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 30)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Column(name = "join_date", nullable = false)
    private LocalDate joinDate;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "bank_account", length = 60)
    private String bankAccount;

    @Column(name = "tax_number", length = 60)
    private String taxNumber;

    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY)
    private List<EmployeeQualification> qualifications = new ArrayList<>();

    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY)
    private List<EmployeeDocument> documents = new ArrayList<>();

    public enum EmploymentType {
        FULL_TIME, PART_TIME, CONTRACT, VISITING, INTERN
    }

    public enum Status {
        ACTIVE, ON_LEAVE, SUSPENDED, RESIGNED, TERMINATED
    }

    public String fullName() {
        String name = firstName;
        if (middleName != null && !middleName.isBlank()) {
            name = name + " " + middleName;
        }
        if (lastName != null && !lastName.isBlank()) {
            name = name + " " + lastName;
        }
        return name;
    }

    /** A terminated member of staff must not appear in the next payroll run. */
    public boolean isPayable() {
        return status == Status.ACTIVE || status == Status.ON_LEAVE;
    }
}