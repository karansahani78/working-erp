package com.educationerp.finance;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** One line of a fee structure: tuition, transport, library, examination and so on. */
@Entity
@Table(name = "fee_components")
public class FeeComponent extends BaseEntity {

    @Column(name = "fee_structure_id", nullable = false)
    private UUID feeStructureId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 30)
    private ComponentType componentType = ComponentType.TUITION;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    public enum ComponentType {
        TUITION, ADMISSION, TRANSPORT, LIBRARY, EXAMINATION, BOARD, HOSTEL, LAB, OTHER
    }

    public UUID getFeeStructureId() {
        return feeStructureId;
    }

    public void setFeeStructureId(UUID feeStructureId) {
        this.feeStructureId = feeStructureId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ComponentType getComponentType() {
        return componentType;
    }

    public void setComponentType(ComponentType componentType) {
        this.componentType = componentType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }
}
