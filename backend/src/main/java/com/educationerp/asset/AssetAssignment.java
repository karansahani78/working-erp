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
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One spell of an asset being in somebody's hands.
 *
 * <p>Never closed by editing. A new row starts each time the asset changes hands, so the
 * question "who had the projector when it went missing" is answered by the register rather than
 * by whoever can still remember.
 */
@Entity
@Table(name = "asset_assignments")
@Getter
@Setter
public class AssetAssignment extends BaseEntity {

    /** Who is holding it. The name is kept so history survives a renamed or deleted account. */
    public enum HolderType { USER, EMPLOYEE, STUDENT, EXTERNAL }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "holder_type", nullable = false, length = 20)
    private HolderType holderType = HolderType.USER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "holder_user_id")
    private User holderUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "holder_employee_id")
    private Employee holderEmployee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "holder_student_id")
    private Student holderStudent;

    @Column(name = "holder_name", nullable = false, length = 200)
    private String holderName;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "assigned_by")
    private UUID assignedBy;

    /** Left null while the asset is out. Set when it comes back. */
    @Column(name = "returned_at")
    private Instant returnedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_out", nullable = false, length = 20)
    private Asset.Condition conditionOut = Asset.Condition.GOOD;

    /** What it looked like coming back. Null until then. */
    @Enumerated(EnumType.STRING)
    @Column(name = "condition_in", length = 20)
    private Asset.Condition conditionIn;

    @Column(name = "notes", length = 400)
    private String notes;

    public boolean isOpen() {
        return returnedAt == null;
    }
}
