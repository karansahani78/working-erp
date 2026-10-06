package com.educationerp.finance.service;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.common.numbering.SequenceNumberGenerator;
import com.educationerp.finance.FeeComponent;
import com.educationerp.common.numbering.DocumentSequence;
import com.educationerp.finance.FeeStructure;
import com.educationerp.finance.FeeStructureRepository;
import com.educationerp.finance.Invoice;
import com.educationerp.finance.InvoiceItem;
import com.educationerp.finance.InvoiceItemRepository;
import com.educationerp.finance.InvoiceRepository;
import com.educationerp.finance.Payment;
import com.educationerp.finance.StudentFeeAssessment;
import com.educationerp.finance.StudentFeeAssessmentRepository;
import com.educationerp.finance.dto.FinanceDtos;
import com.educationerp.communication.CommunicationEventCode;
import com.educationerp.communication.CommunicationService;
import com.educationerp.communication.MessageEvent;
import com.educationerp.student.Student;
import com.educationerp.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turning a published fee structure into a student's billable assessment, and an
 * assessment into an invoice.
 *
 * <p>The gross, discount, scholarship and net amounts are snapshotted onto the
 * assessment. Later edits to a fee structure or an award therefore cannot rewrite what a
 * student was told they owe, which is what makes an invoice defensible at audit.
 */
@Service
@RequiredArgsConstructor
public class FeeAssessmentService {

    private final FeeStructureRepository structures;
    private final StudentFeeAssessmentRepository assessments;
    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final SequenceNumberGenerator numbers;
    private final PaymentService paymentService;
    private final AuthorizationChecker auth;
    private final CommunicationService communication;
    private final StudentRepository students;
    private final ApplicationEventPublisher events;

    // ---------------------------------------------------------------- assessments

