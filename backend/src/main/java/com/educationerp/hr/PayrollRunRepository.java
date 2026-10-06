package com.educationerp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

    Optional<PayrollRun> findByPeriodYearAndPeriodMonth(int periodYear, int periodMonth);

    boolean existsByPeriodYearAndPeriodMonth(int periodYear, int periodMonth);

    List<PayrollRun> findByStatusOrderByPeriodYearDescPeriodMonthDesc(PayrollRun.Status status);

    List<PayrollRun> findAllByOrderByPeriodYearDescPeriodMonthDesc();
}