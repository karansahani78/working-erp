package com.educationerp.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * An order placed with a supplier.
 *
 * <p>A purchase is a promise, not a delivery. Stock only moves when goods are received, and
 * each line records how much of what was ordered actually turned up, so a short delivery is
 * visible rather than quietly rounded up.
 */
@Entity
@Table(name = "purchases",
        uniqueConstraints = @UniqueConstraint(name = "uk_purchases_number", columnNames = "purchase_number"))
@Getter
@Setter
public class Purchase extends com.educationerp.common.persistence.BaseEntity {

    public enum Status {
        DRAFT,
        /** Sent to the supplier, nothing expected yet. */
        ORDERED,
        /** Some of the lines have arrived. */
        PARTIAL,
        /** Everything ordered has arrived. */
        RECEIVED,
        CANCELLED
    }

    @Column(name = "purchase_number", nullable = false, length = 40)
    private String purchaseNumber;

    @Column(name = "supplier_name", nullable = false, length = 200)
    private String supplierName;

    @Column(name = "supplier_contact", length = 120)
    private String supplierContact;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "ordered_on", nullable = false)
    private LocalDate orderedOn = LocalDate.now();

    @Column(name = "expected_on")
    private LocalDate expectedOn;

    @Column(name = "received_on")
    private LocalDate receivedOn;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** Freight and anything else that makes the goods cost more than their price. */
    @Column(name = "other_costs", nullable = false, precision = 14, scale = 2)
    private BigDecimal otherCosts = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "purchase", cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true)
    private List<PurchaseItem> items = new ArrayList<>();

    /** What the order is worth once tax and other costs are added. */
    public void recalculateTotals() {
        subtotal = items.stream()
                .map(PurchaseItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalAmount = subtotal.add(zero(taxAmount)).add(zero(otherCosts));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
