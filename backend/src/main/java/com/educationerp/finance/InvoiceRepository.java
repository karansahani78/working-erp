package com.educationerp.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByInvoiceNumberIgnoreCase(String invoiceNumber);

    boolean existsByInvoiceNumberIgnoreCase(String invoiceNumber);

    List<Invoice> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<Invoice> findByStatusOrderByDueDateAsc(Invoice.Status status);

    List<Invoice> findByAssessmentId(UUID assessmentId);
}
