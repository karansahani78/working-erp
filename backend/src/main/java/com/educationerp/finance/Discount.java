package com.educationerp.finance;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** A reusable concession rule: a percentage or a flat amount off the assessed fee. */
@Entity
@Table(name = "discounts")
public class Discount extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private ValueType discountType = ValueType.PERCENTAGE;

    @Column(name = "value", nullable = false, precision = 14, scale = 2)
    private BigDecimal value;

    @Column(name = "max_amount", precision = 14, scale = 2)
    private BigDecimal maxAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_to", nullable = false, length = 30)
    private ApplicableTo applicableTo = ApplicableTo.ALL;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    public enum ValueType {
        PERCENTAGE, FIXED
    }

    public enum ApplicableTo {
        ALL, TUITION, TRANSPORT, HOSTEL
    }

    public enum Status {
        ACTIVE, INACTIVE, EXPIRED
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public ValueType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(ValueType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public ApplicableTo getApplicableTo() {
        return applicableTo;
    }

    public void setApplicableTo(ApplicableTo applicableTo) {
        this.applicableTo = applicableTo;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
