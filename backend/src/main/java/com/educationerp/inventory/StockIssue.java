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
import java.util.UUID;

/**
 * A note handing stock to somebody.
 *
 * <p>The recipient is named by kind and by a name, not by a reference to whichever module
 * needed the thing: departments, classes, courses and individuals all consume stock, and none
 * of them should have to know the store exists. The identifier is kept alongside the name so
 * a report can still be grouped, but the name is what a reader sees.
 */
@Entity
@Table(name = "stock_issues",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_issues_number", columnNames = "issue_number"))
@Getter
@Setter
public class StockIssue extends BaseEntity {

    public enum Status { ISSUED, RETURNED, CANCELLED }

    /** What kind of thing is taking the stock. */
    public enum RecipientType {
        DEPARTMENT, CLASS, COURSE, STUDENT, EMPLOYEE, USER, EXTERNAL
    }

    @Column(name = "issue_number", nullable = false, length = 40)
    private String issueNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "issued_to_type", nullable = false, length = 40)
    private RecipientType issuedToType = RecipientType.DEPARTMENT;

    @Column(name = "issued_to_id")
    private UUID issuedToId;

    @Column(name = "issued_to_name", length = 200)
    private String issuedToName;

    @Column(name = "reason", length = 400)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ISSUED;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "returned_at")
    private Instant returnedAt;
}
