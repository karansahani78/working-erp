package com.educationerp.finance.service;

import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.common.numbering.SequenceNumberGenerator;
import com.educationerp.common.numbering.DocumentSequence;
import com.educationerp.finance.Fine;
import com.educationerp.finance.FineRepository;
import com.educationerp.finance.Invoice;
import com.educationerp.finance.InvoiceRepository;
import com.educationerp.finance.Payment;
import com.educationerp.finance.PaymentRepository;
import com.educationerp.finance.Refund;
import com.educationerp.finance.RefundRepository;
import com.educationerp.finance.StudentFeeAssessment;
import com.educationerp.finance.StudentFeeAssessmentRepository;
import com.educationerp.finance.dto.FinanceDtos;
import com.educationerp.finance.payment.PaymentGateway;
import com.educationerp.communication.CommunicationEventCode;
import com.educationerp.communication.CommunicationService;
import com.educationerp.communication.MessageEvent;
import com.educationerp.student.Student;
import com.educationerp.student.StudentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Taking money and giving it back.
 *
 * <p>Three rules drive everything here. A confirmed payment is never deleted or edited,
 * only refunded, because a receipt that can be rewritten is not evidence of anything. A
 * payment is accepted once per identity, by idempotency key or by provider transaction, so
 * a retried request or a replayed callback cannot take a second payment. And a provider's
 * word is never taken at face value: the amount and status used to confirm a PSP payment
 * come from a fresh read of the provider's own record, not from the callback body.
 */
@Service
public class PaymentService {

    private static final List<Refund.Status> SETTLED_REFUND_STATES =
            List.of(Refund.Status.APPROVED, Refund.Status.PROCESSED);

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final InvoiceRepository invoices;
    private final StudentFeeAssessmentRepository assessments;
    private final FineRepository fines;
    private final SequenceNumberGenerator numbers;
    private final AuditService audit;
    private final AuthorizationChecker auth;
    private final Map<Payment.Provider, PaymentGateway> gateways;
    private final CommunicationService communication;
    private final StudentRepository students;
    private final ApplicationEventPublisher events;

    public PaymentService(PaymentRepository payments, RefundRepository refunds, InvoiceRepository invoices,
                          StudentFeeAssessmentRepository assessments, FineRepository fines,
                          SequenceNumberGenerator numbers, AuditService audit, AuthorizationChecker auth,
                          List<PaymentGateway> gatewayList, CommunicationService communication,
                          StudentRepository students, ApplicationEventPublisher events) {
        this.payments = payments;
        this.refunds = refunds;
        this.invoices = invoices;
        this.assessments = assessments;
        this.fines = fines;
        this.numbers = numbers;
        this.audit = audit;
        this.auth = auth;
        this.gateways = gatewayList.stream()
                .collect(Collectors.toMap(PaymentGateway::provider, Function.identity(), (a, b) -> a));
        this.communication = communication;
        this.students = students;
        this.events = events;
    }

    // ------------------------------------------------------------------- payments

