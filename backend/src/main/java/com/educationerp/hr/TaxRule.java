package com.educationerp.hr;

import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.common.persistence.BaseEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * A versioned set of tax brackets. The blueprint forbids hardcoded rates, so the brackets
 * live in the database with effectivity dates: a rate can be changed for future periods
 * while last month's payslips still explain the tax they were charged.
 */
@Entity
@Table(name = "tax_rules",
        uniqueConstraints = @UniqueConstraint(name = "uk_tax_rules_code", columnNames = "code"))
@Getter
@Setter
public class TaxRule extends BaseEntity {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    /** Progressive brackets: {@code [{"upTo": 500000, "rate": 1}, {"upTo": null, "rate": 15}]}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "brackets", nullable = false, columnDefinition = "jsonb")
    private String brackets;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    public enum Status {
        ACTIVE, RETIRED
    }

    /** One band of the scale: taxable income up to {@code upTo} is taxed at {@code rate}%. */
    public record Bracket(BigDecimal upTo, BigDecimal rate) {
    }

    public boolean covers(LocalDate on) {
        return status == Status.ACTIVE
                && !on.isBefore(effectiveFrom)
                && (effectiveTo == null || !on.isAfter(effectiveTo));
    }

    public List<Bracket> brackets(ObjectMapper mapper) {
        try {
            return mapper.readValue(brackets, new TypeReference<List<Bracket>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Tax rule " + code + " has unreadable brackets.");
        }
    }

    /**
     * Progressive tax over an annual taxable income. Each band only ever taxes the slice of
     * income falling inside it, which is the whole point of a bracket table.
     */
    public BigDecimal taxOn(BigDecimal annualTaxable, ObjectMapper mapper) {
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal lowerBound = BigDecimal.ZERO;
        for (Bracket band : brackets(mapper)) {
            BigDecimal slice = sliceOf(annualTaxable, lowerBound, band.upTo());
            if (slice.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            tax = tax.add(slice.multiply(band.rate())
                    .divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP));
            if (band.upTo() == null || annualTaxable.compareTo(band.upTo()) <= 0) {
                break;
            }
            lowerBound = band.upTo();
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    /** The part of {@code income} that sits inside the band ending at {@code upperBound}. */
    private BigDecimal sliceOf(BigDecimal income, BigDecimal lowerBound, BigDecimal upperBound) {
        BigDecimal slice = income.subtract(lowerBound);
        if (slice.compareTo(BigDecimal.ZERO) < 0) {
            slice = BigDecimal.ZERO;
        }
        if (upperBound != null) {
            BigDecimal room = upperBound.subtract(lowerBound);
            if (slice.compareTo(room) > 0) {
                slice = room;
            }
        }
        return slice;
    }
}