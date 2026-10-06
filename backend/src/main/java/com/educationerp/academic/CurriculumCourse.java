package com.educationerp.academic;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Placement of a course inside a curriculum slot: which semester, how many credits,
 * whether it is mandatory or an elective, and how it is assessed.
 */
@Entity
@Table(name = "curriculum_courses",
        uniqueConstraints = @UniqueConstraint(name = "uk_curriculum_courses_semester_code",
                columnNames = {"curriculum_id", "semester_id", "course_id"}))
public class CurriculumCourse extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id")
    private Semester semester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false, length = 20)
    private RequirementType requirementType = RequirementType.MANDATORY;

    @Column(name = "credit_hours")
    private Integer creditHours;

    @Column(name = "internal_marks")
    private Integer internalMarks;

    @Column(name = "external_marks")
    private Integer externalMarks;

    @Column(name = "elective_group", length = 60)
    private String electiveGroup;

    @Column(name = "ordinal")
    private Integer ordinal;

    public enum RequirementType {
        MANDATORY,
        ELECTIVE,
        OPTIONAL
    }

    public Curriculum getCurriculum() {
        return curriculum;
    }

    public void setCurriculum(Curriculum curriculum) {
        this.curriculum = curriculum;
    }

    public Semester getSemester() {
        return semester;
    }

    public void setSemester(Semester semester) {
        this.semester = semester;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public RequirementType getRequirementType() {
        return requirementType;
    }

    public void setRequirementType(RequirementType requirementType) {
        this.requirementType = requirementType;
    }

    public Integer getCreditHours() {
        return creditHours;
    }

    public void setCreditHours(Integer creditHours) {
        this.creditHours = creditHours;
    }

    public Integer getInternalMarks() {
        return internalMarks;
    }

    public void setInternalMarks(Integer internalMarks) {
        this.internalMarks = internalMarks;
    }

    public Integer getExternalMarks() {
        return externalMarks;
    }

    public void setExternalMarks(Integer externalMarks) {
        this.externalMarks = externalMarks;
    }

    public String getElectiveGroup() {
        return electiveGroup;
    }

    public void setElectiveGroup(String electiveGroup) {
        this.electiveGroup = electiveGroup;
    }

    public Integer getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(Integer ordinal) {
        this.ordinal = ordinal;
    }
}
