package com.educationerp.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    List<Refund> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<Refund> findByStatus(Refund.Status status);

    List<Refund> findByPaymentIdAndStatusIn(UUID paymentId, List<Refund.Status> statuses);

    /**
     * Total already refunded against a payment. Written as a query because the aggregate
     * return type is not a field on the entity.
     */
    @Query("select coalesce(sum(r.amount), 0) from Refund r "
            + "where r.paymentId = :paymentId and r.status in :statuses")
    BigDecimal refundedTotal(@Param("paymentId") UUID paymentId,
                             @Param("statuses") List<Refund.Status> statuses);
}
