package com.educationerp.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Gap-tolerant per-prefix/year counter backing {@link StudentNumberGenerator}. A single
 * row per (prefix, period) pair is incremented under a row lock, so concurrent admission
 * approvals cannot mint the same number twice.
 */
@Entity
@Table(name = "student_number_sequences")
public class StudentNumberSequence {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "prefix", nullable = false, length = 20)
    private String prefix;

    @Column(name = "period", nullable = false, length = 10)
    private String period;

    @Column(name = "current_value", nullable = false)
    private long currentValue;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public long getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(long currentValue) {
        this.currentValue = currentValue;
    }
}