package com.educationerp.library;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Somebody allowed to borrow.
 *
 * <p>A member is a reference to a person the rest of the system already knows -- a student, a
 * member of staff, or a sign-in account -- rather than a copy of their details. Somebody who
 * is both a student and staff is one member record, not two people who happen to share a name.
 */
@Entity
@Table(name = "library_members")
@Getter
@Setter
public class LibraryMember extends BaseEntity {

    public enum Status { ACTIVE, SUSPENDED, EXPIRED }

    @Column(name = "member_code", nullable = false, length = 40)
    private String memberCode;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "external_name", length = 200)
    private String externalName;

    @Column(name = "external_phone", length = 60)
    private String externalPhone;

    @Column(name = "external_email", length = 180)
    private String externalEmail;

    @Column(name = "max_books", nullable = false)
    private int maxBooks = 3;

    @Column(name = "membership_start", nullable = false)
    private LocalDate membershipStart = LocalDate.now();

    @Column(name = "membership_end")
    private LocalDate membershipEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    public boolean isActiveOn(LocalDate on) {
        return status == Status.ACTIVE
                && !on.isBefore(membershipStart)
                && (membershipEnd == null || !on.isAfter(membershipEnd));
    }

    /** What to call this person, whichever way they are linked. */
    public String displayName(String studentName, String employeeName, String loginName) {
        if (studentName != null) {
            return studentName;
        }
        if (employeeName != null) {
            return employeeName;
        }
        if (loginName != null) {
            return loginName;
        }
        return externalName == null ? memberCode : externalName;
    }
}
