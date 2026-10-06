package com.educationerp.student;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A guardian is a person independent of any single student, so one guardian may be
 * linked to several children. The link itself lives in {@link StudentGuardian}.
 *
 * <p>The account link is what lets a parent sign in and see only their own children. It is
 * optional: a guardian record can exist long before anybody creates a login for them.
 */
@Entity
@Table(name = "guardians")
@Getter
@Setter
public class Guardian extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone", length = 60)
    private String phone;

    @Column(name = "email", length = 180)
    private String email;

    @Column(name = "occupation", length = 150)
    private String occupation;

    @Column(name = "address", length = 400)
    private String address;

    @Column(name = "user_id")
    private UUID userId;

    public String displayName() {
        StringBuilder sb = new StringBuilder(firstName == null ? "" : firstName.trim());
        if (middleName != null && !middleName.isBlank()) {
            sb.append(' ').append(middleName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            sb.append(' ').append(lastName.trim());
        }
        return sb.toString();
    }
}