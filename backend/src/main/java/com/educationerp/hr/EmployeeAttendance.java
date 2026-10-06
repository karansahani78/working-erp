package com.educationerp.hr;

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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One row per employee per working day. Overtime lives here rather than on the payslip,
 * so an overtime payment can always be traced back to the days that earned it.
 */
@Entity
@Table(name = "employee_attendance",
        uniqueConstraints = @UniqueConstraint(name = "uk_employee_attendance",
                columnNames = {"employee_id", "attendance_date"}))
@Getter
@Setter
public class EmployeeAttendance extends BaseEntity {

    private static final int STANDARD_MINUTES = 8 * 60;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "check_in")
    private LocalTime checkIn;

    @Column(name = "check_out")
    private LocalTime checkOut;

    @Column(name = "overtime_minutes", nullable = false)
    private int overtimeMinutes;

    @Column(name = "remarks", length = 300)
    private String remarks;

    public enum Status {
        PRESENT, ABSENT, LATE, ON_LEAVE, HOLIDAY, WEEK_OFF;

        /** Whether this status represents a day actually worked, for overtime purposes. */
        public boolean hasWorked() {
            return this == PRESENT || this == LATE;
        }
    }

    /** A working day with no clock-out is not evidence of overtime. */
    public boolean hasWorked() {
        return status != null && status.hasWorked();
    }

    public BigDecimal workedHours() {
        if (!hasWorked() || checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            return BigDecimal.ZERO.setScale(2);
        }
        long minutes = Duration.between(checkIn, checkOut).toMinutes();
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /** Overtime is whatever exceeds a standard day, unless it was recorded explicitly. */
    public int effectiveOvertimeMinutes() {
        if (overtimeMinutes > 0) {
            return overtimeMinutes;
        }
        if (!hasWorked() || checkIn == null || checkOut == null) {
            return 0;
        }
        long worked = Duration.between(checkIn, checkOut).toMinutes();
        return (int) Math.max(0, worked - STANDARD_MINUTES);
    }
}