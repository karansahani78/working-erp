package com.educationerp.common.numbering;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Server-issued numbers for everything the institution numbers: invoices, receipts, journals,
 * library cards, book barcodes, purchase orders, stock notes, asset tags and documents.
 *
 * <p>Clients never supply these numbers, and each kind is numbered from its own counter under a
 * row lock, so two concurrent requests cannot be handed the same number. A dated series
 * restarts each calendar year, which keeps numbers short and easy to quote over a counter; an
 * undated one runs for the life of the institution so a membership card number never changes.
 */
@Component
@RequiredArgsConstructor
public class SequenceNumberGenerator {

    private static final int WIDTH = 5;
    private static final int MAX_ATTEMPTS = 5;

    private final DocumentSequenceRepository sequences;

    /** Sequence for a period derived from the document date, so back-dated entries are numbered in their own year. */
    @Transactional(propagation = Propagation.REQUIRED)
    public String next(DocumentSequence.Kind kind, LocalDate onDate) {
        return next(kind, period(onDate));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public String next(DocumentSequence.Kind kind) {
        return next(kind, period(LocalDate.now()));
    }

    private String next(DocumentSequence.Kind kind, String period) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            DocumentSequence sequence = sequences
                    .lockByKindAndPeriod(kind, period)
                    .orElseGet(() -> create(kind, period));
            sequence.setCurrentValue(sequence.getCurrentValue() + 1);
            sequences.save(sequence);
            return format(kind, period, sequence.getCurrentValue());
        }
        throw new IllegalStateException("Could not allocate a " + kind + " number");
    }

    private DocumentSequence create(DocumentSequence.Kind kind, String period) {
        DocumentSequence sequence = new DocumentSequence();
        sequence.setId(UUID.randomUUID());
        sequence.setDocumentKind(kind);
        sequence.setPeriod(period);
        sequence.setCurrentValue(0L);
        return sequence;
    }

    String format(DocumentSequence.Kind kind, String period, long value) {
        return switch (kind) {
            case INVOICE -> "INV-" + period + "-" + pad(value);
            case RECEIPT -> "RCP-" + period + "-" + pad(value);
            case JOURNAL -> "JRN-" + period + "-" + pad(value);
            case MEMBER -> "MBR-" + pad(value);
            case BARCODE -> "BC-" + pad(value);
            case PURCHASE -> "PUR-" + period + "-" + pad(value);
            case STOCK_ISSUE -> "SI-" + period + "-" + pad(value);
            case TRANSFER -> "TRF-" + period + "-" + pad(value);
            case ASSET -> "AST-" + pad(value);
            case DOCUMENT -> "DOC-" + period + "-" + pad(value);
            case IMPORT -> "IMP-" + period + "-" + pad(value);
            case EMPLOYEE -> "EMP-" + period + "-" + pad(value);
        };
    }

    private String pad(long value) {
        String digits = Long.toString(value);
        return digits.length() >= WIDTH ? digits : "0".repeat(WIDTH - digits.length()) + digits;
    }

    private String period(LocalDate date) {
        return Integer.toString(date.getYear());
    }
}
