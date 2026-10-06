package com.educationerp.importer;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One spreadsheet on its way into the system.
 *
 * <p>The batch is the whole story of the import: what was uploaded, how the columns were mapped,
 * what was found wrong, and what was finally written. It is kept after the fact because "the
 * fees were imported from that file on the ninth" is a question somebody will ask, and because a
 * file that produced four hundred errors should still be explainable a month later.
 *
 * <p>The rows themselves are not kept. The uploaded file is stored, and so are the errors, but a
 * copy of every rejected row would be a second, silently divergent version of the data.
 */
@Entity
@Table(name = "import_batches")
@Getter
@Setter
public class ImportBatch extends BaseEntity {

    public enum Status {
        /** Uploaded and nothing more. Nothing has been looked at yet. */
        UPLOADED,
        /** Columns mapped to fields, still not validated. */
        MAPPED,
        /** Every row read and judged. Still nothing written. */
        VALIDATED,
        /** Rows were written. */
        COMPLETED,
        /** The import was attempted and refused, or it failed part way. */
        FAILED
    }

    @Column(name = "batch_number", nullable = false, length = 40, updatable = false)
    private String batchNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, length = 30, updatable = false)
    private ImportType importType;

    @Column(name = "original_filename", nullable = false, length = 255, updatable = false)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false, length = 400, updatable = false)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.UPLOADED;

    /** Which spreadsheet column feeds which field, as column index to field name. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_mapping", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> columnMapping = Map.of();

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "invalid_rows", nullable = false)
    private int invalidRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    /** One entry per rejected row, saying which row and what was wrong with it. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "row_errors", nullable = false, columnDefinition = "jsonb")
    private List<RowError> rowErrors = List.of();

    /** What the import did, once it has been run. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "import_report", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> importReport = Map.of();

    @Column(name = "completed_at")
    private Instant completedAt;

    public void markMapped() {
        this.status = Status.MAPPED;
    }

    public void markValidated() {
        this.status = Status.VALIDATED;
    }

    public void markCompleted(Instant when) {
        this.status = Status.COMPLETED;
        this.completedAt = when;
    }

    public void markFailed() {
        this.status = Status.FAILED;
    }

    /**
     * One thing wrong with one row.
     *
     * @param row the spreadsheet row, counting the header as row 1
     * @param field the field it concerns, or a column name for a structural problem
     * @param message what is wrong, in words the person can act on
     * @param value what they put there
     */
    public record RowError(int row, String field, String message, String value) {
    }

    /** The kinds of record the blueprint lists as importable. */
    public enum ImportType {
        STUDENTS,
        GUARDIANS,
        EMPLOYEES,
        COURSES,
        FEES,
        INVENTORY,
        BOOKS
    }
}