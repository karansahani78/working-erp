package com.educationerp.finance.payment;

import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.finance.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shared behaviour for the hosted payment providers (eSewa, Khalti, FonePay).
 *
 * <p>A callback body is treated as a hint, never as the truth. The handler re-reads the
 * transaction from the provider before any money moves, because a webhook arrives over an
 * unauthenticated channel and a payload claiming "paid 50,000" is otherwise just a
 * request to be paid 50,000. Where a provider does sign its callbacks the signature is
 * checked first; the server-to-server read is the backstop for the ones that do not.
 *
 * <p>Each provider differs only in its endpoints and payload shape, so subclasses supply
 * those and inherit initiation, verification and signature handling.
 */
@Slf4j
public abstract class PspPaymentGateway implements PaymentGateway {

    protected final RestClient http;
    protected final String merchantId;
    protected final String secretKey;
    protected final String initiatePath;
    protected final String verifyPath;

    protected PspPaymentGateway(RestClient http, String merchantId, String secretKey,
                                String initiatePath, String verifyPath) {
        this.http = http;
        this.merchantId = merchantId;
        this.secretKey = secretKey;
        this.initiatePath = initiatePath;
        this.verifyPath = verifyPath;
    }

    @Override
    public String initiate(PaymentRequest request) {
        if (!StringUtils.hasText(merchantId) || !StringUtils.hasText(secretKey)) {
            // Failing loudly beats silently queueing a payment that can never be confirmed.
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE,
                    provider() + " is not configured for this institution.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchant_id", merchantId);
        body.put("amount", request.amount());
        body.put("currency", request.currency());
        body.put("reference", request.description());
        body.putAll(extraFields(request));

        Map<?, ?> response = http.post()
                .uri(initiatePath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        String transactionId = transactionIdOf(response);
        if (!StringUtils.hasText(transactionId)) {
            throw new AppException(ErrorCode.PAYMENT_FAILED,
                    provider() + " did not return a transaction reference.");
        }
        return transactionId;
    }

    @Override
    public ProviderPayment verify(String providerTransactionId) {
        if (!StringUtils.hasText(providerTransactionId)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "A provider transaction reference is required to verify a payment.");
        }
        try {
            Map<?, ?> response = http.get()
                    .uri(uriBuilder -> uriBuilder.path(verifyPath)
                            .queryParam("merchant_id", merchantId)
                            .queryParam("transaction_id", providerTransactionId)
                            .build())
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw new AppException(ErrorCode.PAYMENT_FAILED,
                        provider() + " returned no record for this transaction.");
            }
            return new ProviderPayment(
                    textOf(response, providerTransactionId, "transaction_id", "transactionId", "reference"),
                    decimalOf(response.get("amount")),
                    textOf(response, "NPR", "currency"),
                    statusOf(response),
                    textOf(response, "", "message", "status_message"));
        } catch (AppException appException) {
            throw appException;
        } catch (RuntimeException failure) {
            log.warn("Could not read {} transaction {} back from the provider", provider(), providerTransactionId,
                    failure);
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE,
                    "The payment provider could not be reached to verify this payment.");
        }
    }

    @Override
    public boolean verifyCallback(String payload, String signature) {
        if (!StringUtils.hasText(secretKey)) {
            // No configured secret means we cannot attribute the callback, so we do not
            // accept it. The caller falls back to a server-to-server verification.
            return false;
        }
        if (!StringUtils.hasText(signature) || !StringUtils.hasText(payload)) {
            return false;
        }
        return MessageDigest.isEqual(expectedSignature(payload).getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8));
    }

    /** HMAC-SHA256 over the raw payload, hex encoded, as sent by the provider. */
    protected String expectedSignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not compute the expected callback signature", failure);
        }
    }

    /** Provider-specific fields merged into the initiation body. */
    protected Map<String, Object> extraFields(PaymentRequest request) {
        return Map.of();
    }

    protected String transactionIdOf(Map<?, ?> response) {
        if (response == null) {
            return null;
        }
        for (String key : new String[]{"transaction_id", "transactionId", "reference", "token"}) {
            Object value = response.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    /**
     * Only {@code CONFIRMED} from the provider's own status field counts as paid. A
     * missing, unknown or pending status is treated as not paid, so an unrecognised value
     * from a provider cannot be read as success.
     */
    protected Payment.Status statusOf(Map<?, ?> response) {
        Object raw = response.containsKey("status") ? response.get("status") : response.get("state");
        if (raw == null) {
            return Payment.Status.PENDING;
        }
        String value = String.valueOf(raw).trim().toUpperCase(java.util.Locale.ROOT);
        return switch (value) {
            case "COMPLETE", "COMPLETED", "SUCCESS", "SUCCESSFUL", "CAPTURED", "PAID", "VERIFIED" ->
                    Payment.Status.CONFIRMED;
            case "FAIL", "FAILED", "FAILED_REFUND", "CANCELLED", "CANCELED", "EXPIRED", "DECLINED" ->
                    Payment.Status.FAILED;
            case "REFUNDED" -> Payment.Status.REFUNDED;
            case "PARTIALLY_REFUNDED" -> Payment.Status.PARTIALLY_REFUNDED;
            default -> Payment.Status.PENDING;
        };
    }

    /** First present key wins, otherwise the fallback. Avoids {@code Map<?,?>getOrDefault}, which cannot type-check. */
    protected String textOf(Map<?, ?> source, String fallback, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return fallback;
    }

    protected BigDecimal decimalOf(Object raw) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (NumberFormatException notANumber) {
            return BigDecimal.ZERO;
        }
    }

    /** Reference the student quotes back on the gateway, used to find our own record. */
    protected String describe(PaymentRequest request) {
        return request.description() == null ? "Fee payment " + request.studentId() : request.description();
    }

    protected UUID studentOf(PaymentRequest request) {
        return request.studentId();
    }
}
