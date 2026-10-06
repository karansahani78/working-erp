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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Moving stock from one store to another.
 *
 * <p>Stock leaves when the transfer is dispatched and arrives when it is completed, so goods in
 * transit are visible rather than having quietly appeared at the far end.
 */
@Entity
@Table(name = "stock_transfers",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_transfers_number", columnNames = "transfer_number"))
@Getter
@Setter
public class StockTransfer extends BaseEntity {

    public enum Status {
        REQUESTED,
        /** Gone from the sending store, not yet at the receiving one. */
        DISPATCHED,
        COMPLETED,
        CANCELLED
    }

    @Column(name = "transfer_number", nullable = false, length = 40)
    private String transferNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_store_id", nullable = false)
    private Store fromStore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_store_id", nullable = false)
    private Store toStore;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.REQUESTED;

    @Column(name = "reason", length = 400)
    private String reason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "received_at")
    private Instant receivedAt;
}
