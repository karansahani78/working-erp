package com.educationerp.importer;

import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.api.PageResponse;
import com.educationerp.common.error.AppException;
import com.educationerp.common.numbering.DocumentSequence;
import com.educationerp.common.numbering.SequenceNumberGenerator;
import com.educationerp.document.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The import workflow: upload, map, validate, preview, confirm, report.
 *
 * <p>The stages are kept apart on purpose. An upload is stored and nothing else happens; a
 * validation reads every cell and writes no school record; only a confirmation writes, and only
 * after a validation has passed. The blueprint is explicit that an unvalidated file is never
 * imported, and the only reliable way to guarantee that is to make the step impossible to skip
 * rather than to ask for it politely.
 *
 * <p>Confirming runs in the caller's transaction, so a file either lands whole or not at all:
 * half an import is a state nobody can reason about.
 */
@Service
@RequiredArgsConstructor
public class ImportService {

    /** A preview is for looking at, not for reading; the counts come from the whole file. */
    private static final int PREVIEW_ROWS = 20;

    private final ImportBatchRepository batches;
    private final ImportBatchDuplicateRepository duplicates;
    private final ImportDefinitions definitions;
    private final ImportValidator validator;
    private final ImportWriters writers;
    private final SpreadsheetReader reader;
    private final ObjectStorage storage;
    private final SequenceNumberGenerator numbers;
    private final AuthorizationChecker auth;
    private final AuditService audit;

    // ------------------------------------------------------------------ upload

    /**
     * Take the file and stop there.
     *
     * <p>The headers are read so the caller can be told what the system saw, and the columns are
     * matched against the fields so a sensibly-headed file needs no mapping at all. Nothing is
     * written to any school record at this point.
     */
    @Transactional
    public ImportDtos.UploadResult upload(MultipartFile file, ImportBatch.ImportType type) {
        auth.requirePermission("IMPORT_RUN");
        auth.requirePermission(definitions.viewPermission(type));

        SpreadsheetReader.SheetData sheet = reader.read(file);
        ImportField[] fields = definitions.fields(type);

        ImportBatch batch = new ImportBatch();
        batch.setBatchNumber(numbers.next(DocumentSequence.Kind.IMPORT));
        batch.setImportType(type);
        batch.setOriginalFilename(file.getOriginalFilename() == null ? "import"
                : file.getOriginalFilename());
        batch.setStorageKey(store(batch.getBatchNumber(), file));
        batch.setTotalRows(sheet.rows().size());
        batch.setColumnMapping(suggest(sheet, fields));
        batches.save(batch);

        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .module("IMPORT")
                .entityType("ImportBatch")
                .entityId(batch.getId().toString())
                .entityLabel(batch.getBatchNumber())
                .summary("Uploaded " + batch.getOriginalFilename() + " as " + type
                        + " (" + sheet.rows().size() + " rows)")
                .succeeded(true)
                .build());

