package com.educationerp.document.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import com.educationerp.document.Document;
import com.educationerp.document.DocumentAccess;
import com.educationerp.document.DocumentDtos;
import com.educationerp.document.DocumentService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The document library over HTTP.
 *
 * <p>Uploads and new versions take the file as a multipart part called {@code file} and the
 * metadata as a JSON part called {@code metadata}, because the two have to travel together and
 * the metadata is the part worth validating before anything is written to disk.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentDtos.DocumentRow> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("metadata") @Valid DocumentDtos.UploadRequest request) {
        return ApiResponse.ok(documents.upload(file, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<DocumentDtos.DocumentRow>> search(
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) Document.Status status,
            @RequestParam(required = false) Document.VerificationStatus verificationStatus,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) String relatedType,
            @RequestParam(required = false) UUID relatedId,
            @RequestParam(required = false) String term,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(documents.search(documentType, status, verificationStatus,
                ownerUserId, relatedType, relatedId, term, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<DocumentDtos.DocumentDetail> detail(@PathVariable UUID id) {
        return ApiResponse.ok(documents.detail(id));
    }

    @PutMapping("/{id}/metadata")
    public ApiResponse<DocumentDtos.DocumentRow> updateMetadata(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentDtos.MetadataRequest request) {
        return ApiResponse.ok(documents.updateMetadata(id, request));
    }

    /** Replace the file, keeping the old one as a numbered version. */
    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentDtos.DocumentRow> addVersion(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestPart("metadata") @Valid DocumentDtos.VersionRequest request) {
        return ApiResponse.ok(documents.addVersion(id, file, request));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<DocumentDtos.VersionRow>> versions(@PathVariable UUID id) {
        return ApiResponse.ok(documents.versionHistory(id));
    }

    /**
     * Hand back the file itself.
     *
     * <p>Sent as an attachment by default so a browser never renders an uploaded file from this
     * origin; {@code inline=true} is offered for the document viewer, which needs to display PDFs.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id,
                                             @RequestParam(required = false) Integer version,
                                             @RequestParam(defaultValue = "false") boolean inline) {
        DocumentDtos.Download download = documents.download(id, version);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(inline
                ? ContentDisposition.inline().filename(download.filename(), StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        headers.setContentLength(download.fileSize());
        headers.set("X-Content-SHA256", download.checksumSha256());
        // Bytes from storage must never be treated as a page on this site.
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'none'; sandbox");
        return new ResponseEntity<>(new InputStreamResource(download.content()), headers, HttpStatus.OK);
    }

    /** What the caller may do with this document. */
    @GetMapping("/{id}/access-level")
    public ApiResponse<DocumentDtos.AccessLevelFor> accessLevel(@PathVariable UUID id) {
        return ApiResponse.ok(documents.effectiveLevel(id));
    }

    // -------------------------------------------------------------------- access

    @PostMapping("/{id}/access")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentDtos.AccessRow> grant(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentDtos.GrantRequest request) {
        return ApiResponse.ok(documents.grant(id, request));
    }

    @DeleteMapping("/{id}/access/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, @PathVariable UUID grantId) {
        documents.revoke(id, grantId);
    }

    // ------------------------------------------------------------ verification

    @PatchMapping("/{id}/verification")
    public ApiResponse<DocumentDtos.DocumentRow> verify(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentDtos.VerifyRequest request) {
        return ApiResponse.ok(documents.verify(id, request));
    }

    /** Takes a document out of circulation without erasing the record of it. */
    @DeleteMapping("/{id}")
    public ApiResponse<DocumentDtos.DocumentRow> delete(@PathVariable UUID id,
                                                       @RequestParam(required = false) String reason) {
        return ApiResponse.ok(documents.softDelete(id, reason));
    }

    // ----------------------------------------------------------------- reporting

    /** Everything linked to one record, for the panel beside a student or an employee. */
    @GetMapping("/related")
    public ApiResponse<List<DocumentDtos.DocumentRow>> related(
            @RequestParam String relatedType,
            @RequestParam UUID relatedId) {
        return ApiResponse.ok(documents.related(relatedType, relatedId));
    }

    @GetMapping("/expiring")
    public ApiResponse<List<DocumentDtos.DocumentRow>> expiring(
            @RequestParam(defaultValue = "90") int days) {
        return ApiResponse.ok(documents.expiringSoon(days));
    }

    @GetMapping("/overview")
    public ApiResponse<DocumentDtos.DocumentOverview> overview() {
        return ApiResponse.ok(documents.overview());
    }
}
