package com.educationerp.student;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Institution-configurable student number format. The blueprint default renders
 * {@code BCA-2026-00124} using the pattern {@code {PREFIX}-{YEAR}-{SEQ:5}}.
 */
@Entity
@Table(name = "student_number_settings")
public class StudentNumberSetting extends BaseEntity {

    @Column(name = "format", nullable = false, length = 60)
    private String format = "{PREFIX}-{YEAR}-{SEQ:5}";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}