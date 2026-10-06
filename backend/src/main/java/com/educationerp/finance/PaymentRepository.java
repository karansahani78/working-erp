package com.educationerp.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByReceiptNumberIgnoreCase(String receiptNumber);

    Optional<Payment> findByProviderAndProviderTransactionId(Payment.Provider provider, String providerTransactionId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByStudentIdOrderByReceivedAtDesc(UUID studentId);

    List<Payment> findByInvoiceIdOrderByReceivedAtAsc(UUID invoiceId);

    List<Payment> findByStatus(Payment.Status status);
}
