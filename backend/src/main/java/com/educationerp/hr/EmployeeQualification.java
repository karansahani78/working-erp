package com.educationerp.hr;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A qualification held by a specific employee, with where and when it was earned. */
@Entity
@Table(name = "employee_qualifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_employee_qualifications",
                columnNames = {"employee_id", "qualification_id"}))
@Getter
@Setter
public class EmployeeQualification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "qualification_id", nullable = false)
    private Qualification qualification;

    @Column(name = "institution", length = 150)
    private String institution;

    @Column(name = "awarded_year")
    private Integer awardedYear;

    @Column(name = "grade", length = 20)
    private String grade;
}