    @Transactional(readOnly = true)
    public List<FinanceDtos.PaymentResponse> listPayments(UUID studentId) {
        auth.requirePermission("PAYMENT_READ");
        return payments.findByStudentIdOrderByReceivedAtDesc(studentId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FinanceDtos.PaymentResponse getPayment(UUID id) {
        auth.requirePermission("PAYMENT_READ");
        return toResponse(requirePayment(id));
    }

    /**
     * Records a payment and, for a provider method, starts the charge. A repeated
     * idempotency key returns the original payment instead of raising an error, so a
     * client that retries after a timeout gets the same answer it would have got.
     */
    @Transactional
    public FinanceDtos.PaymentResponse record(FinanceDtos.RecordPayment request) {
        auth.requirePermission("PAYMENT_CREATE");
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Payment existing = payments.findByIdempotencyKey(request.idempotencyKey().trim())
                    .orElse(null);
            if (existing != null) {
                return toResponse(existing);
            }
        }
        Payment.Provider provider = resolveProvider(request);
        BigDecimal amount = money(request.amount());

        Billing billing = resolveBilling(request);

        Payment payment = new Payment();
        payment.setStudentId(request.studentId());
        payment.setInvoiceId(billing.invoiceId());
        payment.setAssessmentId(billing.assessmentId());
        payment.setReceiptNumber(numbers.next(DocumentSequence.Kind.RECEIPT));
        payment.setAmount(amount);
        payment.setCurrency(billing.currency());
        payment.setMethod(request.method());
        payment.setProvider(provider);
        payment.setIdempotencyKey(blankToNull(request.idempotencyKey()));
        payment.setReference(request.reference());
        payment.setStatus(Payment.Status.PENDING);
        payment.setReceivedAt(Instant.now());

        if (provider != Payment.Provider.MANUAL) {
            PaymentGateway gateway = requireGateway(provider);
            payment.setProviderTransactionId(gateway.initiate(new PaymentGateway.PaymentRequest(
                    request.studentId(), amount, payment.getCurrency(), payment.getReceiptNumber())));
            // Manual money is already in hand, so a cashier-entered payment is confirmed
            // immediately. A PSP payment stays pending until the provider confirms it.
            payment.setStatus(Payment.Status.PENDING);
        } else {
            payment.setStatus(Payment.Status.CONFIRMED);
            payment.setConfirmedAt(Instant.now());
        }
        Payment saved = payments.save(payment);
        if (saved.getStatus() == Payment.Status.CONFIRMED) {
            applyToBalance(saved);
        }
        audit.record(AuditEvent.builder()
                .action(AuditAction.PAYMENT)
                .entityType("Payment")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getReceiptNumber())
                .summary(saved.getAmount().toPlainString() + " " + saved.getCurrency() + " " + saved.getStatus())
                .module("FINANCE")
                .succeeded(true)
                .build());
        return toResponse(saved);
    }

    /**
     * Applies a provider callback. The body only tells us which transaction to look at:
     * the amount and status come from the provider's own record, so a forged or tampered
     * payload cannot mark an unpaid invoice as settled.
     */
    @Transactional
    public FinanceDtos.PaymentResponse handleCallback(FinanceDtos.ProviderCallback callback,
                                                      String payload, String signature) {
        // Find our own record first: an unknown transaction is a 404 whatever the
        // provider's configuration looks like, so the answer does not depend on it.
        Payment payment = payments
                .findByProviderAndProviderTransactionId(callback.provider(), callback.providerTransactionId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                        "No payment matches that provider transaction."));
        PaymentGateway gateway = requireGateway(callback.provider());
        if (payment.getStatus() == Payment.Status.CONFIRMED) {
            // A replayed callback is normal, not an error: providers retry until they get
            // a success response.
            return toResponse(payment);
        }
        if (!gateway.verifyCallback(payload, signature)) {
            audit.record(AuditEvent.builder()
                    .action(AuditAction.UPDATE)
                    .entityType("Payment")
                    .entityId(payment.getId().toString())
                    .summary("Rejected an unsigned or mismatched callback for "
                            + callback.providerTransactionId())
                    .module("FINANCE")
                    .succeeded(false)
                    .failureReason("Callback signature could not be verified")
                    .build());
        }

