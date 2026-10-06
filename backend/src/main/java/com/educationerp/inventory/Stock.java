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

/**
 * How much of an item is in one store.
 *
 * <p>This row is the current answer and nothing else. Every change to it is explained by a row
 * in {@link StockMovement}, which is what makes the figure arguable: if the shelf and the
 * screen disagree, the movements say which one is lying.
 */
@Entity
@Table(name = "stock",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_item_store", columnNames = {"item_id", "store_id"}))
@Getter
@Setter
public class Stock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity = BigDecimal.ZERO;

    /** A running average, kept so a stock take can be valued without re-reading history. */
    @Column(name = "average_cost", precision = 12, scale = 2)
    private BigDecimal averageCost;

    @Column(name = "last_movement_at")
    private Instant lastMovementAt;
}
