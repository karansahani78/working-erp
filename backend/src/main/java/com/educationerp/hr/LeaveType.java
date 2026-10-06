package com.educationerp.hr;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A category of leave and how much of it an employee gets each year. */
@Entity
@Table(name = "leave_types",
        uniqueConstraints = @UniqueConstraint(name = "uk_leave_types_code", columnNames = "code"))
@Getter
@Setter
public class LeaveType extends BaseEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "days_per_year", nullable = false, precision = 6, scale = 1)
    private BigDecimal daysPerYear = BigDecimal.ZERO;

    @Column(name = "paid", nullable = false)
    private boolean paid = true;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}