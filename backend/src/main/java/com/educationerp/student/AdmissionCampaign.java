package com.educationerp.student;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Admission campaign: the window during which applications are accepted for a target
 * academic year, including the configurable document requirements an applicant must
 * submit.
 */
@Entity
@Table(name = "admission_campaigns")
public class AdmissionCampaign extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "academic_year_id")
    private java.util.UUID academicYearId;

    @Column(name = "open_date")
    private LocalDate openDate;

    @Column(name = "close_date")
    private LocalDate closeDate;

    @Column(name = "application_fee", nullable = false)
    private java.math.BigDecimal applicationFee = java.math.BigDecimal.ZERO;

    @Column(name = "capacity")
    private Integer capacity;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PLANNED;

    @Column(name = "document_requirements")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String documentRequirements;

    public enum Status {
        PLANNED, OPEN, CLOSED, ARCHIVED
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

    public java.util.UUID getAcademicYearId() {
        return academicYearId;
    }

    public void setAcademicYearId(java.util.UUID academicYearId) {
        this.academicYearId = academicYearId;
    }

    public LocalDate getOpenDate() {
        return openDate;
    }

    public void setOpenDate(LocalDate openDate) {
        this.openDate = openDate;
    }

    public LocalDate getCloseDate() {
        return closeDate;
    }

    public void setCloseDate(LocalDate closeDate) {
        this.closeDate = closeDate;
    }

    public java.math.BigDecimal getApplicationFee() {
        return applicationFee;
    }

    public void setApplicationFee(java.math.BigDecimal applicationFee) {
        this.applicationFee = applicationFee;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getDocumentRequirements() {
        return documentRequirements;
    }

    public void setDocumentRequirements(String documentRequirements) {
        this.documentRequirements = documentRequirements;
    }
}