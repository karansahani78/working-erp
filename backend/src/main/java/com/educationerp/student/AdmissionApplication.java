package com.educationerp.student;

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
 * An admission application. The {@link Status} follows the blueprint lifecycle:
 * DRAFT → SUBMITTED → UNDER_REVIEW → DOCUMENT_VERIFICATION → ELIGIBILITY →
 * ENTRANCE → SELECTED / WAITLISTED / REJECTED → ADMITTED → ENROLLED.
 */
@Entity
@Table(name = "admission_applications")
public class AdmissionApplication extends BaseEntity {

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "reference_code", nullable = false, length = 40)
    private String referenceCode;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "nationality", length = 80)
    private String nationality;

    @Column(name = "phone", length = 60)
    private String phone;

    @Column(name = "email", length = 180)
    private String email;

    @Column(name = "address", length = 400)
    private String address;

    @Column(name = "photo_url", length = 400)
    private String photoUrl;

    @Column(name = "applying_program_id")
    private UUID applyingProgramId;

    @Column(name = "applying_class_id")
    private UUID applyingClassId;

    @Column(name = "previous_school", length = 200)
    private String previousSchool;

    @Column(name = "previous_qualification", length = 120)
    private String previousQualification;

    @Column(name = "previous_percentage", precision = 5, scale = 2)
    private BigDecimal previousPercentage;

    @Column(name = "entrance_score", precision = 7, scale = 2)
    private BigDecimal entranceScore;

    @Column(name = "merit_score", precision = 7, scale = 2)
    private BigDecimal meritScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status = Status.DRAFT;

    @Column(name = "submitted_at")
    private LocalDate submittedAt;

    @Column(name = "decided_at")
    private LocalDate decidedAt;

    @Column(name = "decision_notes", length = 1000)
    private String decisionNotes;

    @Column(name = "student_id")
    private UUID studentId;

    public enum Status {
        DRAFT,
        SUBMITTED,
        UNDER_REVIEW,
        DOCUMENT_VERIFICATION,
        ELIGIBILITY,
        ENTRANCE,
        SELECTED,
        WAITLISTED,
        REJECTED,
        ADMITTED,
        ENROLLED;

        /** Terminal states can no longer transition anywhere else. */
        public boolean isTerminal() {
            return this == REJECTED || this == ENROLLED;
        }
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(UUID campaignId) {
        this.campaignId = campaignId;
    }

    public String getReferenceCode() {
        return referenceCode;
    }

    public void setReferenceCode(String referenceCode) {
        this.referenceCode = referenceCode;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public UUID getApplyingProgramId() {
        return applyingProgramId;
    }

    public void setApplyingProgramId(UUID applyingProgramId) {
        this.applyingProgramId = applyingProgramId;
    }

    public UUID getApplyingClassId() {
        return applyingClassId;
    }

    public void setApplyingClassId(UUID applyingClassId) {
        this.applyingClassId = applyingClassId;
    }

    public String getPreviousSchool() {
        return previousSchool;
    }

    public void setPreviousSchool(String previousSchool) {
        this.previousSchool = previousSchool;
    }

    public String getPreviousQualification() {
        return previousQualification;
    }

    public void setPreviousQualification(String previousQualification) {
        this.previousQualification = previousQualification;
    }

    public BigDecimal getPreviousPercentage() {
        return previousPercentage;
    }

    public void setPreviousPercentage(BigDecimal previousPercentage) {
        this.previousPercentage = previousPercentage;
    }

    public BigDecimal getEntranceScore() {
        return entranceScore;
    }

    public void setEntranceScore(BigDecimal entranceScore) {
        this.entranceScore = entranceScore;
    }

    public BigDecimal getMeritScore() {
        return meritScore;
    }

    public void setMeritScore(BigDecimal meritScore) {
        this.meritScore = meritScore;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDate getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDate submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDate getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDate decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public void setDecisionNotes(String decisionNotes) {
        this.decisionNotes = decisionNotes;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }
}