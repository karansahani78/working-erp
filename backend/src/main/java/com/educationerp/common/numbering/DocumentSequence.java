package com.educationerp.common.numbering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Per-kind, per-period counter behind {@link SequenceNumberGenerator}. A single row per
 * (kind, period) is incremented under a pessimistic write lock, so two concurrent requests
 * can never be handed the same number.
 *
 * <p>This lives in the shared package because numbering is not a finance concern: receipts and
 * journals are only two of the documents this institution numbers by hand.
 */
@Entity
@Table(name = "document_sequences")
public class DocumentSequence {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_kind", nullable = false, length = 30)
    private Kind documentKind;

    @Column(name = "period", nullable = false, length = 20)
    private String period;

    @Column(name = "current_value", nullable = false)
    private long currentValue;

    public enum Kind {
        INVOICE, RECEIPT, JOURNAL,
        /** A library member's card number. */
        MEMBER,
        /** A book copy's barcode. */
        BARCODE,
        /** A purchase order. */
        PURCHASE,
        /** A stock issue note. */
        STOCK_ISSUE,
        /** A stock transfer note. */
        TRANSFER,
        /** An asset tag. */
        ASSET,
        /** A managed document's reference number. */
        DOCUMENT,
        /** A spreadsheet import's batch number. */
        IMPORT,
        /** An employee's code, which the staff screen requires and imports must therefore issue. */
        EMPLOYEE
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Kind getDocumentKind() {
        return documentKind;
    }

    public void setDocumentKind(Kind documentKind) {
        this.documentKind = documentKind;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public long getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(long currentValue) {
        this.currentValue = currentValue;
    }
}
