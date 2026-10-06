package com.educationerp.inventory;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** One line of a purchase: what was ordered, how much, and how much actually arrived. */
@Entity
@Table(name = "purchase_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_purchase_items_line",
                columnNames = {"purchase_id", "item_id"}))
@Getter
@Setter
public class PurchaseItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "quantity_ordered", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityOrdered = BigDecimal.ZERO;

    /**
     * How much has actually turned up.
     *
     * <p>Kept separate from what was ordered rather than assuming the two are the same: a
     * short or over delivery is the whole point of tracking this.
     */
    @Column(name = "quantity_received", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityReceived = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "received_at")
    private Instant receivedAt;

    public boolean isFullyReceived() {
        return quantityReceived.compareTo(quantityOrdered) >= 0;
    }

    /** What is still on its way. */
    public BigDecimal outstanding() {
        BigDecimal remaining = quantityOrdered.subtract(quantityReceived);
        return remaining.signum() > 0 ? remaining : BigDecimal.ZERO;
    }

    void priceAt(BigDecimal unitCost) {
        this.unitCost = unitCost;
        this.lineTotal = this.unitCost.multiply(this.quantityOrdered);
    }
}
