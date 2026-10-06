package com.educationerp.document;

import com.educationerp.auth.role.Role;
import com.educationerp.auth.user.User;
import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.EnumType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Who may do what with a document.
 *
 * <p>Access is granted to a person or to a role, never to both, and an expiring grant stops
 * working on its own: a contractor who leaves in June does not need somebody to remember to
 * revoke their access in July.
 */
@Entity
@Table(name = "document_access")
@Getter
@Setter
public class DocumentAccess extends BaseEntity {

    public enum PrincipalType { USER, ROLE }

    /** MANAGE can also grant, revoke and delete; EDIT can add versions. */
    public enum AccessLevel {

        VIEW, EDIT, MANAGE;

        /** Whether holding this level is enough for the level asked for. */
        public boolean covers(AccessLevel required) {
            return switch (required) {
                case VIEW -> true;
                case EDIT -> this == EDIT || this == MANAGE;
                case MANAGE -> this == MANAGE;
            };
        }
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, length = 20)
    private PrincipalType principalType = PrincipalType.USER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "principal_user_id")
    private User principalUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_role", length = 40)
    private Role principalRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 20)
    private AccessLevel accessLevel = AccessLevel.VIEW;

    @Column(name = "granted_by")
    private java.util.UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt = Instant.now();

    /** Null means the grant does not lapse. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /** Whether this grant covers at least the level asked for. */
    public boolean covers(AccessLevel required) {
        return accessLevel.covers(required);
    }

    public String principalName() {
        return principalType == PrincipalType.USER
                ? (principalUser == null ? "Unknown user" : principalUser.getDisplayName())
                : (principalRole == null ? "Unknown role" : principalRole.name());
    }
}
