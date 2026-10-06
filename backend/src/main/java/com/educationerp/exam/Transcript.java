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

/** Cumulative academic record for a student. */
@Entity
@Table(name = "transcripts")
public class Transcript extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "reference_code", nullable = false, length = 40)
    private String referenceCode;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();

    @Column(name = "generated_by")
    private UUID generatedBy;

    @Column(name = "total_credits", precision = 6, scale = 2)
    private BigDecimal totalCredits;

    @Column(name = "cumulative_gpa", precision = 4, scale = 2)
    private BigDecimal cumulativeGpa;

    @Column(name = "cumulative_cgpa", precision = 4, scale = 2)
    private BigDecimal cumulativeCgpa;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "finalised_at")
    private Instant finalisedAt;

    public enum Status {
        DRAFT, FINALISED, ISSUED
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public String getReferenceCode() {
        return referenceCode;
    }

    public void setReferenceCode(String referenceCode) {
        this.referenceCode = referenceCode;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public UUID getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(UUID generatedBy) {
        this.generatedBy = generatedBy;
    }

    public BigDecimal getTotalCredits() {
        return totalCredits;
    }

    public void setTotalCredits(BigDecimal totalCredits) {
        this.totalCredits = totalCredits;
    }

    public BigDecimal getCumulativeGpa() {
        return cumulativeGpa;
    }

    public void setCumulativeGpa(BigDecimal cumulativeGpa) {
        this.cumulativeGpa = cumulativeGpa;
    }

    public BigDecimal getCumulativeCgpa() {
        return cumulativeCgpa;
    }

    public void setCumulativeCgpa(BigDecimal cumulativeCgpa) {
        this.cumulativeCgpa = cumulativeCgpa;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getFinalisedAt() {
        return finalisedAt;
    }

    public void setFinalisedAt(Instant finalisedAt) {
        this.finalisedAt = finalisedAt;
    }
}
