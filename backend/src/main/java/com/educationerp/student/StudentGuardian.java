package com.educationerp.student;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Explicit parent-child link between a student and a guardian. Never assume one parent
 * equals one student: a guardian may have multiple children and a student may have
 * several guardians.
 */
@Entity
@Table(name = "student_guardians")
public class StudentGuardian extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private java.util.UUID studentId;

    @Column(name = "guardian_id", nullable = false)
    private java.util.UUID guardianId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false, length = 30)
    private Relationship relationship;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryContact;

    @Column(name = "can_pickup", nullable = false)
    private boolean canPickup;

    public enum Relationship {
        FATHER, MOTHER, GUARDIAN, OTHER
    }

    public java.util.UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(java.util.UUID studentId) {
        this.studentId = studentId;
    }

    public java.util.UUID getGuardianId() {
        return guardianId;
    }

    public void setGuardianId(java.util.UUID guardianId) {
        this.guardianId = guardianId;
    }

    public Relationship getRelationship() {
        return relationship;
    }

    public void setRelationship(Relationship relationship) {
        this.relationship = relationship;
    }

    public boolean isPrimaryContact() {
        return primaryContact;
    }

    public void setPrimaryContact(boolean primaryContact) {
        this.primaryContact = primaryContact;
    }

    public boolean isCanPickup() {
        return canPickup;
    }

    public void setCanPickup(boolean canPickup) {
        this.canPickup = canPickup;
    }
}