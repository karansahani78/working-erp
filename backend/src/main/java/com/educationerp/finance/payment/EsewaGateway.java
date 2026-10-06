package com.educationerp.finance.payment;

import com.educationerp.finance.Payment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * eSewa. Registered only when the institution has supplied credentials, so an institution
 * that does not accept eSewa never exposes the endpoints at all.
 */
@Component
@ConditionalOnProperty(prefix = "payments.esewa", name = "enabled", havingValue = "true")
public class EsewaGateway extends PspPaymentGateway {

    public EsewaGateway(GatewayConfig config) {
        super(config.getEsewa().client(), config.getEsewa().getMerchantId(), config.getEsewa().getSecretKey(),
                config.getEsewa().getInitiatePath(), config.getEsewa().getVerifyPath());
    }

    @Override
    public Payment.Provider provider() {
        return Payment.Provider.ESEWA;
    }

    @Override
    protected Map<String, Object> extraFields(PaymentRequest request) {
        return Map.of("success_url", "", "failure_url", "");
    }
}
