package com.educationerp.hr;

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

/** How much leave of one type an employee has left this year. */
@Entity
@Table(name = "leave_balances",
        uniqueConstraints = @UniqueConstraint(name = "uk_leave_balances",
                columnNames = {"employee_id", "leave_type_id", "leave_year"}))
@Getter
@Setter
public class LeaveBalance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "leave_year", nullable = false)
    private int leaveYear;

    @Column(name = "entitled", nullable = false, precision = 6, scale = 1)
    private BigDecimal entitled = BigDecimal.ZERO;

    @Column(name = "used", nullable = false, precision = 6, scale = 1)
    private BigDecimal used = BigDecimal.ZERO;

    public BigDecimal remaining() {
        return entitled.subtract(used);
    }

    public boolean canTake(BigDecimal days) {
        return remaining().compareTo(days) >= 0;
    }

    public void take(BigDecimal days) {
        used = used.add(days);
    }
}