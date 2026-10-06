package com.educationerp.importer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A row that matched a record already in the system.
 *
 * <p>Both values are kept: what is already there and what the spreadsheet proposed. The person
 * importing decides which is right, and they cannot do that from "duplicate" alone.
 */
@Entity
@Table(name = "import_batch_duplicates")
@Getter
@Setter
public class ImportBatchDuplicate {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ImportBatch batch;

    /** The spreadsheet row, counting the header as row 1. */
    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "field_name", nullable = false, length = 80)
    private String fieldName;

    @Column(name = "existing_value", nullable = false, length = 255)
    private String existingValue;

    @Column(name = "incoming_value", nullable = false, length = 255)
    private String incomingValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ImportBatchDuplicate() {
    }

    public ImportBatchDuplicate(ImportBatch batch, int rowNumber, String fieldName,
                                String existingValue, String incomingValue) {
        this.batch = batch;
        this.rowNumber = rowNumber;
        this.fieldName = fieldName;
        this.existingValue = existingValue;
        this.incomingValue = incomingValue;
    }
}
