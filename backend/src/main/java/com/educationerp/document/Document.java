package com.educationerp.document;

import com.educationerp.academic.Department;
import com.educationerp.auth.user.User;
import com.educationerp.common.persistence.BaseEntity;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One document: what it is, where its bytes are, and who may have them.
 *
 * <p>The row points at the current version and carries the metadata a reader needs without
 * opening the file. Bytes, checksums and earlier versions live in storage and
 * {@link DocumentVersion}, so a large file never sits in a table row and a superseded version is
 * still recoverable.
 */
@Entity
@Table(name = "documents",
        uniqueConstraints = @UniqueConstraint(name = "uk_documents_number", columnNames = "document_number"))
@Getter
@Setter
public class Document extends BaseEntity {

    /** Draft while being written, active while it is the version people should use. */
    public enum Status { DRAFT, ACTIVE, SUPERSEDED, ARCHIVED }

    public enum VerificationStatus { UNVERIFIED, PENDING, VERIFIED, REJECTED }

    @Column(name = "document_number", nullable = false, length = 40)
    private String documentNumber;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    /** What kind of thing this is: POLICY, CERTIFICATE, CONTRACT, REPORT and so on. */
    @Column(name = "document_type", nullable = false, length = 60)
    private String documentType;

    @Column(name = "category", length = 120)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_department_id")
    private Department ownerDepartment;

    /** What this document is about: STUDENT, EMPLOYEE, PAYROLL and so on. */
    @Column(name = "related_type", length = 60)
    private String relatedType;

    @Column(name = "related_id")
    private UUID relatedId;

    @Column(name = "storage_provider", nullable = false, length = 30)
    private String storageProvider;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 300)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    /** Lets a reader prove the file has not been swapped underneath them. */
    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    /** Keep the bytes until this date even if the document is deleted. */
    @Column(name = "retention_until")
    private LocalDate retentionUntil;

    /** Set instead of deleting the row, so the deletion itself is auditable. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** True when the document has an expiry date that has passed. */
    public boolean isExpired() {
        return expiresOn != null && expiresOn.isBefore(LocalDate.now());
    }
}
