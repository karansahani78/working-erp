package com.educationerp.hr;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One line of a salary structure: an allowance or a deduction, fixed or a share of basic. */
@Entity
@Table(name = "salary_components")
@Getter
@Setter
public class SalaryComponent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salary_structure_id", nullable = false)
    private SalaryStructure salaryStructure;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 20)
    private ComponentType componentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private ValueType valueType = ValueType.FIXED;

    /** An amount when FIXED, a percentage of the basic salary when PERCENTAGE. */
    @Column(name = "value", nullable = false, precision = 14, scale = 4)
    private BigDecimal value;

    @Column(name = "taxable", nullable = false)
    private boolean taxable = true;

    public enum ComponentType {
        ALLOWANCE, DEDUCTION
    }

    public enum ValueType {
        FIXED, PERCENTAGE
    }

    public BigDecimal amountOn(BigDecimal basicSalary) {
        if (valueType == ValueType.PERCENTAGE) {
            return basicSalary.multiply(value).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        }
        return value;
    }
}