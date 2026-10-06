package com.educationerp.academic;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * School-mode class (for example Grade 10, +2 Science). Not used in college/university
 * mode — the UI hides these fields when the academic model says they do not apply.
 */
@Entity
@Table(name = "school_classes",
        uniqueConstraints = @UniqueConstraint(name = "uk_school_classes_year_code",
                columnNames = {"academic_year_id", "code"}))
public class SchoolClass extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "ordinal")
    private Integer ordinal;

    @Column(name = "grade_level")
    private Integer gradeLevel;

    @Column(name = "stream")
    private String stream;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public AcademicYear getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(AcademicYear academicYear) {
        this.academicYear = academicYear;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(Integer ordinal) {
        this.ordinal = ordinal;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(Integer gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public String getStream() {
        return stream;
    }

    public void setStream(String stream) {
        this.stream = stream;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
