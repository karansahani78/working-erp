package com.educationerp.inventory;

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
import java.time.Instant;
import java.util.UUID;

/**
 * One line of the stock ledger. Append-only.
 *
 * <p>A movement is never edited or deleted. A mistake is answered with another movement that
 * says what went wrong, so the running balance can always be rebuilt from the beginning and
 * the reason for every change is still readable years later.
 *
 * <p>Direction is carried by the type rather than by the sign of the quantity, so a row can
 * never be read the wrong way round.
 */
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
public class StockMovement extends BaseEntity {

    public enum Type {
        /** Goods bought: money out, stock in. */
        PURCHASE,
        /** A return to the supplier, or a donation in. */
        RECEIPT,
        /** Handed out to a department, a class or a person. */
        ISSUE,
        /** Handed back. */
        RETURN,
        /** Arrived from another store. */
        TRANSFER_IN,
        /** Left for another store. */
        TRANSFER_OUT,
        /** A correction after a stock take. Either sign. */
        ADJUSTMENT,
        /** Broken, lost or consumed. */
        WASTAGE
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private Type type;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity = BigDecimal.ZERO;

    /** What was on the shelf after this movement, so a ledger can be read without a sum. */
    @Column(name = "balance_after", precision = 12, scale = 3)
    private BigDecimal balanceAfter;

    @Column(name = "unit_cost", precision = 12, scale = 2)
    private BigDecimal unitCost;

    /** What asked for the movement: a purchase, an issue note, a transfer. */
    @Column(name = "reference_type", length = 60)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reason", length = 400)
    private String reason;

    @Column(name = "moved_at", nullable = false)
    private Instant movedAt = Instant.now();

    @Column(name = "moved_by")
    private UUID movedBy;

    /** True when this movement adds to the shelf, false when it takes from it. */
    public boolean isInbound() {
        return switch (type) {
            case PURCHASE, RECEIPT, RETURN, TRANSFER_IN -> true;
            case ISSUE, TRANSFER_OUT, WASTAGE -> false;
            // An adjustment is whichever way the count fell.
            case ADJUSTMENT -> quantity.signum() >= 0;
        };
    }

    /** The change this movement makes to the balance: negative when stock leaves. */
    public BigDecimal signedQuantity() {
        // An adjustment is recorded as the signed difference between the count and the
        // balance, so its own sign already carries the direction. Every other movement
        // stores a magnitude, and its type decides which way that magnitude goes.
        if (type == Type.ADJUSTMENT) {
            return quantity;
        }
        return isInbound() ? quantity : quantity.negate();
    }
}
