package com.educationerp.exam;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A student's mark for one exam subject.
 *
 * <p>The result follows DRAFT → MARKS_ENTERED → VERIFIED → APPROVED → PUBLISHED. Once
 * published the marks are frozen: changes must go through a {@link ResultCorrection}.
 */
@Entity
@Table(name = "results")
public class Result extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "examination_id", nullable = false)
    private UUID examinationId;

    @Column(name = "exam_subject_id", nullable = false)
    private UUID examSubjectId;

    @Column(name = "enrollment_id")
    private UUID enrollmentId;

    @Column(name = "marks_obtained", precision = 8, scale = 2)
    private BigDecimal marksObtained;

    @Column(name = "max_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "percentage", precision = 6, scale = 2)
    private BigDecimal percentage;

    @Column(name = "letter_grade", length = 10)
    private String letterGrade;

    @Column(name = "grade_point", precision = 4, scale = 2)
    private BigDecimal gradePoint;

    @Column(name = "is_pass")
    private Boolean pass;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ResultStatus status = ResultStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    public enum ResultStatus {
        DRAFT, MARKS_ENTERED, VERIFIED, APPROVED, PUBLISHED
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public UUID getExaminationId() {
        return examinationId;
    }

    public void setExaminationId(UUID examinationId) {
        this.examinationId = examinationId;
    }

    public UUID getExamSubjectId() {
        return examSubjectId;
    }

    public void setExamSubjectId(UUID examSubjectId) {
        this.examSubjectId = examSubjectId;
    }

    public UUID getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(UUID enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public BigDecimal getMarksObtained() {
        return marksObtained;
    }

    public void setMarksObtained(BigDecimal marksObtained) {
        this.marksObtained = marksObtained;
    }

    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(BigDecimal maxMarks) {
        this.maxMarks = maxMarks;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public String getLetterGrade() {
        return letterGrade;
    }

    public void setLetterGrade(String letterGrade) {
        this.letterGrade = letterGrade;
    }

    public BigDecimal getGradePoint() {
        return gradePoint;
    }

    public void setGradePoint(BigDecimal gradePoint) {
        this.gradePoint = gradePoint;
    }

    public Boolean getPass() {
        return pass;
    }

    public void setPass(Boolean pass) {
        this.pass = pass;
    }

    public ResultStatus getStatus() {
        return status;
    }

    public void setStatus(ResultStatus status) {
        this.status = status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