        return new ImportDtos.UploadResult(summary(batch), sheet.headers(), help(fields),
                validator.unmapped(fields, sheet, batch.getColumnMapping()).stream()
                        .map(ImportDtos.FieldHelp::from).toList());
    }

    /** Store the file outside the database, under a key this service invents. */
    private String store(String batchNumber, MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String key = "imports/" + batchNumber + "/" + UUID.randomUUID() + "-"
                + filename.replaceAll("[^A-Za-z0-9._-]", "_");
        try (InputStream in = file.getInputStream()) {
            storage.put(key, in);
        } catch (IOException e) {
            throw AppException.rule("The file could not be stored: " + e.getMessage());
        }
        return key;
    }

    // ------------------------------------------------------------------ mapping

    /** Confirm which spreadsheet column feeds which field. */
    @Transactional
    public ImportDtos.BatchSummary map(UUID batchId, Map<String, String> mapping) {
        ImportBatch batch = require(batchId);
        ImportField[] fields = definitions.fields(batch.getImportType());
        SpreadsheetReader.SheetData sheet = sheet(batch);

        Map<String, String> cleaned = new LinkedHashMap<>();
        for (ImportField field : fields) {
            String column = mapping.get(field.name());
            if (column == null || column.isBlank()) {
                continue;
            }
            if (!sheet.headers().contains(column)) {
                throw AppException.rule("\"" + column + "\" is not a column in that file.");
            }
            cleaned.put(field.name(), column);
        }
        // A mapping that leaves a required field with nothing to read is refused now rather than
        // producing four hundred "required" errors later.
        validator.requireMapped(fields, sheet, cleaned);

        batch.setColumnMapping(cleaned);
        batch.markMapped();
        batches.save(batch);
        return summary(batch);
    }

    // ------------------------------------------------------------------ validate

    /**
     * Read every row and say what is wrong with it.
     *
     * <p>This writes no school records. It writes the errors to the batch, which is the point: the
     * person importing can see the whole picture, fix the file, and upload it again.
     */
    @Transactional
    public ImportDtos.ValidationResult validate(UUID batchId) {
        ImportBatch batch = require(batchId);
        ImportField[] fields = definitions.fields(batch.getImportType());
        SpreadsheetReader.SheetData sheet = sheet(batch);
        validator.requireMapped(fields, sheet, batch.getColumnMapping());

        List<ImportValidator.ValidatedRow> rows =
                validator.validate(fields, sheet, batch.getColumnMapping());

        List<ImportBatch.RowError> errors = rows.stream()
                .flatMap(row -> row.errors().stream()).toList();

        // Re-validation replaces the previous verdict rather than adding to it, so a file that is
        // validated twice does not appear to have twice the problems.
        duplicates.deleteByBatchId(batchId);
        List<ImportWriters.Match> found = writers.duplicates(batch.getImportType(), rows);
        duplicates.flush();
        duplicates.saveAll(found.stream()
                .map(match -> new ImportBatchDuplicate(batch, match.row(), match.field(),
                        match.existing(), match.incoming()))
                .toList());

        batch.setRowErrors(errors);
        batch.setValidRows((int) rows.stream().filter(ImportValidator.ValidatedRow::ok).count());
        batch.setInvalidRows((int) rows.stream().filter(row -> !row.ok()).count());
        batch.markValidated();
        batches.save(batch);

        audit.record(AuditEvent.builder()
                .action(AuditAction.READ)
                .module("IMPORT")
                .entityType("ImportBatch")
                .entityId(batchId.toString())
                .entityLabel(batch.getBatchNumber())
                .summary("Validated " + batch.getTotalRows() + " rows: " + batch.getValidRows()
                        + " usable, " + batch.getInvalidRows() + " with problems, "
                        + found.size() + " already in the system")
                .succeeded(true)
                .build());

        return new ImportDtos.ValidationResult(summary(batch), errors,
                rows.stream().filter(ImportValidator.ValidatedRow::ok).limit(PREVIEW_ROWS).toList(),
                found.stream()
                        .map(match -> new ImportDtos.Duplicate(match.row(), match.field(),
                                match.existing(), match.incoming()))
                        .toList());
    }

    // ------------------------------------------------------------------ confirm

    /**
     * Write the rows.
     *
     * <p>Refused while any row has a problem, unless the caller says to skip the bad ones, in
     * which case the skipped rows are named in the report. Either way the person confirming
     * decides, and the decision is written down.
     */
    @Transactional
    public ImportDtos.ImportReport confirm(UUID batchId, boolean skipInvalidRows) {
        ImportBatch batch = require(batchId);
        auth.requirePermission(definitions.confirmPermission(batch.getImportType()));

        if (batch.getStatus() != ImportBatch.Status.VALIDATED) {
            throw AppException.rule("This file has not been validated yet. Validate it first.");
        }
        if (batch.getInvalidRows() > 0 && !skipInvalidRows) {
            throw AppException.rule(batch.getInvalidRows() + " rows have problems. Fix them and "
                    + "upload the file again, or confirm again to import the "
                    + batch.getValidRows() + " rows that are fine.");
        }

        ImportField[] fields = definitions.fields(batch.getImportType());
        List<ImportValidator.ValidatedRow> rows =
                validator.validate(fields, sheet(batch), batch.getColumnMapping());
        // Only rows that validated are ever written. skipInvalidRows decides whether the caller is
        // allowed to proceed at all while some rows are broken; it never widens the set of rows
        // written, because a broken row that is written anyway is exactly what validation exists to
        // prevent.
        List<ImportValidator.ValidatedRow> usable = rows.stream()
                .filter(ImportValidator.ValidatedRow::ok).toList();

        ImportWriters.Outcome outcome = writers.write(batch.getImportType(), usable);
        List<String> notes = new ArrayList<>(outcome.notes());
        rows.stream().filter(row -> !row.ok()).forEach(row ->
                notes.add("Row " + row.rowNumber() + ": skipped, " + row.errors().get(0).message()));

        ImportDtos.ImportReport report = new ImportDtos.ImportReport(rows.size(),
                outcome.imported(), rows.size() - outcome.imported(), notes);

        batch.setImportedRows(outcome.imported());
        batch.setImportReport(report.detail());
        batch.markCompleted(Instant.now());
        batches.save(batch);

        audit.record(AuditEvent.builder()
                .action(AuditAction.IMPORT)
                .module("IMPORT")
                .entityType("ImportBatch")
                .entityId(batchId.toString())
                .entityLabel(batch.getBatchNumber())
                .summary("Imported " + outcome.imported() + " of " + rows.size() + " rows as "
                        + batch.getImportType() + (skipInvalidRows ? ", skipping the rest" : ""))
                .succeeded(true)
                .build());
        return report;
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public PageResponse<ImportDtos.BatchSummary> list(ImportBatch.ImportType type,
                                                      Pageable pageable) {
        if (type == null) {
            auth.requirePermission("IMPORT_READ");
            return PageResponse.from(batches.findAllByOrderByCreatedAtDesc(pageable), this::summary);
        }
        auth.requirePermission(definitions.viewPermission(type));
        return PageResponse.from(
                batches.findByImportTypeOrderByCreatedAtDesc(type, pageable), this::summary);
    }

    @Transactional(readOnly = true)
    public ImportDtos.BatchSummary detail(UUID batchId) {
        return summary(require(batchId));
    }

    @Transactional(readOnly = true)
    public List<ImportBatch.RowError> errors(UUID batchId) {
        return require(batchId).getRowErrors();
    }

    @Transactional(readOnly = true)
    public List<ImportDtos.Duplicate> duplicateRows(UUID batchId) {
        require(batchId);
        return duplicates.findByBatchIdOrderByRowNumber(batchId).stream()
                .map(d -> new ImportDtos.Duplicate(d.getRowNumber(), d.getFieldName(),
                        d.getExistingValue(), d.getIncomingValue()))
                .toList();
    }

    /** What each import type needs, for the screen that explains the file to prepare. */
    public List<ImportDtos.ImportTypeHelp> help() {
        auth.requirePermission("IMPORT_READ");
        List<ImportDtos.ImportTypeHelp> help = new ArrayList<>();
        for (ImportBatch.ImportType type : ImportBatch.ImportType.values()) {
            if (!auth.hasPermission(definitions.viewPermission(type))) {
                continue;
            }
            List<ImportField> fields = List.of(definitions.fields(type));
            help.add(new ImportDtos.ImportTypeHelp(type,
                    fields.stream().map(ImportField::label).toList(),
                    fields.stream().map(ImportDtos.FieldHelp::from).toList(),
                    definitions.confirmPermission(type)));
        }
        return help;
    }

    // ------------------------------------------------------------------ helpers

    private ImportBatch require(UUID batchId) {
        ImportBatch batch = batches.findById(batchId)
                .orElseThrow(() -> AppException.notFound("Import batch"));
        auth.requirePermission(definitions.viewPermission(batch.getImportType()));
        return batch;
    }

    /**
     * The uploaded file, read again from storage.
     *
     * <p>Always the original rather than a copy kept in the session, so what gets validated and
     * then written is exactly what was uploaded.
     */
    private SpreadsheetReader.SheetData sheet(ImportBatch batch) {
        try (InputStream in = storage.open(batch.getStorageKey())) {
            return reader.read(in, batch.getOriginalFilename());
        } catch (IOException e) {
            throw AppException.rule("The uploaded file could not be read again: " + e.getMessage());
        }
    }

    /** Match the headers against the fields, so a well-headed file needs no mapping. */
    private Map<String, String> suggest(SpreadsheetReader.SheetData sheet, ImportField[] fields) {
        Map<String, Integer> resolved = validator.resolveColumns(fields, sheet, Map.of());
        Map<String, String> suggested = new LinkedHashMap<>();
        resolved.forEach((field, index) -> suggested.put(field, sheet.headers().get(index)));
        return suggested;
    }

    private List<ImportDtos.FieldHelp> help(ImportField[] fields) {
        return java.util.Arrays.stream(fields).map(ImportDtos.FieldHelp::from).toList();
    }

    private ImportDtos.BatchSummary summary(ImportBatch batch) {
        return new ImportDtos.BatchSummary(batch.getId(), batch.getBatchNumber(),
                batch.getImportType(), batch.getStatus(), batch.getOriginalFilename(),
                batch.getTotalRows(), batch.getValidRows(), batch.getInvalidRows(),
                batch.getImportedRows(), batch.getCreatedAt(), batch.getCompletedAt(),
                Map.copyOf(batch.getColumnMapping()));
    }
}