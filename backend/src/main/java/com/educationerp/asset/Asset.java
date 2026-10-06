package com.educationerp.asset;

import com.educationerp.common.persistence.BaseEntity;
import com.educationerp.hr.Employee;
import com.educationerp.student.Student;
import com.educationerp.auth.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One physical thing the institution owns: a projector, a desk, a bus.
 *
 * <p>The database holds the invariant that a thing which is on someone's desk says so, and that a
 * thing on nobody's desk does not pretend to have a holder. The columns are three because the
 * holder may be a member of staff, a student or a plain account, and the service refuses to set
 * more than one.
 */
@Entity
@Table(name = "assets",
        uniqueConstraints = @UniqueConstraint(name = "uk_assets_number", columnNames = "asset_number"))
@Getter
@Setter
public class Asset extends BaseEntity {

    public enum Status { AVAILABLE, ASSIGNED, IN_MAINTENANCE, LOST, DISPOSED }

    public enum Condition { NEW, GOOD, FAIR, POOR }

    public enum Disposal { SALE, RECYCLE, DONATION, SCRAPPED, WRITE_OFF }

    @Column(name = "asset_number", nullable = false, length = 40)
    private String assetNumber;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private AssetCategory category;

    @Column(name = "serial_number", length = 120)
    private String serialNumber;

    @Column(name = "brand", length = 120)
    private String brand;

    @Column(name = "model", length = 120)
    private String model;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_cost", precision = 14, scale = 2)
    private BigDecimal purchaseCost;

    @Column(name = "warranty_expiry")
    private LocalDate warrantyExpiry;

    @Column(name = "location", length = 200)
    private String location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private com.educationerp.academic.Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_employee_id")
    private Employee assignedEmployee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_student_id")
    private Student assignedStudent;

    @Column(name = "assigned_at")
    private java.time.Instant assignedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_status", nullable = false, length = 20)
    private Condition conditionStatus = Condition.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.AVAILABLE;

    /**
     * When the institution stopped being able to find this, and what it was looking for.
     *
     * <p>These are kept after the asset is found or written off: an account of an item that went
     * missing once is worth keeping, and the status says whether it is missing right now.
     */
    @Column(name = "lost_at")
    private java.time.Instant lostAt;

    @Column(name = "lost_reason", length = 500)
    private String lostReason;

    /** The moment it left the register for good, and on what terms. */
    @Column(name = "disposed_at")
    private java.time.Instant disposedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "disposal_method", length = 40)
    private Disposal disposalMethod;

    @Column(name = "disposal_notes", length = 500)
    private String disposalNotes;

    @Column(name = "disposal_value", precision = 14, scale = 2)
    private BigDecimal disposalValue;

    @Column(name = "notes", length = 500)
    private String notes;

    /** Who is holding it right now, named for a reader rather than for the database. */
    public String holderName() {
        if (assignedEmployee != null) {
            return assignedEmployee.fullName();
        }
        if (assignedStudent != null) {
            return assignedStudent.getFirstName() + " " + assignedStudent.getLastName();
        }
        if (assignedUser != null) {
            return assignedUser.getDisplayName();
        }
        return null;
    }

    /** Clear the holder, which is what returning an asset means for this row. */
    public void clearHolder() {
        assignedUser = null;
        assignedEmployee = null;
        assignedStudent = null;
        assignedAt = null;
    }
}
