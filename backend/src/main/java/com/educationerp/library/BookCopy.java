package com.educationerp.library;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One physical copy of a book, and the thing that is actually lent.
 *
 * <p>The barcode on the copy is the only identifier a member at the counter needs, so it is
 * the one identifier on this row that can never change.
 */
@Entity
@Table(name = "book_copies")
@Getter
@Setter
public class BookCopy extends BaseEntity {

    public enum AcquisitionType { PURCHASE, DONATION, EXCHANGE }

    public enum ConditionStatus { NEW, GOOD, WORN, DAMAGED }

    public enum Status {
        AVAILABLE,
        ISSUED,
        RESERVED,
        IN_REPAIR,
        WITHDRAWN,
        LOST
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "barcode", nullable = false, length = 60)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @Column(name = "acquisition_type", nullable = false, length = 20)
    private AcquisitionType acquisitionType = AcquisitionType.PURCHASE;

    @Column(name = "acquired_on")
    private LocalDate acquiredOn;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_status", nullable = false, length = 20)
    private ConditionStatus conditionStatus = ConditionStatus.GOOD;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.AVAILABLE;

    @Column(name = "notes", length = 500)
    private String notes;

    /** True when this copy is on the shelf and can be lent right now. */
    public boolean isLendable() {
        return status == Status.AVAILABLE
                && (book == null || !book.isReference());
    }
}
