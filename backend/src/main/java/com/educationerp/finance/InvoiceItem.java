package com.educationerp.finance;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** A single charge line on an invoice. */
@Entity
@Table(name = "invoice_items")
public class InvoiceItem extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 30)
    private FeeComponent.ComponentType componentType = FeeComponent.ComponentType.TUITION;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /**
     * True when the line reduces the invoice total (a discount or scholarship) rather than
     * charging for something. The amount stays positive either way; the flag carries the sign.
     */
    @Column(name = "is_concession", nullable = false)
    private boolean concession;

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public FeeComponent.ComponentType getComponentType() {
        return componentType;
    }

    public void setComponentType(FeeComponent.ComponentType componentType) {
        this.componentType = componentType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isConcession() {
        return concession;
    }

    public void setConcession(boolean concession) {
        this.concession = concession;
    }
}
