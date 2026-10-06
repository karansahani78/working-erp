package com.educationerp.finance.dto;

import com.educationerp.finance.Discount;
import com.educationerp.finance.FeeComponent;
import com.educationerp.finance.FeeStructure;
import com.educationerp.finance.Fine;
import com.educationerp.finance.Invoice;
import com.educationerp.finance.InvoiceItem;
import com.educationerp.finance.Payment;
import com.educationerp.finance.Refund;
import com.educationerp.finance.Scholarship;
import com.educationerp.finance.StudentConcession;
import com.educationerp.finance.StudentFeeAssessment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record CreateFeeStructure(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 40) String code,
            UUID programId,
            UUID schoolClassId,
            @NotNull UUID academicYearId,
            @NotNull @DecimalMin("0.00") BigDecimal totalAmount,
            @Size(max = 3) String currency,
            @Size(max = 500) String description) {
    }

    public record UpdateFeeStructure(
            @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin("0.00") BigDecimal totalAmount,
            @Size(max = 3) String currency,
            @Size(max = 500) String description) {
    }

    public record FeeComponentRequest(
            @NotNull FeeComponent.ComponentType componentType,
            @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin("0.00") BigDecimal amount,
            Boolean mandatory,
            @Size(max = 300) String description) {
    }

    public record ComponentList(List<FeeComponentRequest> components) {
    }

    public record FeeStructureResponse(
            UUID id,
            String name,
            String code,
            UUID programId,
            UUID schoolClassId,
            UUID academicYearId,
            BigDecimal totalAmount,
            BigDecimal componentTotal,
            boolean componentsMatchTotal,
            String currency,
            FeeStructure.Status status,
            String description,
            List<FeeComponentResponse> components,
            Instant createdAt) {
    }

    public record FeeComponentResponse(
            UUID id,
            FeeComponent.ComponentType componentType,
            String name,
            BigDecimal amount,
            boolean mandatory,
            String description) {
    }

    public record CreateDiscount(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull Discount.ValueType valueType,
            @NotNull @DecimalMin("0.00") BigDecimal value,
            @Size(max = 500) String description) {
    }

    public record CreateScholarship(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull Scholarship.ValueType valueType,
            @NotNull @DecimalMin("0.00") BigDecimal value,
            @Size(max = 500) String description) {
    }

    public record AwardResponse(
            UUID id,
            String code,
            String name,
            Object valueType,
            BigDecimal value,
            String description,
            Object status) {
    }

    public record GrantConcession(
            UUID discountId,
            UUID scholarshipId,
            @NotNull @DecimalMin("0.00") BigDecimal amount,
            @Size(max = 500) String reason) {
    }

    public record ConcessionResponse(
            UUID id,
            UUID studentId,
            UUID discountId,
            UUID scholarshipId,
            UUID academicYearId,
            BigDecimal amount,
            String reason,
            StudentConcession.Status status,
            Instant createdAt) {
    }

    public record AssessFees(
            @NotNull UUID feeStructureId,
            @NotNull LocalDate dueDate,
            @Size(max = 500) String notes) {
    }

    public record FeeAssessmentResponse(
            UUID id,
            UUID studentId,
            UUID feeStructureId,
            UUID academicYearId,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal scholarshipAmount,
            BigDecimal netAmount,
            BigDecimal paidAmount,
            BigDecimal refundedAmount,
            BigDecimal outstandingAmount,
            String currency,
            LocalDate dueDate,
            StudentFeeAssessment.Status status,
            String notes,
            Instant createdAt) {
    }

    public record CreateInvoice(
            @NotNull UUID assessmentId,
            @NotNull LocalDate issueDate,
            LocalDate dueDate,
            @Size(max = 500) String notes) {
    }

    public record InvoiceResponse(
            UUID id,
            UUID studentId,
            UUID assessmentId,
            String invoiceNumber,
            LocalDate issueDate,
            LocalDate dueDate,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal balanceDue,
            Invoice.Status status,
            String notes,
            List<InvoiceItemResponse> items,
            Instant createdAt) {
    }

    /** {@code amount} is always positive; a concession line reduces the total instead of charging. */
    public record InvoiceItemResponse(
            UUID id,
            String description,
            FeeComponent.ComponentType componentType,
            BigDecimal amount,
            boolean concession) {
    }

    public record RecordPayment(
            @NotNull UUID studentId,
            UUID invoiceId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull Payment.Method method,
            Payment.Provider provider,
            @Size(max = 120) String providerTransactionId,
            @Size(max = 120) String idempotencyKey,
            @Size(max = 120) String reference) {
    }

    public record ProviderCallback(
            @NotNull Payment.Provider provider,
            @Size(max = 120) String providerTransactionId,
            @NotNull UUID studentId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull Payment.Status status,
            @Size(max = 300) String message) {
    }

    public record PaymentResponse(
            UUID id,
            UUID studentId,
            UUID invoiceId,
            String receiptNumber,
            BigDecimal amount,
            String currency,
            Payment.Method method,
            Payment.Provider provider,
            String providerTransactionId,
            String idempotencyKey,
            String reference,
            Payment.Status status,
            String failureReason,
            Instant receivedAt,
            Instant confirmedAt) {
    }

    public record RequestRefund(
            @NotNull UUID paymentId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Size(max = 500) String reason,
            Payment.Method method) {
    }

    public record RefundDecision(@NotNull Refund.Status status, @Size(max = 300) String reason) {
    }

    public record RefundResponse(
            UUID id,
            UUID paymentId,
            UUID studentId,
            BigDecimal amount,
            String reason,
            Payment.Method method,
            Refund.Status status,
            UUID requestedBy,
            UUID approvedBy,
            Instant approvedAt,
            Instant processedAt,
            Instant createdAt) {
    }

    public record CreateFine(
            @NotNull UUID studentId,
            @NotBlank @Size(max = 200) String description,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            UUID academicYearId) {
    }

    public record FineResponse(
            UUID id,
            UUID studentId,
            String description,
            BigDecimal amount,
            UUID academicYearId,
            Fine.Status status,
            Instant createdAt) {
    }

    public record ReceivablesRow(
            UUID assessmentId,
            UUID studentId,
            UUID feeStructureId,
            BigDecimal netAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            LocalDate dueDate,
            long daysOverdue,
            StudentFeeAssessment.Status status) {
    }

    public record ReceivablesReport(
            BigDecimal totalOutstanding,
            BigDecimal totalOverdue,
            int assessmentCount,
            List<ReceivablesRow> rows) {
    }

    public record StudentFeeSummary(
            UUID studentId,
            List<FeeAssessmentResponse> assessments,
            List<InvoiceResponse> invoices,
            List<PaymentResponse> payments,
            List<RefundResponse> refunds,
            List<FineResponse> fines,
            BigDecimal totalNet,
            BigDecimal totalPaid,
            BigDecimal totalOutstanding) {
    }
}