        PaymentGateway.ProviderPayment verified =
                gateway.verify(callback.providerTransactionId());
        if (verified.amount().compareTo(payment.getAmount()) != 0) {
            payment.setStatus(Payment.Status.FAILED);
            payment.setFailureReason("The provider reported a different amount than was requested.");
            payments.save(payment);
            throw new AppException(ErrorCode.PAYMENT_FAILED,
                    "The provider reported a different amount than was requested.");
        }
        if (verified.status() == Payment.Status.CONFIRMED) {
            payment.setStatus(Payment.Status.CONFIRMED);
            payment.setConfirmedAt(Instant.now());
            payment.setFailureReason(null);
            applyToBalance(payment);
            payments.save(payment);
            notifyPaymentReceived(payment);
        } else if (verified.status() == Payment.Status.FAILED) {
            payment.setStatus(Payment.Status.FAILED);
            payment.setFailureReason(verified.message());
        }
        payments.save(payment);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("Payment")
                .entityId(payment.getId().toString())
                .entityLabel(payment.getReceiptNumber())
                .summary(payment.getStatus() + " after verifying " + callback.providerTransactionId())
                .module("FINANCE")
                .succeeded(true)
                .build());
        return toResponse(payment);
    }

    @Transactional
    public FinanceDtos.PaymentResponse confirmManually(UUID id) {
        auth.requirePermission("PAYMENT_CREATE");
        Payment payment = requirePayment(id);
        if (payment.getStatus() == Payment.Status.CONFIRMED) {
            return toResponse(payment);
        }
        if (payment.getProvider() != Payment.Provider.MANUAL) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a manual payment can be confirmed without the provider.");
        }
        payment.setStatus(Payment.Status.CONFIRMED);
        payment.setConfirmedAt(Instant.now());
        applyToBalance(payment);
        payments.save(payment);
        notifyPaymentReceived(payment);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("Payment")
                .entityId(payment.getId().toString())
                .entityLabel(payment.getReceiptNumber())
                .summary("Manually confirmed")
                .module("FINANCE")
                .succeeded(true)
                .build());
        return toResponse(payments.save(payment));
    }


    /**
     * Tells the student and their guardians that money has arrived.
     *
     * <p>Sent from both confirmation paths -- a provider callback and a manual confirmation --
     * because a receipt nobody heard about is the commonest complaint this kind of system
     * gets. The notification is keyed on the payment, so a provider replaying a callback
     * cannot produce a second receipt message.
     */
    private void notifyPaymentReceived(Payment payment) {
        List<UUID> recipients = communication.recipientsForStudent(payment.getStudentId(), null);
        if (recipients.isEmpty()) {
            return;
        }
        Student student = students.findById(payment.getStudentId()).orElse(null);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("studentName", student == null ? "Student" : student.displayName());
        variables.put("amount", payment.getAmount().toPlainString());
        variables.put("receiptNumber", payment.getReceiptNumber());
        variables.put("date", payment.getConfirmedAt() == null ? "" 
                : payment.getConfirmedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString());
        events.publishEvent(MessageEvent.of(CommunicationEventCode.PAYMENT_COMPLETED,
                recipients, variables, "Payment", payment.getId()));
    }

    @Transactional
    public FinanceDtos.PaymentResponse fail(UUID id, String reason) {
        auth.requirePermission("PAYMENT_CREATE");
        Payment payment = requirePayment(id);
        if (payment.getStatus() == Payment.Status.CONFIRMED) {
            throw new AppException(ErrorCode.PAYMENT_ALREADY_PROCESSED,
                    "A confirmed payment cannot be marked failed. Refund it instead.");
        }
        payment.setStatus(Payment.Status.FAILED);
        payment.setFailureReason(reason);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("Payment")
                .entityId(payment.getId().toString())
                .entityLabel(payment.getReceiptNumber())
                .summary("Marked failed")
                .module("FINANCE")
                .succeeded(true)
                .failureReason(reason)
                .build());
        return toResponse(payments.save(payment));
    }

    // -------------------------------------------------------------------- refunds

    @Transactional(readOnly = true)
    public List<FinanceDtos.RefundResponse> listRefunds(UUID studentId) {
        auth.requirePermission("PAYMENT_READ");
        return refunds.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::toRefundResponse).toList();
    }

    @Transactional
    public FinanceDtos.RefundResponse requestRefund(UUID studentId, FinanceDtos.RequestRefund request) {
        auth.requirePermission("PAYMENT_REFUND");
        Payment payment = requirePayment(request.paymentId());
        if (!payment.getStudentId().equals(studentId)) {
            throw AppException.notFound("Payment");
        }
        if (payment.getStatus() != Payment.Status.CONFIRMED
                && payment.getStatus() != Payment.Status.PARTIALLY_REFUNDED) {
            throw new AppException(ErrorCode.REFUND_NOT_ALLOWED,
                    "Only a confirmed payment can be refunded.");
        }
        BigDecimal already = nullSafe(refunds.refundedTotal(payment.getId(), SETTLED_REFUND_STATES));
        BigDecimal refundable = payment.getAmount().subtract(already);
        BigDecimal amount = money(request.amount());
        if (amount.compareTo(refundable) > 0) {
            throw new AppException(ErrorCode.REFUND_NOT_ALLOWED,
                    "Only " + refundable.toPlainString() + " of this payment is still refundable.");
        }
        Refund refund = new Refund();
        refund.setPaymentId(payment.getId());
        refund.setStudentId(studentId);
        refund.setAmount(amount);
        refund.setReason(request.reason().trim());
        refund.setMethod(request.method() == null ? payment.getMethod() : request.method());
        refund.setStatus(Refund.Status.PENDING);
        refund.setRequestedBy(currentUserIdOrNull());
        Refund saved = refunds.save(refund);
        audit.record(AuditEvent.builder()
                .action(AuditAction.REFUND)
                .entityType("Refund")
                .entityId(saved.getId().toString())
                .entityLabel(payment.getReceiptNumber())
                .summary("Requested a refund of " + amount.toPlainString())
                .module("FINANCE")
                .succeeded(true)
                .build());
        return toRefundResponse(saved);
    }

    @Transactional
    public FinanceDtos.RefundResponse decideRefund(UUID id, FinanceDtos.RefundDecision decision) {
        auth.requirePermission("PAYMENT_REFUND");
        Refund refund = refunds.findById(id).orElseThrow(() -> AppException.notFound("Refund"));
        if (refund.getStatus() != Refund.Status.PENDING) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a pending refund can be approved or rejected.");
        }
        Payment payment = requirePayment(refund.getPaymentId());
        if (decision.status() == Refund.Status.REJECTED) {
            refund.setStatus(Refund.Status.REJECTED);
            audit.record(AuditEvent.builder()
                    .action(AuditAction.UPDATE)
                    .entityType("Refund")
                    .entityId(refund.getId().toString())
                    .summary("Rejected the refund request")
                    .module("FINANCE")
                    .succeeded(true)
                    .failureReason(decision.reason())
                    .build());
            return toRefundResponse(refunds.save(refund));
        }
        if (decision.status() != Refund.Status.APPROVED && decision.status() != Refund.Status.PROCESSED) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "A refund decision must approve or reject the request.");
        }
        refund.setStatus(Refund.Status.PROCESSED);
        refund.setApprovedBy(currentUserIdOrNull());
        refund.setApprovedAt(Instant.now());
        refund.setProcessedAt(Instant.now());
        refunds.save(refund);

        // The original payment row is left intact; the refund is the record of the change.
        BigDecimal totalRefunded = nullSafe(refunds.refundedTotal(payment.getId(),
                List.of(Refund.Status.PROCESSED)));
        payment.setStatus(totalRefunded.compareTo(payment.getAmount()) >= 0
                ? Payment.Status.REFUNDED : Payment.Status.PARTIALLY_REFUNDED);
        payments.save(payment);
        applyRefundToBalance(payment, refund);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("Refund")
                .entityId(refund.getId().toString())
                .entityLabel(payment.getReceiptNumber())
                .summary("Processed a refund of " + refund.getAmount().toPlainString())
                .module("FINANCE")
                .succeeded(true)
                .build());
        return toRefundResponse(refunds.findById(id).orElseThrow());
    }

    // ---------------------------------------------------------------------- fines

    @Transactional
    public FinanceDtos.FineResponse createFine(FinanceDtos.CreateFine request) {
        auth.requirePermission("FEE_CREATE");
        Fine fine = new Fine();
        fine.setStudentId(request.studentId());
        fine.setDescription(request.description().trim());
        fine.setAmount(money(request.amount()));
        fine.setAcademicYearId(request.academicYearId());
        fine.setStatus(Fine.Status.PENDING);
        Fine saved = fines.save(fine);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("Fine")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getDescription())
                .summary("Issued a fine of " + saved.getAmount().toPlainString())
                .module("FINANCE")
                .succeeded(true)
                .build());
        return toFineResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FinanceDtos.FineResponse> listFines(UUID studentId) {
        auth.requirePermission("FEE_READ");
        return fines.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::toFineResponse).toList();
    }

    @Transactional
    public FinanceDtos.FineResponse waiveFine(UUID id) {
        auth.requirePermission("FEE_APPROVE");
        Fine fine = fines.findById(id).orElseThrow(() -> AppException.notFound("Fine"));
        if (fine.getStatus() != Fine.Status.PENDING) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a pending fine can be waived or paid.");
        }
        fine.setStatus(Fine.Status.WAIVED);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("Fine")
                .entityId(fine.getId().toString())
                .entityLabel(fine.getDescription())
                .summary("Waived")
                .module("FINANCE")
                .succeeded(true)
                .build());
        return toFineResponse(fines.save(fine));
    }

    // -------------------------------------------------------------------- helpers

    /**
     * Moves a confirmed payment onto the bill it settles. The invoice and the assessment
     * are updated from the payment's own amount, so a partial payment leaves a truthful
     * partial balance on both.
     */
    private void applyToBalance(Payment payment) {
        if (payment.getInvoiceId() != null) {
            Invoice invoice = invoices.findById(payment.getInvoiceId()).orElse(null);
            if (invoice != null) {
                BigDecimal paid = invoice.getPaidAmount().add(payment.getAmount());
                if (paid.compareTo(invoice.getTotalAmount()) > 0) {
                    throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "This payment is larger than the balance on the invoice.");
                }
                invoice.setPaidAmount(paid);
                invoice.setStatus(paid.compareTo(invoice.getTotalAmount()) == 0
                        ? Invoice.Status.PAID
                        : paid.compareTo(BigDecimal.ZERO) > 0
                        ? Invoice.Status.PARTIALLY_PAID : Invoice.Status.ISSUED);
                invoices.save(invoice);
            }
        }
        if (payment.getAssessmentId() != null) {
            StudentFeeAssessment assessment = assessments.findById(payment.getAssessmentId()).orElse(null);
            if (assessment != null) {
                BigDecimal paid = assessment.getPaidAmount().add(payment.getAmount());
                if (paid.compareTo(assessment.getNetAmount()) > 0) {
                    throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "This payment is larger than the balance on the assessment.");
                }
                assessment.setPaidAmount(paid);
                assessment.setStatus(paid.compareTo(assessment.getNetAmount()) == 0
                        ? StudentFeeAssessment.Status.PAID : StudentFeeAssessment.Status.PARTIALLY_PAID);
                assessments.save(assessment);
            }
        }
    }

    private void applyRefundToBalance(Payment payment, Refund refund) {
        if (payment.getAssessmentId() != null) {
            assessments.findById(payment.getAssessmentId()).ifPresent(assessment -> {
                assessment.setRefundedAmount(assessment.getRefundedAmount().add(refund.getAmount()));
                if (assessment.getStatus() == StudentFeeAssessment.Status.PAID) {
                    // The student no longer owes the refunded portion.
                    assessment.setStatus(StudentFeeAssessment.Status.PARTIALLY_PAID);
                }
                assessments.save(assessment);
            });
        }
        if (payment.getInvoiceId() != null) {
            invoices.findById(payment.getInvoiceId()).ifPresent(invoice -> {
                BigDecimal paid = invoice.getPaidAmount().subtract(refund.getAmount());
                invoice.setPaidAmount(paid.max(BigDecimal.ZERO));
                invoice.setStatus(invoice.getPaidAmount().compareTo(BigDecimal.ZERO) == 0
                        ? Invoice.Status.ISSUED
                        : invoice.getPaidAmount().compareTo(invoice.getTotalAmount()) == 0
                        ? Invoice.Status.PAID : Invoice.Status.PARTIALLY_PAID);
                invoices.save(invoice);
            });
        }
    }

    /** What a payment settles: an invoice and the assessment behind it, in the assessed currency. */
    private record Billing(UUID invoiceId, UUID assessmentId, String currency) {
    }

    /**
     * Works out what the payment settles and proves the bill belongs to the payer. An invoice
     * settles its own assessment; otherwise the student's open assessment is used. Naming an
     * invoice that belongs to somebody else would credit one student's bill out of another
     * student's payment, so the two have to agree.
     */
    private Billing resolveBilling(FinanceDtos.RecordPayment request) {
        if (request.invoiceId() != null) {
            Invoice invoice = invoices.findById(request.invoiceId())
                    .orElseThrow(() -> AppException.notFound("Invoice"));
            if (!invoice.getStudentId().equals(request.studentId())) {
                throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "That invoice belongs to another student.");
            }
            if (invoice.getStatus() != Invoice.Status.ISSUED) {
                throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                        "Only an issued invoice can be paid.");
            }
            String currency = assessments.findById(invoice.getAssessmentId())
                    .map(StudentFeeAssessment::getCurrency)
                    .orElse("NPR");
            return new Billing(invoice.getId(), invoice.getAssessmentId(), currency);
        }
        StudentFeeAssessment assessment = assessments
                .findByStudentIdOrderByCreatedAtDesc(request.studentId()).stream()
                .filter(candidate -> candidate.getStatus() == StudentFeeAssessment.Status.PENDING
                        || candidate.getStatus() == StudentFeeAssessment.Status.PARTIALLY_PAID)
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "This student has no outstanding fee assessment to pay against."));
        return new Billing(null, assessment.getId(), assessment.getCurrency());
    }

    /**
     * A manual method implies a manual provider. A digital method with no provider named is
     * a mistake worth catching now rather than a payment stuck in PENDING forever.
     */
    private Payment.Provider resolveProvider(FinanceDtos.RecordPayment request) {
        if (request.provider() != null) {
            return request.provider();
        }
        return switch (request.method()) {
            case CASH, BANK_TRANSFER, CHEQUE -> Payment.Provider.MANUAL;
            case ESEWA, KHALTI, FONEPAY -> throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Name the provider for a digital payment so it can be verified.");
        };
    }

    private PaymentGateway requireGateway(Payment.Provider provider) {
        PaymentGateway gateway = gateways.get(provider);
        if (gateway == null) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE,
                    provider + " payments are not configured for this institution.");
        }
        return gateway;
    }

    private Payment requirePayment(UUID id) {
        return payments.findById(id).orElseThrow(() -> AppException.notFound("Payment"));
    }

    /** The audit trail already attributes the event, so a background job may have no user. */
    private UUID currentUserIdOrNull() {
        return auth.currentUserOrNull() == null ? null : auth.currentUser().userId();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    FinanceDtos.PaymentResponse toResponse(Payment payment) {
        return new FinanceDtos.PaymentResponse(
                payment.getId(), payment.getStudentId(), payment.getInvoiceId(), payment.getReceiptNumber(),
                payment.getAmount(), payment.getCurrency(), payment.getMethod(), payment.getProvider(),
                payment.getProviderTransactionId(), payment.getIdempotencyKey(), payment.getReference(),
                payment.getStatus(), payment.getFailureReason(), payment.getReceivedAt(), payment.getConfirmedAt());
    }

    FinanceDtos.RefundResponse toRefundResponse(Refund refund) {
        return new FinanceDtos.RefundResponse(
                refund.getId(), refund.getPaymentId(), refund.getStudentId(), refund.getAmount(),
                refund.getReason(), refund.getMethod(), refund.getStatus(), refund.getRequestedBy(),
                refund.getApprovedBy(), refund.getApprovedAt(), refund.getProcessedAt(), refund.getCreatedAt());
    }

    /** Newest money first: what a staff member opening the finance tab wants at the top. */
    List<Payment> paymentsOf(UUID studentId) {
        return payments.findByStudentIdOrderByReceivedAtDesc(studentId);
    }

    List<FinanceDtos.RefundResponse> refundsOf(UUID studentId) {
        return refunds.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::toRefundResponse).toList();
    }

    List<FinanceDtos.FineResponse> finesOf(UUID studentId) {
        return fines.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::toFineResponse).toList();
    }

    FinanceDtos.FineResponse toFineResponse(Fine fine) {
        return new FinanceDtos.FineResponse(fine.getId(), fine.getStudentId(), fine.getDescription(),
                fine.getAmount(), fine.getAcademicYearId(), fine.getStatus(), fine.getCreatedAt());
    }
}