    @Transactional(readOnly = true)
    public List<FinanceDtos.FeeAssessmentResponse> listAssessments(UUID studentId) {
        auth.requirePermission("FEE_READ");
        return assessments.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::toAssessmentResponse).toList();
    }

    @Transactional(readOnly = true)
    public FinanceDtos.FeeAssessmentResponse getAssessment(UUID id) {
        auth.requirePermission("FEE_READ");
        return toAssessmentResponse(requireAssessment(id));
    }

    /**
     * Snapshots a published fee structure onto a student. Re-assessing the same structure
     * for the same student is rejected rather than quietly double-billing them.
     */
    @Transactional
    public FinanceDtos.FeeAssessmentResponse assess(UUID studentId, FinanceDtos.AssessFees request) {
        auth.requirePermission("FEE_CREATE");
        return assessWithoutPermissionCheck(studentId, request);
    }

    /**
     * The billing itself, with the permission check left to the caller.
     *
     * <p>Kept separate from {@link #assess} because the system assesses fees on its own
     * initiative in at least two places -- admitting a student, and opening a billing
     * period. Those paths are the system speaking, not a clerk billing by hand, so they must
     * not require the clerk's permission to do it.
     */
    private FinanceDtos.FeeAssessmentResponse assessWithoutPermissionCheck(UUID studentId,
                                                                           FinanceDtos.AssessFees request) {
        FeeStructure structure = structures.findById(request.feeStructureId())
                .orElseThrow(() -> AppException.notFound("Fee structure"));
        if (structure.getStatus() != FeeStructure.Status.PUBLISHED) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only a published fee structure can be assessed to a student.");
        }
        assessments.findByStudentIdAndFeeStructureId(studentId, structure.getId()).ifPresent(existing -> {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE,
                    "This student has already been assessed against this fee structure.");
        });
        if (request.dueDate().isBefore(LocalDate.now())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The due date cannot be in the past.");
        }

        BigDecimal gross = money(structure.getTotalAmount());
        StudentFeeAssessment assessment = new StudentFeeAssessment();
        assessment.setStudentId(studentId);
        assessment.setFeeStructureId(structure.getId());
        assessment.setAcademicYearId(structure.getAcademicYearId());
        assessment.setGrossAmount(gross);
        assessment.setDiscountAmount(BigDecimal.ZERO);
        assessment.setScholarshipAmount(BigDecimal.ZERO);
        assessment.setNetAmount(gross);
        assessment.setPaidAmount(BigDecimal.ZERO);
        assessment.setRefundedAmount(BigDecimal.ZERO);
        assessment.setCurrency(structure.getCurrency());
        assessment.setDueDate(request.dueDate());
        assessment.setNotes(request.notes());
        assessment.setStatus(StudentFeeAssessment.Status.PENDING);
        StudentFeeAssessment saved = assessments.save(assessment);
        notifyFeeDue(saved, structure);
        return toAssessmentResponse(saved);
    }

    /**
     * Tells the family that a bill has landed, with the amount and the date it is wanted by.
     *
     * <p>Sent once per assessment. A reopened billing period that assesses the same structure
     * again is refused by the duplicate guard before it reaches this point, so there is no
     * second bill to announce.
     */
    private void notifyFeeDue(StudentFeeAssessment assessment, FeeStructure structure) {
        List<UUID> recipients = communication.recipientsForStudent(assessment.getStudentId(), null);
        if (recipients.isEmpty()) {
            return;
        }
        Student student = students.findById(assessment.getStudentId()).orElse(null);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("studentName", student == null ? "Student" : student.displayName());
        variables.put("amount", assessment.getNetAmount().toPlainString());
        variables.put("currency", assessment.getCurrency());
        variables.put("dueDate", assessment.getDueDate().toString());
        variables.put("feeStructure", structure.getName());
        events.publishEvent(MessageEvent.of(CommunicationEventCode.FEE_DUE,
                recipients, variables, "StudentFeeAssessment", assessment.getId()));
    }

    /**
     * The admission path's fee step. The blueprint puts fee assessment between approval and
     * payment, and forbids re-typing what the system already knows, so the structure is
     * chosen by the caller and a retry returns the assessment already on the student
     * instead of a duplicate. There is no FEE_CREATE check here on purpose: this is the
     * system speaking for itself, not a clerk billing by hand, and a registrar who may
     * approve an admission need not also hold the fee permission.
     *
     * @return the assessment now on the student, or {@code null} when the structure is
     *         not published and so cannot be charged yet
     */
    @Transactional
    public FinanceDtos.FeeAssessmentResponse assessAutomatically(UUID studentId, UUID feeStructureId,
                                                                 LocalDate dueDate, String notes) {
        FeeStructure structure = structures.findById(feeStructureId)
                .orElseThrow(() -> AppException.notFound("Fee structure"));
        if (structure.getStatus() != FeeStructure.Status.PUBLISHED) {
            return null;
        }
        LocalDate effectiveDueDate = dueDate == null || dueDate.isBefore(LocalDate.now())
                ? LocalDate.now().plusMonths(1)
                : dueDate;
        return assessments.findByStudentIdAndFeeStructureId(studentId, structure.getId())
                .map(this::toAssessmentResponse)
                .orElseGet(() -> assessWithoutPermissionCheck(studentId,
                        new FinanceDtos.AssessFees(structure.getId(), effectiveDueDate, notes)));
    }

    @Transactional
    public FinanceDtos.FeeAssessmentResponse cancelAssessment(UUID id, String reason) {        auth.requirePermission("FEE_APPROVE");
        StudentFeeAssessment assessment = requireAssessment(id);
        if (assessment.getStatus() == StudentFeeAssessment.Status.CANCELLED) {
            return toAssessmentResponse(assessment);
        }
        if (assessment.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            // Money has already been taken against this bill, so cancelling it would leave
            // the payment with nothing to sit against. The refund path handles this.
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This assessment has payments against it. Refund the payments before cancelling it.");
        }
        assessment.setStatus(StudentFeeAssessment.Status.CANCELLED);
        assessment.setNotes(reason == null || reason.isBlank()
                ? assessment.getNotes() : reason);
        return toAssessmentResponse(assessments.save(assessment));
    }

    // ------------------------------------------------------------------- invoices

    @Transactional(readOnly = true)
    public FinanceDtos.InvoiceResponse getInvoice(UUID id) {
        auth.requirePermission("INVOICE_READ");
        return toInvoiceResponse(requireInvoice(id));
    }

    @Transactional(readOnly = true)
    public List<FinanceDtos.InvoiceResponse> listInvoices(UUID studentId) {
        auth.requirePermission("INVOICE_READ");
        return invoices.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::toInvoiceResponse).toList();
    }

    /**
     * Issues a draft invoice from an assessment. The invoice totals are snapshotted at
     * issue time; the assessment's net amount is carried across unchanged so there is a
     * single amount owed, stated once.
     */
    @Transactional
    public FinanceDtos.InvoiceResponse createInvoice(UUID studentId, FinanceDtos.CreateInvoice request) {
        auth.requirePermission("INVOICE_CREATE");
        StudentFeeAssessment assessment = assessments
                .findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .filter(candidate -> request.assessmentId().equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("Fee assessment"));
        if (assessment.getStatus() == StudentFeeAssessment.Status.CANCELLED) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "A cancelled assessment cannot be invoiced.");
        }
        if (assessment.getNetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This assessment has nothing left to invoice.");
        }
        List<Invoice> existing = invoices.findByAssessmentId(assessment.getId());
        if (existing.stream().anyMatch(invoice -> invoice.getStatus() != Invoice.Status.CANCELLED)) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE,
                    "An invoice has already been issued for this assessment.");
        }
        if (request.dueDate() != null && request.dueDate().isBefore(request.issueDate())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The invoice due date cannot be before the issue date.");
        }

        Invoice invoice = new Invoice();
        invoice.setStudentId(studentId);
        invoice.setAssessmentId(assessment.getId());
        invoice.setAcademicYearId(assessment.getAcademicYearId());
        invoice.setInvoiceNumber(numbers.next(DocumentSequence.Kind.INVOICE, request.issueDate()));
        invoice.setIssueDate(request.issueDate());
        invoice.setDueDate(request.dueDate());
        invoice.setTotalAmount(assessment.getNetAmount());
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setStatus(Invoice.Status.DRAFT);
        invoice.setNotes(request.notes());
        invoices.save(invoice);

        // The charge lines add up to gross and the concession lines come off them, so the
        // student can see what they were charged and what was waived, and the lines still
        // total the assessed net.
        addItem(invoice.getId(), "Tuition and academic fees", FeeComponent.ComponentType.TUITION,
                assessment.getGrossAmount());
        if (assessment.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            addItem(invoice.getId(), "Discount applied", FeeComponent.ComponentType.ADMISSION,
                    assessment.getDiscountAmount(), true);
        }
        if (assessment.getScholarshipAmount().compareTo(BigDecimal.ZERO) > 0) {
            addItem(invoice.getId(), "Scholarship applied", FeeComponent.ComponentType.OTHER,
                    assessment.getScholarshipAmount(), true);
        }
        return toInvoiceResponse(invoices.findById(invoice.getId()).orElseThrow());
    }

    @Transactional
    public FinanceDtos.InvoiceResponse issueInvoice(UUID id) {
        auth.requirePermission("INVOICE_CREATE");
        Invoice invoice = requireInvoice(id);
        if (invoice.getStatus() != Invoice.Status.DRAFT) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a draft invoice can be issued.");
        }
        BigDecimal total = lineTotal(id);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "An invoice with no chargeable lines cannot be issued.");
        }
        if (total.compareTo(invoice.getTotalAmount()) != 0) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "The invoice lines total " + total.toPlainString()
                            + " but the invoice total is " + invoice.getTotalAmount().toPlainString() + ".");
        }
        invoice.setStatus(Invoice.Status.ISSUED);
        return toInvoiceResponse(invoices.save(invoice));
    }

    @Transactional
    public FinanceDtos.InvoiceResponse cancelInvoice(UUID id, String reason) {
        auth.requirePermission("INVOICE_CREATE");
        Invoice invoice = requireInvoice(id);
        if (invoice.getStatus() == Invoice.Status.CANCELLED) {
            return toInvoiceResponse(invoice);
        }
        if (invoice.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This invoice has payments against it. Refund them before cancelling it.");
        }
        invoice.setStatus(Invoice.Status.CANCELLED);
        invoice.setNotes(reason);
        return toInvoiceResponse(invoices.save(invoice));
    }

    // ------------------------------------------------------------------ reporting

    @Transactional(readOnly = true)
    public FinanceDtos.ReceivablesReport receivables() {
        auth.requirePermission("FINANCE_REPORT_READ");
        List<StudentFeeAssessment> open =
                assessments.findOutstanding(StudentFeeAssessment.Status.CANCELLED);
        LocalDate today = LocalDate.now();
        List<FinanceDtos.ReceivablesRow> rows = open.stream().map(assessment -> {
            BigDecimal outstanding = money(assessment.outstanding());
            long days = assessment.getDueDate() == null || !assessment.getDueDate().isBefore(today)
                    ? 0 : ChronoUnit.DAYS.between(assessment.getDueDate(), today);
            return new FinanceDtos.ReceivablesRow(assessment.getId(), assessment.getStudentId(),
                    assessment.getFeeStructureId(), assessment.getNetAmount(), assessment.getPaidAmount(),
                    outstanding, assessment.getDueDate(), days, assessment.getStatus());
        }).toList();
        BigDecimal total = rows.stream().map(FinanceDtos.ReceivablesRow::outstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overdue = rows.stream().filter(row -> row.daysOverdue() > 0)
                .map(FinanceDtos.ReceivablesRow::outstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinanceDtos.ReceivablesReport(money(total), money(overdue), rows.size(), rows);
    }

    // ---------------------------------------------------------------- student summary

    /**
     * Everything one student owes and has paid, for the student 360 finance tab.
     *
     * <p>Access is checked as student access rather than as the finance module permissions:
     * whoever may open the student's record may see their billing, and asking for FEE_READ
     * as well would make the finance tab turn the whole 360 view into a 403 for a
     * counsellor who legitimately has the student but not the ledger.
     *
     * @return null when the student has no fee activity at all, so the tab is absent rather
     *         than an empty box
     */
    @Transactional(readOnly = true)
    public FinanceDtos.StudentFeeSummary studentSummary(UUID studentId) {
        auth.requireStudentAccess(studentId);
        List<StudentFeeAssessment> billable = assessments.findByStudentIdOrderByCreatedAtDesc(studentId);
        List<Invoice> studentInvoices = invoices.findByStudentIdOrderByCreatedAtDesc(studentId);
        List<Payment> studentPayments = paymentService.paymentsOf(studentId);
        if (billable.isEmpty() && studentInvoices.isEmpty() && studentPayments.isEmpty()) {
            return null;
        }
        // Cancelled bills are history, not money: they must not inflate what is owed.
        List<StudentFeeAssessment> live = billable.stream()
                .filter(assessment -> assessment.getStatus() != StudentFeeAssessment.Status.CANCELLED)
                .toList();
        BigDecimal net = live.stream().map(StudentFeeAssessment::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = live.stream().map(StudentFeeAssessment::outstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Paid is read off the money that actually came in, not off the bills.
        BigDecimal paid = studentPayments.stream()
                .filter(payment -> payment.getStatus() == Payment.Status.CONFIRMED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinanceDtos.StudentFeeSummary(studentId,
                live.stream().map(this::toAssessmentResponse).toList(),
                studentInvoices.stream().map(this::toInvoiceResponse).toList(),
                studentPayments.stream().map(paymentService::toResponse).toList(),
                paymentService.refundsOf(studentId),
                paymentService.finesOf(studentId),
                money(net), money(paid), money(outstanding));
    }

    // -------------------------------------------------------------------- helpers

    private void addItem(UUID invoiceId, String description,
                         FeeComponent.ComponentType type, BigDecimal amount) {
        addItem(invoiceId, description, type, amount, false);
    }

    private void addItem(UUID invoiceId, String description,
                         FeeComponent.ComponentType type, BigDecimal amount, boolean concession) {
        InvoiceItem item = new InvoiceItem();
        item.setInvoiceId(invoiceId);
        item.setDescription(description);
        item.setComponentType(type);
        item.setAmount(money(amount));
        item.setConcession(concession);
        items.save(item);
    }

    /** Charge lines less concession lines: what the student actually owes. */
    private BigDecimal lineTotal(UUID invoiceId) {
        return items.findByInvoiceIdOrderByComponentTypeAsc(invoiceId).stream()
                .map(line -> line.isConcession() ? line.getAmount().negate() : line.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    StudentFeeAssessment requireAssessment(UUID id) {
        return assessments.findById(id).orElseThrow(() -> AppException.notFound("Fee assessment"));
    }

    Invoice requireInvoice(UUID id) {
        return invoices.findById(id).orElseThrow(() -> AppException.notFound("Invoice"));
    }

    BigDecimal money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    FinanceDtos.FeeAssessmentResponse toAssessmentResponse(StudentFeeAssessment assessment) {
        return new FinanceDtos.FeeAssessmentResponse(
                assessment.getId(),
                assessment.getStudentId(),
                assessment.getFeeStructureId(),
                assessment.getAcademicYearId(),
                assessment.getGrossAmount(),
                assessment.getDiscountAmount(),
                assessment.getScholarshipAmount(),
                assessment.getNetAmount(),
                assessment.getPaidAmount(),
                assessment.getRefundedAmount(),
                money(assessment.outstanding()),
                assessment.getCurrency(),
                assessment.getDueDate(),
                assessment.getStatus(),
                assessment.getNotes(),
                assessment.getCreatedAt());
    }

    FinanceDtos.InvoiceResponse toInvoiceResponse(Invoice invoice) {
        List<InvoiceItem> lines = items.findByInvoiceIdOrderByComponentTypeAsc(invoice.getId());
        return new FinanceDtos.InvoiceResponse(
                invoice.getId(),
                invoice.getStudentId(),
                invoice.getAssessmentId(),
                invoice.getInvoiceNumber(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                money(invoice.getTotalAmount().subtract(invoice.getPaidAmount())),
                invoice.getStatus(),
                invoice.getNotes(),
                lines.stream().map(line -> new FinanceDtos.InvoiceItemResponse(
                        line.getId(), line.getDescription(), line.getComponentType(),
                        line.getAmount(), line.isConcession())).toList(),
                invoice.getCreatedAt());
    }
}
