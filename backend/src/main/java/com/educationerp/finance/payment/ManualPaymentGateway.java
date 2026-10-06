package com.educationerp.finance.payment;

import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.finance.Payment;
import org.springframework.stereotype.Component;

/**
 * Cash, cheques and bank transfers: the money arrives outside the system, so there is
 * nothing to initiate and nothing to verify. An administrator confirms the payment
 * manually, which is why {@code initiate} is never called for these methods.
 */
@Component
public class ManualPaymentGateway implements PaymentGateway {

    @Override
    public Payment.Provider provider() {
        return Payment.Provider.MANUAL;
    }

    @Override
    public String initiate(PaymentRequest request) {
        throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                "Cash, cheques and bank transfers are confirmed manually and have no provider reference.");
    }

    @Override
    public ProviderPayment verify(String providerTransactionId) {
        throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                "A manual payment has no provider record to verify.");
    }
}
