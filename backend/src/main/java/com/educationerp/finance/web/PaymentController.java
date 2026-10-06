package com.educationerp.finance.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.educationerp.finance.Payment;
import com.educationerp.finance.dto.FinanceDtos;
import com.educationerp.finance.service.FeeAssessmentService;
import com.educationerp.finance.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Invoicing, payments, refunds and fines. */
@RestController
@RequestMapping("/api/v1/finance")
@Tag(name = "Finance")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService payments;
    private final FeeAssessmentService invoices;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------- invoices

    @GetMapping("/invoices/{id}")
    public ApiResponse<FinanceDtos.InvoiceResponse> getInvoice(@PathVariable UUID id) {
        return ApiResponse.ok(invoices.getInvoice(id));
    }

    @GetMapping("/students/{studentId}/invoices")
    public ApiResponse<List<FinanceDtos.InvoiceResponse>> listInvoices(@PathVariable UUID studentId) {
        return ApiResponse.ok(invoices.listInvoices(studentId));
    }

    @PostMapping("/students/{studentId}/invoices")
    public ApiResponse<FinanceDtos.InvoiceResponse> createInvoice(
            @PathVariable UUID studentId, @Valid @RequestBody FinanceDtos.CreateInvoice request) {
        return ApiResponse.ok(invoices.createInvoice(studentId, request));
    }

    @PostMapping("/invoices/{id}/issue")
    public ApiResponse<FinanceDtos.InvoiceResponse> issue(@PathVariable UUID id) {
        return ApiResponse.ok(invoices.issueInvoice(id));
    }

    @DeleteMapping("/invoices/{id}")
    public ApiResponse<FinanceDtos.InvoiceResponse> cancel(@PathVariable UUID id,
                                                           @RequestParam(required = false) String reason) {
        return ApiResponse.ok(invoices.cancelInvoice(id, reason));
    }

    // -------------------------------------------------------------------- payments

    @GetMapping("/payments/{id}")
    public ApiResponse<FinanceDtos.PaymentResponse> getPayment(@PathVariable UUID id) {
        return ApiResponse.ok(payments.getPayment(id));
    }

    @GetMapping("/students/{studentId}/payments")
    public ApiResponse<List<FinanceDtos.PaymentResponse>> listPayments(@PathVariable UUID studentId) {
        return ApiResponse.ok(payments.listPayments(studentId));
    }

    @PostMapping("/payments")
    public ApiResponse<FinanceDtos.PaymentResponse> record(@Valid @RequestBody FinanceDtos.RecordPayment request) {
        return ApiResponse.ok(payments.record(request));
    }

    /**
     * Provider webhook. The raw body and signature are passed through so the signature can
     * be checked over the exact bytes the provider signed, and the amount and status are
     * re-read from the provider rather than taken from this payload.
     */
    @PostMapping("/payments/callback/{provider}")
    public ApiResponse<FinanceDtos.PaymentResponse> callback(
            @PathVariable Payment.Provider provider,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String rawBody) throws JsonProcessingException {
        FinanceDtos.ProviderCallback callback = objectMapper.readValue(rawBody, FinanceDtos.ProviderCallback.class);
        if (callback.provider() != provider) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The callback body does not match the provider in the URL.");
        }
        // The raw body is handed to the gateway untouched: a signature is only meaningful
        // over the exact bytes the provider signed.
        return ApiResponse.ok(payments.handleCallback(callback, rawBody, signature));
    }

    @PostMapping("/payments/{id}/confirm")
    public ApiResponse<FinanceDtos.PaymentResponse> confirm(@PathVariable UUID id) {
        return ApiResponse.ok(payments.confirmManually(id));
    }

    @PostMapping("/payments/{id}/fail")
    public ApiResponse<FinanceDtos.PaymentResponse> fail(@PathVariable UUID id,
                                                         @RequestParam(required = false) String reason) {
        return ApiResponse.ok(payments.fail(id, reason));
    }

    // --------------------------------------------------------------------- refunds

    @GetMapping("/students/{studentId}/refunds")
    public ApiResponse<List<FinanceDtos.RefundResponse>> listRefunds(@PathVariable UUID studentId) {
        return ApiResponse.ok(payments.listRefunds(studentId));
    }

    @PostMapping("/students/{studentId}/refunds")
    public ApiResponse<FinanceDtos.RefundResponse> requestRefund(
            @PathVariable UUID studentId, @Valid @RequestBody FinanceDtos.RequestRefund request) {
        return ApiResponse.ok(payments.requestRefund(studentId, request));
    }

    @PostMapping("/refunds/{id}/decision")
    public ApiResponse<FinanceDtos.RefundResponse> decideRefund(@PathVariable UUID id,
                                                                @Valid @RequestBody FinanceDtos.RefundDecision request) {
        return ApiResponse.ok(payments.decideRefund(id, request));
    }

    // ----------------------------------------------------------------------- fines

    @GetMapping("/students/{studentId}/fines")
    public ApiResponse<List<FinanceDtos.FineResponse>> listFines(@PathVariable UUID studentId) {
        return ApiResponse.ok(payments.listFines(studentId));
    }

    @PostMapping("/fines")
    public ApiResponse<FinanceDtos.FineResponse> createFine(@Valid @RequestBody FinanceDtos.CreateFine request) {
        return ApiResponse.ok(payments.createFine(request));
    }

    @PostMapping("/fines/{id}/waive")
    public ApiResponse<FinanceDtos.FineResponse> waiveFine(@PathVariable UUID id) {
        return ApiResponse.ok(payments.waiveFine(id));
    }
}
