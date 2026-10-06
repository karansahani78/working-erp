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

/**
 * Something the institution holds and spends.
 *
 * <p>The reorder level lives here rather than on a stock row because it is a property of the
 * item: every store should warn at the same level, and a warning that changes when stock moves
 * between stores would be noise.
 */
@Entity
@Table(name = "items",
        uniqueConstraints = @UniqueConstraint(name = "uk_items_code", columnNames = "code"))
@Getter
@Setter
public class Item extends BaseEntity {

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ItemCategory category;

    /** PCS, BOX, REAM, LITRE and so on. Free text, because every school counts differently. */
    @Column(name = "unit", nullable = false, length = 30)
    private String unit = "PCS";

    @Column(name = "reorder_level", nullable = false, precision = 12, scale = 3)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    /** How much to order when the level is crossed, rather than ordering one at a time. */
    @Column(name = "reorder_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal reorderQuantity = BigDecimal.ZERO;

    @Column(name = "track_batch", nullable = false)
    private boolean batchTracked = false;

    @Column(name = "track_expiry", nullable = false)
    private boolean expiryTracked = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** True when the item should be on a purchase list at this quantity. */
    public boolean needsReordering(BigDecimal onHand) {
        return onHand != null && onHand.compareTo(reorderLevel) <= 0;
    }
}
