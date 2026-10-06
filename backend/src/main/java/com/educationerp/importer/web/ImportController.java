package com.educationerp.importer.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import com.educationerp.importer.ImportBatch;
import com.educationerp.importer.ImportDtos;
import com.educationerp.importer.ImportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * The import endpoint.
 *
 * <p>Each stage is its own call, so a person can upload, look, change their mind about the
 * mapping, validate, and only then confirm. Nothing about that sequence is enforced by the
 * frontend: the service refuses to confirm a file that has not been validated, whichever screen
 * the request came from.
 */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportService imports;

    public ImportController(ImportService imports) {
        this.imports = imports;
    }

    /** What each import type needs, for the screen that helps somebody prepare a file. */
    @GetMapping("/types")
    public ApiResponse<List<ImportDtos.ImportTypeHelp>> types() {
        return ApiResponse.ok(imports.help());
    }

    @GetMapping
    public ApiResponse<PageResponse<ImportDtos.BatchSummary>> list(
            @RequestParam(required = false) ImportBatch.ImportType type,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(imports.list(type, pageable));
    }

    /**
     * Upload a file. This stores it and reads its headers; it writes no school records.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportDtos.UploadResult> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam ImportBatch.ImportType type) {
        return ApiResponse.ok(imports.upload(file, type));
    }

    @GetMapping("/{id}")
    public ApiResponse<ImportDtos.BatchSummary> detail(@PathVariable UUID id) {
        return ApiResponse.ok(imports.detail(id));
    }

    /** Confirm which spreadsheet column feeds which field. */
    @PutMapping("/{id}/mapping")
    public ApiResponse<ImportDtos.BatchSummary> map(
            @PathVariable UUID id, @Valid @RequestBody ImportDtos.MapRequest request) {
        return ApiResponse.ok(imports.map(id, request.mapping()));
    }

    /** Read every row. Writes errors to the batch and nothing else. */
    @PostMapping("/{id}/validate")
    public ApiResponse<ImportDtos.ValidationResult> validate(@PathVariable UUID id) {
        return ApiResponse.ok(imports.validate(id));
    }

    /** Write the rows. Only possible after a validation has passed. */
    @PostMapping("/{id}/confirm")
    public ApiResponse<ImportDtos.ImportReport> confirm(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean skipInvalidRows) {
        return ApiResponse.ok(imports.confirm(id, skipInvalidRows));
    }

    @GetMapping("/{id}/errors")
    public ApiResponse<List<ImportBatch.RowError>> errors(@PathVariable UUID id) {
        return ApiResponse.ok(imports.errors(id));
    }

    @GetMapping("/{id}/duplicates")
    public ApiResponse<List<ImportDtos.Duplicate>> duplicates(@PathVariable UUID id) {
        return ApiResponse.ok(imports.duplicateRows(id));
    }
}