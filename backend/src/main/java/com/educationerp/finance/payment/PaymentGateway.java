package com.educationerp.finance.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The seam between the finance module and a payment provider.
 *
 * <p>Separating the gateway from the payment service is what keeps PSP coupling out of the
 * money path: the service owns the ledger, the receipt number and the idempotency rules,
 * while a gateway only knows how to start a charge and read back its outcome. Providers
 * that send a webhook implement {@link #verifyCallback}; those that do not, such as cash
 * or a bank transfer, are never asked to.
 */
public interface PaymentGateway {

    com.educationerp.finance.Payment.Provider provider();

    /**
     * Initiates a charge and returns the provider's transaction reference, which is stored
     * against the pending payment so a later callback can be matched to it.
     */
    String initiate(PaymentRequest request);

    /**
     * Re-reads the provider's own record for this transaction.
     *
     * @return the provider's view of the payment
     */
    ProviderPayment verify(String providerTransactionId);

    /**
     * Confirms that a webhook really came from the provider. Implementations must verify
     * the provider's signature over the raw payload; an unverifiable callback is rejected
     * rather than trusted.
     */
    default boolean verifyCallback(String payload, String signature) {
        throw new UnsupportedOperationException(provider() + " does not deliver webhooks.");
    }

    record PaymentRequest(UUID studentId, BigDecimal amount, String currency, String description) {
    }

    record ProviderPayment(String providerTransactionId, BigDecimal amount, String currency,
                           com.educationerp.finance.Payment.Status status, String message) {
    }
}
