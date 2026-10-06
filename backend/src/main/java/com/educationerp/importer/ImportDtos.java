package com.educationerp.importer;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the import screens send and receive.
 *
 * <p>The four stages each have their own result because a person doing this needs to be told where
 * they are and what to do next: after an upload, which columns were matched; after a validation,
 * what is wrong and a preview of what will be written; after a confirmation, what actually
 * happened.
 */
public final class ImportDtos {

    private ImportDtos() {
    }

    /** Where a batch has got to, in the terms the list and detail screens show. */
    public record BatchSummary(UUID id, String batchNumber, ImportBatch.ImportType importType,
                               ImportBatch.Status status, String originalFilename, int totalRows,
                               int validRows, int invalidRows, int importedRows, Instant createdAt,
                               Instant completedAt,
                               Map<String, String> columnMapping) {
    }

    /**
     * The answer to an upload: what the system saw, what it wants, and what it could not match.
     *
     * @param headers the columns as the file names them
     * @param fields what this import needs
     * @param unmapped the fields with no matching column, so the mapping screen can be opened
     *                straight away rather than after a confusing failure
     */
    public record UploadResult(BatchSummary batch, List<String> headers,
                               List<FieldHelp> fields, List<FieldHelp> unmapped) {
    }

    /**
     * The answer to a validation: what is wrong, and what will be written if it is confirmed.
     *
     * @param preview the first rows that would be written, so the mapping can be checked without
     *                reading the file again
     * @param errors one entry per rejected row
     * @param duplicates rows that match something already in the system
     */
    public record ValidationResult(BatchSummary batch, List<ImportBatch.RowError> errors,
                                   List<ImportValidator.ValidatedRow> preview,
                                   List<Duplicate> duplicates) {
    }

    /** A row that matched an existing record, with both values so the decision can be made. */
    public record Duplicate(int row, String field, String existing, String incoming) {
    }

    /** The answer to a confirmation. */
    public record ImportReport(int total, int imported, int skipped, List<String> notes) {

        /** Kept for the batch row, so the outcome survives the response. */
        public Map<String, Object> detail() {
            return Map.of("total", total, "imported", imported, "skipped", skipped,
                    "notes", notes == null ? List.of() : notes);
        }
    }

    /** One field an import wants, described without exposing how it is stored. */
    public record FieldHelp(String name, String label, boolean required, String kind,
                            List<String> allowed) {

        static FieldHelp from(ImportField field) {
            // The name is what a mapping is keyed by, so it has to travel with the label.
            return new FieldHelp(field.name(), field.label(), field.required(),
                    field.kind().name().toLowerCase(java.util.Locale.ROOT), field.allowed());
        }
    }

    /** What one import type needs, for the screen that helps somebody prepare a file. */
    public record ImportTypeHelp(ImportBatch.ImportType type, List<String> templateHeaders,
                                 List<FieldHelp> fields, String confirmPermission) {
    }

    /** Confirming, with a decision about the rows that have problems. */
    public record ConfirmRequest(boolean skipInvalidRows) {
    }

    /** The columns a person confirmed, field name to the column they chose. */
    public record MapRequest(@NotNull Map<String, String> mapping) {
    }
}