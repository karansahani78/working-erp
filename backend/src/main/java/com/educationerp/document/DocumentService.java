package com.educationerp.document;

import com.educationerp.academic.Department;
import com.educationerp.academic.DepartmentRepository;
import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.role.Role;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.auth.user.AuthenticatedUser;
import com.educationerp.auth.user.User;
import com.educationerp.auth.user.UserRepository;
import com.educationerp.common.api.PageResponse;
import com.educationerp.common.error.AppException;
import com.educationerp.common.numbering.DocumentSequence;
import com.educationerp.common.numbering.SequenceNumberGenerator;
import com.educationerp.document.storage.ObjectStorage;
import com.educationerp.document.storage.StorageProperties;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The document library.
 *
 * <p>The database holds the paperwork about the paperwork: title, owner, expiry, who may read
 * it. The bytes go to storage under a key this service invents, and every upload also writes a
 * version row so that a superseded file can still be produced. Nothing is ever overwritten,
 * because the day somebody needs the file as it was, that is the day it is too late to have
 * overwritten it.
 *
 * <p>Access is explicit. The owner keeps control of their document, grants name a person or a
 * role, and grants may lapse. A caller who holds DOCUMENT_DELETE is treated as an administrator
 * for documents, which is what makes a document reachable by someone other than its owner at
 * all -- otherwise a lost password would mean an unopenable file.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DocumentService {

    private static final int EXPIRY_SOON_DAYS = 90;

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final DocumentAccessRepository access;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final ObjectStorage storage;
    private final StorageProperties properties;
    private final SequenceNumberGenerator numbers;
    private final InstitutionService institutions;
    private final AuthorizationChecker auth;
    private final AuditService audit;

    // ------------------------------------------------------------------ uploads

    public DocumentDtos.DocumentRow upload(MultipartFile file, DocumentDtos.UploadRequest request) {
        guard("DOCUMENT_UPLOAD");
        checkFile(file);
        String number = numbers.next(DocumentSequence.Kind.DOCUMENT);
        String key = storageKey(number, 1, file.getOriginalFilename());

        Stored stored = store(key, file);
        Document document = new Document();
        document.setDocumentNumber(number);
        document.setTitle(request.title().trim());
        document.setDescription(request.description());
        document.setDocumentType(request.documentType().trim().toUpperCase(Locale.ROOT));
        document.setCategory(request.category());
        document.setOwnerDepartment(request.ownerDepartmentId() == null ? null
                : departments.findById(request.ownerDepartmentId())
                .orElseThrow(() -> AppException.notFound("Department")));
        document.setRelatedType(request.relatedType());
        document.setRelatedId(request.relatedId());
        document.setStorageProvider(storage.provider());
        document.setStorageKey(stored.key());
        document.setOriginalFilename(safeName(file.getOriginalFilename()));
        document.setContentType(file.getContentType());
        document.setFileSize(stored.size());
        document.setChecksumSha256(stored.checksum());
        document.setPageCount(request.pageCount());
        document.setCurrentVersion(1);
        document.setStatus(Document.Status.DRAFT);
        document.setVerificationStatus(Document.VerificationStatus.UNVERIFIED);
        document.setIssuedOn(request.issuedOn());
        document.setExpiresOn(request.expiresOn());
        document.setRetentionUntil(request.retentionUntil());
        requireExpiryOrder(request.issuedOn(), request.expiresOn());
        documents.save(document);

        DocumentVersion version = new DocumentVersion();
        version.setDocument(document);
        version.setVersionNumber(1);
        version.setStorageKey(stored.key());
        version.setFileSize(stored.size());
        version.setChecksumSha256(stored.checksum());
        version.setOriginalFilename(document.getOriginalFilename());
        version.setContentType(document.getContentType());
        version.setChangeNote(request.changeNote() == null ? "First version" : request.changeNote());
        version.setUploadedBy(caller());
        versions.save(version);

        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .module("DOCUMENTS")
                .entityType("Document")
                .entityId(document.getId().toString())
                .summary("Document " + number + " (" + document.getTitle() + ") uploaded")
                .build());
        return documentRow(document);
    }

    /**
     * Add a version.
     *
     * <p>The previous file stays exactly where it is and becomes unreferenced by the document's
     * current pointer, so the chain is complete. The number is read, incremented and written in
     * one locked step to stop two simultaneous uploads claiming the same version.
     */
    @Transactional
    public DocumentDtos.DocumentRow addVersion(UUID documentId, MultipartFile file,
                                               DocumentDtos.VersionRequest request) {
        Document document = document(documentId);
        requireLevel(document, DocumentAccess.AccessLevel.EDIT);
        checkFile(file);

        Document locked = documents.findByIdForUpdate(documentId)
                .orElseThrow(() -> AppException.notFound("Document"));
        int next = locked.getCurrentVersion() + 1;
        String key = storageKey(locked.getDocumentNumber(), next, file.getOriginalFilename());
        Stored stored = store(key, file);

        DocumentVersion version = new DocumentVersion();
        version.setDocument(locked);
        version.setVersionNumber(next);
        version.setStorageKey(stored.key());
        version.setFileSize(stored.size());
        version.setChecksumSha256(stored.checksum());
        version.setOriginalFilename(safeName(file.getOriginalFilename()));
        version.setContentType(file.getContentType());
        version.setChangeNote(request.changeNote());
        version.setUploadedBy(caller());
        versions.save(version);

        locked.setCurrentVersion(next);
        locked.setStorageKey(stored.key());
        locked.setOriginalFilename(version.getOriginalFilename());
        locked.setContentType(version.getContentType());
        locked.setFileSize(stored.size());
        locked.setChecksumSha256(stored.checksum());
        if (request.issuedOn() != null) {
            locked.setIssuedOn(request.issuedOn());
        }
        if (request.expiresOn() != null) {
            locked.setExpiresOn(request.expiresOn());
        }
        requireExpiryOrder(locked.getIssuedOn(), locked.getExpiresOn());
        // A new file has not been looked at by anybody, so the old verdict does not carry over.
        locked.setVerificationStatus(Document.VerificationStatus.PENDING);
        locked.setVerifiedBy(null);
        locked.setVerifiedAt(null);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .module("DOCUMENTS")
                .entityType("Document")
                .entityId(locked.getId().toString())
                .summary("Version " + next + " of " + locked.getDocumentNumber() + " uploaded")
                .build());
        return documentRow(locked);
    }

    // ----------------------------------------------------------------- retrieval

    public PageResponse<DocumentDtos.DocumentRow> search(String documentType, Document.Status status,
                                                         Document.VerificationStatus verificationStatus,
                                                         UUID ownerUserId, String relatedType,
                                                         UUID relatedId, String term,
                                                         Pageable pageable) {
        guard("DOCUMENT_READ");
        String like = term == null || term.isBlank() ? null
                : "%" + term.trim().toLowerCase() + "%";
        // Listing is a form of reading, so a document this caller may not open must not appear in
        // the list either. Who may read which document is held in the access table, so the rule
        // goes into the query rather than being applied to the page afterwards.
        AuthenticatedUser current = auth.requireUser();
        Page<Document> page = documents.search(blankToNull(documentType), status,
                verificationStatus, ownerUserId, blankToNull(relatedType), relatedId, like,
                current.userId(), rolesOf(current), current.hasPermission("DOCUMENT_DELETE"),
                Instant.now(), pageable);
        return PageResponse.from(page, this::documentRow);
    }

    /**
     * The caller's roles as the enum, for a grant held by role.
     *
     * <p>A name that is not a role is dropped rather than allowed to fail the whole query: the
     * session carries role names from the token, and one stale name should cost a grant rather
     * than the document list.
     */
    private List<Role> rolesOf(AuthenticatedUser current) {
        return current.roles().stream()
                .map(name -> {
                    try {
                        return Role.valueOf(name);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public DocumentDtos.DocumentDetail detail(UUID id) {
        guard("DOCUMENT_READ");
        Document document = document(id);
        requireLevel(document, DocumentAccess.AccessLevel.VIEW);
        return new DocumentDtos.DocumentDetail(documentRow(document),
                versions.findByDocumentIdOrderByVersionNumberDesc(id).stream()
                        .map(this::versionRow).toList(),
                access.findByDocumentIdOrderByGrantedAtDesc(id).stream()
                        .map(this::accessRow).toList());
    }

    /** Everything linked to one record, for the panel beside a student or an employee. */
    public List<DocumentDtos.DocumentRow> related(String relatedType, UUID relatedId) {
        guard("DOCUMENT_READ");
        return documents.findRelated(relatedType, relatedId).stream()
                .filter(document -> canView(document))
                .map(this::documentRow)
                .toList();
    }

    public List<DocumentDtos.VersionRow> versionHistory(UUID documentId) {
        guard("DOCUMENT_READ");
        Document document = document(documentId);
        requireLevel(document, DocumentAccess.AccessLevel.VIEW);
        return versions.findByDocumentIdOrderByVersionNumberDesc(documentId).stream()
                .map(this::versionRow).toList();
    }

    /**
     * Hand back the bytes.
     *
     * <p>Checked against the recorded checksum before the caller sees them, so a file that has
     * been altered on disk is reported rather than served.
     *
     * <p>Verifying means reading the whole file to the end, which is also the end of the stream,
     * so the file is opened a second time for the caller. Reading it into memory instead would
     * hand back a stream that works, at the price of holding a document of unknown size in the
     * heap of whoever asked for it.
     */
    public DocumentDtos.Download download(UUID documentId, Integer versionNumber) {
        guard("DOCUMENT_READ");
        Document document = document(documentId);
        requireLevel(document, DocumentAccess.AccessLevel.VIEW);
        DocumentVersion version = versionNumber == null
                ? versions.findFirstByDocumentIdOrderByVersionNumberDesc(documentId)
                .orElseThrow(() -> AppException.notFound("Document version"))
                : versions.findByDocumentIdAndVersionNumber(documentId, versionNumber)
                .orElseThrow(() -> AppException.notFound("Document version"));
        verifyChecksum(version.getChecksumSha256(), open(version.getStorageKey()));
        audit.record(AuditEvent.builder()
                .action(AuditAction.READ)
                .module("DOCUMENTS")
                .entityType("Document")
                .entityId(document.getId().toString())
                .summary("Downloaded " + document.getDocumentNumber() + " version "
                        + version.getVersionNumber())
                .succeeded(true)
                .build());
        return new DocumentDtos.Download(documentRow(document), version.getOriginalFilename(),
                version.getContentType(), version.getFileSize(), version.getChecksumSha256(),
                open(version.getStorageKey()));
    }

    /** A fresh stream over a stored file. */
    private InputStream open(String storageKey) {
        try {
            return storage.open(storageKey);
        } catch (IOException e) {
            throw AppException.notFound("The stored file for this document");
        }
    }

    /** The access rules applied to this caller, so the front end can hide what they cannot use. */
    public DocumentDtos.AccessLevelFor effectiveLevel(UUID documentId) {
        Document document = document(documentId);
        return new DocumentDtos.AccessLevelFor(
                can(document, DocumentAccess.AccessLevel.VIEW),
                can(document, DocumentAccess.AccessLevel.EDIT),
                can(document, DocumentAccess.AccessLevel.MANAGE));
    }

    // ------------------------------------------------------------------ metadata

    public DocumentDtos.DocumentRow updateMetadata(UUID documentId,
                                                   DocumentDtos.MetadataRequest request) {
        Document document = document(documentId);
        requireLevel(document, DocumentAccess.AccessLevel.MANAGE);
        document.setTitle(request.title().trim());
        document.setDescription(request.description());
        document.setDocumentType(request.documentType().trim().toUpperCase(Locale.ROOT));
        document.setCategory(request.category());
        document.setRelatedType(request.relatedType());
        document.setRelatedId(request.relatedId());
        document.setIssuedOn(request.issuedOn());
        document.setExpiresOn(request.expiresOn());
        document.setRetentionUntil(request.retentionUntil());
        document.setPageCount(request.pageCount());
        if (request.status() != null) {
            document.setStatus(request.status());
        }
        requireExpiryOrder(document.getIssuedOn(), document.getExpiresOn());
        if (request.ownerUserId() != null) {
            document.setOwner(users.findById(request.ownerUserId())
                    .orElseThrow(() -> AppException.notFound("User")));
        }
        if (request.ownerDepartmentId() != null) {
            document.setOwnerDepartment(departments.findById(request.ownerDepartmentId())
                    .orElseThrow(() -> AppException.notFound("Department")));
        }
        return documentRow(document);
    }

    /**
     * Record a verdict on the file.
     *
     * <p>A rejected document is not deleted. It is marked, so the person who uploaded it can
     * send a corrected version instead of wondering where the file went.
     */
    public DocumentDtos.DocumentRow verify(UUID documentId, DocumentDtos.VerifyRequest request) {
        guard("DOCUMENT_VERIFY");
        Document document = document(documentId);
        document.setVerificationStatus(request.approved()
                ? Document.VerificationStatus.VERIFIED
                : Document.VerificationStatus.REJECTED);
        AuthenticatedUser current = auth.requireUser();
        document.setVerifiedBy(callerUser());
        document.setVerifiedAt(Instant.now());
        if (request.approved() && document.getStatus() == Document.Status.DRAFT) {
            document.setStatus(Document.Status.ACTIVE);
            if (document.getIssuedOn() == null) {
                document.setIssuedOn(LocalDate.now());
            }
        }
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .module("DOCUMENTS")
                .entityType("Document")
                .entityId(document.getId().toString())
                .summary((request.approved() ? "Verified " : "Rejected ") + document.getDocumentNumber()
                        + (request.note() == null ? "" : ": " + request.note()))
                .build());
        return documentRow(document);
    }

    /**
     * Take a document out of circulation.
     *
     * <p>The row is kept and marked, and the bytes are removed only if nothing needs them kept
     * for longer; the deletion is itself recorded, which is the whole reason it is a soft delete.
     */
    public DocumentDtos.DocumentRow softDelete(UUID documentId, String reason) {
        guard("DOCUMENT_DELETE");
        Document document = document(documentId);
        document.setDeletedAt(Instant.now());
        document.setDeletedBy(auth.requireUser().userId());
        document.setStatus(Document.Status.ARCHIVED);
        boolean keepBytes = document.getRetentionUntil() != null
                && document.getRetentionUntil().isAfter(LocalDate.now());
        if (!keepBytes) {
            try {
                storage.delete(document.getStorageKey());
                for (DocumentVersion version : versions.findByDocumentIdOrderByVersionNumberDesc(
                        documentId)) {
                    storage.delete(version.getStorageKey());
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not remove the stored file for "
                        + document.getDocumentNumber(), e);
            }
        }
        audit.record(AuditEvent.builder()
                .action(AuditAction.DELETE)
                .module("DOCUMENTS")
                .entityType("Document")
                .entityId(document.getId().toString())
                .summary("Document " + document.getDocumentNumber() + " deleted"
                        + (reason == null ? "" : ": " + reason)
                        + (keepBytes ? " (bytes kept until the retention date)" : ""))
                .build());
        return documentRow(document);
    }

    // -------------------------------------------------------------------- access

    public DocumentDtos.AccessRow grant(UUID documentId, DocumentDtos.GrantRequest request) {
        Document document = document(documentId);
        requireLevel(document, DocumentAccess.AccessLevel.MANAGE);
        guardPrincipal(request);
        java.util.Optional<DocumentAccess> existing = existingGrant(documentId, request);

        DocumentAccess grantAccess = existing.orElseGet(DocumentAccess::new);
        grantAccess.setDocument(document);
        grantAccess.setPrincipalType(request.principalType());
        if (request.principalType() == DocumentAccess.PrincipalType.USER) {
            grantAccess.setPrincipalUser(users.findById(request.principalUserId())
                    .orElseThrow(() -> AppException.notFound("User")));
            grantAccess.setPrincipalRole(null);
        } else {
            grantAccess.setPrincipalRole(role(request.principalRole()));
            grantAccess.setPrincipalUser(null);
        }
        grantAccess.setAccessLevel(request.accessLevel());
        grantAccess.setExpiresAt(request.expiresAt());
        grantAccess.setGrantedAt(Instant.now());
        grantAccess.setGrantedBy(auth.requireUser().userId());
        return accessRow(access.save(grantAccess));
    }

    public void revoke(UUID documentId, UUID grantId) {
        Document document = document(documentId);
        requireLevel(document, DocumentAccess.AccessLevel.MANAGE);
        DocumentAccess grantAccess = access.findById(grantId)
                .orElseThrow(() -> AppException.notFound("Access grant"));
        if (!grantAccess.getDocument().getId().equals(documentId)) {
            throw AppException.rule("That grant belongs to a different document.");
        }
        access.delete(grantAccess);
    }

    // ----------------------------------------------------------------- reporting

    public List<DocumentDtos.DocumentRow> expiringSoon(int days) {
        guard("DOCUMENT_READ");
        LocalDate today = LocalDate.now();
        return documents.findExpiringBetween(today, today.plusDays(days)).stream()
                .filter(this::canView)
                .map(this::documentRow)
                .toList();
    }

    public DocumentDtos.DocumentOverview overview() {
        guard("DOCUMENT_READ");
        // Counts are as revealing as titles, so they are cut down to what this caller may read.
        List<Document> live = documents.findAll().stream()
                .filter(document -> !document.isDeleted())
                .filter(this::canView)
                .toList();
        LocalDate today = LocalDate.now();
        long bytes = live.stream().mapToLong(Document::getFileSize).sum();
        return new DocumentDtos.DocumentOverview(live.size(),
                live.stream().filter(d -> d.getStatus() == Document.Status.ACTIVE).count(),
                live.stream().filter(d -> d.getStatus() == Document.Status.DRAFT).count(),
                live.stream().filter(d -> d.getStatus() == Document.Status.ARCHIVED).count(),
                live.stream().filter(d -> d.getVerificationStatus()
                        == Document.VerificationStatus.VERIFIED).count(),
                live.stream().filter(d -> d.getVerificationStatus()
                        == Document.VerificationStatus.PENDING).count(),
                live.stream().filter(d -> d.getVerificationStatus()
                        == Document.VerificationStatus.REJECTED).count(),
                live.stream().filter(Document::isExpired).count(),
                live.stream().filter(d -> d.getExpiresOn() != null
                        && !d.getExpiresOn().isBefore(today)
                        && !d.getExpiresOn().isAfter(today.plusDays(EXPIRY_SOON_DAYS))).count(),
                bytes);
    }

    // ------------------------------------------------------------------ helpers

    private void guard(String permission) {
        institutions.requireModuleEnabled(ModuleKey.DOCUMENTS);
        auth.requirePermission(permission);
    }

    private void checkFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AppException.rule("Attach a file to upload.");
        }
        if (file.getSize() > properties.getMaxDocumentBytes()) {
            throw AppException.rule("That file is larger than the "
                    + (properties.getMaxDocumentBytes() / (1024 * 1024)) + "MB limit.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!properties.getAllowedContentTypes().contains(type)) {
            throw AppException.rule("Files of type " + type + " are not accepted.");
        }
    }

    /**
     * A key of our own construction.
     *
     * <p>The extension is taken from the filename only after being reduced to letters and
     * digits, and the whole thing lands under a generated name, so nothing a caller sends can
     * steer where the file is written.
     */
    private String storageKey(String documentNumber, int version, String filename) {
        String extension = "";
        if (filename != null && filename.contains(".")) {
            String candidate = filename.substring(filename.lastIndexOf('.') + 1)
                    .replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
            if (!candidate.isEmpty() && candidate.length() <= 8) {
                extension = "." + candidate;
            }
        }
        return "documents/" + documentNumber.replaceAll("[^A-Za-z0-9-]", "")
                + "/v" + version + "-" + UUID.randomUUID() + extension;
    }

    private String safeName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document";
        }
        // Just the last path segment: a filename is a label, never a path.
        String name = filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        return name.length() > 300 ? name.substring(0, 300) : name;
    }

    /** What came back: the bytes are on disk, and we know their size and fingerprint. */
    private record Stored(String key, long size, String checksum) {
    }

    /**
     * Stream the upload into storage while fingerprinting it.
     *
     * <p>One pass, so the file is never held in memory and never written twice.
     */
    private Stored store(String key, MultipartFile file) {
        MessageDigest digest = sha256();
        try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
            storage.put(key, in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
        return new Stored(key, storage.size(key), HexFormat.of().formatHex(digest.digest()));
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    /**
     * Confirm the bytes are the ones we recorded.
     *
     * <p>Reading them once here costs a pass and turns a silently altered file into a refusal.
     */
    private void verifyChecksum(String expected, InputStream content) {
        MessageDigest digest = sha256();
        try (InputStream in = new DigestInputStream(content, digest)) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the stored file", e);
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(expected)) {
            throw AppException.rule("The stored file does not match its recorded checksum; "
                    + "it may have been changed outside the system.");
        }
    }

    private void requireExpiryOrder(LocalDate issuedOn, LocalDate expiresOn) {
        if (issuedOn != null && expiresOn != null && expiresOn.isBefore(issuedOn)) {
            throw AppException.rule("An expiry date cannot be before the issue date.");
        }
    }

    private void guardPrincipal(DocumentDtos.GrantRequest request) {
        boolean namesUser = request.principalUserId() != null;
        boolean namesRole = request.principalRole() != null && !request.principalRole().isBlank();
        if (namesUser == namesRole) {
            throw AppException.rule("Name either a user or a role, not both.");
        }
        boolean consistent = request.principalType() == DocumentAccess.PrincipalType.USER
                ? namesUser : namesRole;
        if (!consistent) {
            throw AppException.rule("The principal type does not match who was named.");
        }
    }

    private java.util.Optional<DocumentAccess> existingGrant(UUID documentId,
                                                            DocumentDtos.GrantRequest request) {
        return request.principalType() == DocumentAccess.PrincipalType.USER
                ? access.findByDocumentIdAndPrincipalUserId(documentId, request.principalUserId())
                : access.findByDocumentIdAndPrincipalRole(documentId,
                role(request.principalRole()));
    }

    /** Roles are a fixed catalogue, so an unknown name is refused rather than looked up. */
    private Role role(String name) {
        for (Role role : Role.values()) {
            if (role.name().equalsIgnoreCase(name.trim())) {
                return role;
            }
        }
        throw AppException.notFound("Role");
    }

    /**
     * The most a caller may do with this document, or null for nothing.
     *
     * <p>Checked in order of strength so an administrator is not also forced to be the owner.
     */
    private DocumentAccess.AccessLevel levelFor(Document document,
                                                DocumentAccess.AccessLevel required) {
        AuthenticatedUser current = auth.requireUser();
        boolean administrator = current.hasPermission("DOCUMENT_DELETE");
        if (document.getOwner() != null && document.getOwner().getId().equals(current.userId())) {
            return DocumentAccess.AccessLevel.MANAGE;
        }
        if (document.getCreatedBy() != null
                && current.userId().equals(document.getCreatedBy())) {
            return DocumentAccess.AccessLevel.MANAGE;
        }
        DocumentAccess.AccessLevel best = null;
        for (DocumentAccess grantAccess : access.findByDocumentIdOrderByGrantedAtDesc(
                document.getId())) {
            if (grantAccess.isExpired()) {
                continue;
            }
            boolean applies = grantAccess.getPrincipalType() == DocumentAccess.PrincipalType.USER
                    ? grantAccess.getPrincipalUser() != null
                    && grantAccess.getPrincipalUser().getId().equals(current.userId())
                    : grantAccess.getPrincipalRole() != null
                    && current.roles().contains(grantAccess.getPrincipalRole().name());
            if (!applies || !grantAccess.covers(required)) {
                continue;
            }
            best = grantAccess.getAccessLevel();
            if (best == DocumentAccess.AccessLevel.MANAGE) {
                return best;
            }
        }
        if (administrator) {
            return DocumentAccess.AccessLevel.MANAGE;
        }
        return best;
    }

    private boolean canView(Document document) {
        return levelFor(document, DocumentAccess.AccessLevel.VIEW) != null;
    }

    private boolean can(Document document, DocumentAccess.AccessLevel required) {
        DocumentAccess.AccessLevel held = levelFor(document, required);
        return held != null && held.covers(required);
    }

    private void requireLevel(Document document, DocumentAccess.AccessLevel required) {
        if (!can(document, required)) {
            throw AppException.denied("You do not have access to this document.");
        }
    }

    private User caller() {
        UUID id = auth.requireUser().userId();
        return users.findById(id).orElse(null);
    }

    private User callerUser() {
        return caller();
    }

    private Document document(UUID id) {
        Document document = documents.findById(id).orElseThrow(() -> AppException.notFound("Document"));
        if (document.isDeleted() && !auth.hasPermission("DOCUMENT_DELETE")) {
            throw AppException.notFound("Document");
        }
        return document;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // -------------------------------------------------------------------- rows

    private DocumentDtos.DocumentRow documentRow(Document document) {
        return new DocumentDtos.DocumentRow(document.getId(), document.getDocumentNumber(),
                document.getTitle(), document.getDescription(), document.getDocumentType(),
                document.getCategory(),
                document.getOwner() == null ? null : document.getOwner().getId(),
                document.getOwner() == null ? null : document.getOwner().getDisplayName(),
                document.getOwnerDepartment() == null ? null : document.getOwnerDepartment().getId(),
                document.getOwnerDepartment() == null ? null : document.getOwnerDepartment().getName(),
                document.getRelatedType(), document.getRelatedId(), document.getStorageProvider(),
                document.getOriginalFilename(), document.getContentType(), document.getFileSize(),
                document.getChecksumSha256(), document.getPageCount(), document.getCurrentVersion(),
                document.getStatus(), document.getVerificationStatus(),
                document.getVerifiedBy() == null ? null : document.getVerifiedBy().getId(),
                document.getVerifiedAt(), document.getIssuedOn(), document.getExpiresOn(),
                document.getRetentionUntil(), document.isExpired(), document.isDeleted(),
                document.getCreatedAt(), document.getCreatedBy());
    }

    private DocumentDtos.VersionRow versionRow(DocumentVersion version) {
        return new DocumentDtos.VersionRow(version.getId(), version.getVersionNumber(),
                version.getOriginalFilename(), version.getContentType(), version.getFileSize(),
                version.getChecksumSha256(), version.getChangeNote(),
                version.getUploadedBy() == null ? null : version.getUploadedBy().getId(),
                version.getCreatedAt());
    }

    private DocumentDtos.AccessRow accessRow(DocumentAccess grantAccess) {
        return new DocumentDtos.AccessRow(grantAccess.getId(), grantAccess.getPrincipalType(),
                grantAccess.getPrincipalUser() == null ? null
                : grantAccess.getPrincipalUser().getId(),
                grantAccess.principalName(),
                grantAccess.getPrincipalRole() == null ? null
                : grantAccess.getPrincipalRole().name(),
                grantAccess.getAccessLevel(), grantAccess.getGrantedBy(),
                grantAccess.getGrantedAt(), grantAccess.getExpiresAt(), grantAccess.isExpired());
    }
}
