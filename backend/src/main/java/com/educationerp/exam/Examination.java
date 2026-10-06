package com.educationerp.exam;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An examination sitting for an academic year/semester, graded with a configurable scale.
 */
@Entity
@Table(name = "examinations")
public class Examination extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "academic_year_id")
    private UUID academicYearId;

    @Column(name = "semester_id")
    private UUID semesterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 30)
    private ExamType examType = ExamType.TERM;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "grading_scale_id")
    private UUID gradingScaleId;

    @Column(name = "max_total_marks", precision = 8, scale = 2)
    private BigDecimal maxTotalMarks;

    @Column(name = "pass_percentage", precision = 5, scale = 2)
    private BigDecimal passPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PLANNED;

    public enum ExamType {
        TERM, MIDTERM, FINAL, UNIT_TEST, PRACTICAL, VIVA
    }

    public enum Status {
        PLANNED, SCHEDULED, IN_PROGRESS, MARKS_ENTERED, VERIFIED, APPROVED, PUBLISHED, ARCHIVED
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

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public void setAcademicYearId(UUID academicYearId) {
        this.academicYearId = academicYearId;
    }

    public UUID getSemesterId() {
        return semesterId;
    }

    public void setSemesterId(UUID semesterId) {
        this.semesterId = semesterId;
    }

    public ExamType getExamType() {
        return examType;
    }

    public void setExamType(ExamType examType) {
        this.examType = examType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public UUID getGradingScaleId() {
        return gradingScaleId;
    }

    public void setGradingScaleId(UUID gradingScaleId) {
        this.gradingScaleId = gradingScaleId;
    }

    public BigDecimal getMaxTotalMarks() {
        return maxTotalMarks;
    }

    public void setMaxTotalMarks(BigDecimal maxTotalMarks) {
        this.maxTotalMarks = maxTotalMarks;
    }

    public BigDecimal getPassPercentage() {
        return passPercentage;
    }

    public void setPassPercentage(BigDecimal passPercentage) {
        this.passPercentage = passPercentage;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
