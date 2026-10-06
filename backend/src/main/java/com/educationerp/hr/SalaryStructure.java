package com.educationerp.hr;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The configurable price of a job: a basic figure plus components that are either a fixed
 * amount or a percentage of the basic. Nothing about a payslip is hardcoded, so raising a
 * salary means publishing a new structure rather than editing code.
 */
@Entity
@Table(name = "salary_structures",
        uniqueConstraints = @UniqueConstraint(name = "uk_salary_structures_code", columnNames = "code"))
@Getter
@Setter
public class SalaryStructure extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "basic_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "NPR";

    /** Paid per overtime hour, so attendance can be turned into money without retyping. */
    @Column(name = "overtime_rate", nullable = false, precision = 14, scale = 2)
    private BigDecimal overtimeRate = BigDecimal.ZERO;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @OneToMany(mappedBy = "salaryStructure", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SalaryComponent> components = new ArrayList<>();

    public enum Status {
        DRAFT, PUBLISHED, ARCHIVED
    }

    public boolean isPublished() {
        return status == Status.PUBLISHED;
    }
}