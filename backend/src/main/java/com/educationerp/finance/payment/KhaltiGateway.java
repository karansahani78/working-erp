package com.educationerp.finance.payment;

import com.educationerp.finance.Payment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Khalti. Registered only when the institution has supplied credentials, so an institution
 * that does not accept Khalti never exposes the endpoints at all.
 */
@Component
@ConditionalOnProperty(prefix = "payments.khalti", name = "enabled", havingValue = "true")
public class KhaltiGateway extends PspPaymentGateway {

    public KhaltiGateway(GatewayConfig config) {
        super(config.getKhalti().client(), config.getKhalti().getMerchantId(), config.getKhalti().getSecretKey(),
                config.getKhalti().getInitiatePath(), config.getKhalti().getVerifyPath());
    }

    @Override
    public Payment.Provider provider() {
        return Payment.Provider.KHALTI;
    }

    @Override
    protected Map<String, Object> extraFields(PaymentRequest request) {
        return Map.of("return_url", "");
    }
}
