package com.educationerp.document;

import com.educationerp.auth.user.User;
import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * One version of a document's bytes.
 *
 * <p>Every upload adds a row and nothing is ever overwritten, so "which file did we send on the
 * third of March" is answerable. The head of the chain is the document's current version, and
 * only that one is the live file.
 */
@Entity
@Table(name = "document_versions",
        uniqueConstraints = @UniqueConstraint(name = "uk_document_versions",
                columnNames = {"document_id", "version_number"}))
@Getter
@Setter
public class DocumentVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "original_filename", nullable = false, length = 300)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    /** Why a new version was added, in the words of whoever added it. */
    @Column(name = "change_note", length = 500)
    private String changeNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

}
