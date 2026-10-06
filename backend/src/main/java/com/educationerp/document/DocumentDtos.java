package com.educationerp.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The requests and responses the document library accepts and returns. */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    /**
     * Uploading a new document.
     *
     * <p>The bytes arrive separately as a multipart part named {@code file}; this carries
     * everything about the document except the file itself.
     */
    public record UploadRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 1000) String description,
            @NotBlank String documentType,
            String category,
            UUID ownerDepartmentId,
            String relatedType,
            UUID relatedId,
            LocalDate issuedOn,
            LocalDate expiresOn,
            LocalDate retentionUntil,
            @Positive Integer pageCount,
            @Size(max = 500) String changeNote) {
    }

    /** Replacing the file on an existing document. */
    public record VersionRequest(
            @Size(max = 500) String changeNote,
            LocalDate issuedOn,
            LocalDate expiresOn) {
    }

    public record MetadataRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 1000) String description,
            @NotBlank String documentType,
            String category,
            UUID ownerUserId,
            UUID ownerDepartmentId,
            String relatedType,
            UUID relatedId,
            LocalDate issuedOn,
            LocalDate expiresOn,
            LocalDate retentionUntil,
            Document.Status status,
            @Positive Integer pageCount,
            String notes) {
    }

    public record DocumentRow(UUID id, String documentNumber, String title, String description,
                              String documentType, String category, UUID ownerUserId,
                              String ownerName, UUID ownerDepartmentId, String ownerDepartmentName,
                              String relatedType, UUID relatedId, String storageProvider,
                              String originalFilename, String contentType, long fileSize,
                              String checksumSha256, Integer pageCount, int currentVersion,
                              Document.Status status, Document.VerificationStatus verificationStatus,
                              UUID verifiedBy, Instant verifiedAt, LocalDate issuedOn,
                              LocalDate expiresOn, LocalDate retentionUntil, boolean expired,
                              boolean deleted, Instant createdAt, UUID createdBy) {
    }

    public record VersionRow(UUID id, int versionNumber, String originalFilename,
                             String contentType, long fileSize, String checksumSha256,
                             String changeNote, UUID uploadedBy, Instant createdAt) {
    }

    public record GrantRequest(
            @NotNull DocumentAccess.PrincipalType principalType,
            UUID principalUserId,
            String principalRole,
            @NotNull DocumentAccess.AccessLevel accessLevel,
            Instant expiresAt) {
    }

    public record AccessRow(UUID id, DocumentAccess.PrincipalType principalType, UUID principalUserId,
                            String principalName, String principalRole,
                            DocumentAccess.AccessLevel accessLevel, UUID grantedBy,
                            Instant grantedAt, Instant expiresAt, boolean expired) {
    }

    public record VerifyRequest(
            boolean approved,
            String note) {
    }

    /** Metadata plus the file, for a download. */
    public record Download(DocumentRow document, String filename, String contentType,
                           long fileSize, String checksumSha256, java.io.InputStream content) {
    }

    public record DocumentDetail(DocumentRow document, List<VersionRow> versions,
                                 List<AccessRow> access) {
    }

    /** What the caller may do with a document, so the front end can grey out what it cannot. */
    public record AccessLevelFor(boolean canView, boolean canEdit, boolean canManage) {
    }

    public record DocumentOverview(long totalDocuments, long active, long draft,
                                   long archived, long verified, long pendingVerification,
                                   long rejected, long expired, long expiringSoon,
                                   long totalBytes) {
    }
}
