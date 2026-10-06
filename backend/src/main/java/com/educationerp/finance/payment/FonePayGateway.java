package com.educationerp.finance.payment;

import com.educationerp.finance.Payment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * FonePay. Registered only when the institution has supplied credentials, so an institution
 * that does not accept FonePay never exposes the endpoints at all.
 */
@Component
@ConditionalOnProperty(prefix = "payments.fonepay", name = "enabled", havingValue = "true")
public class FonePayGateway extends PspPaymentGateway {

    public FonePayGateway(GatewayConfig config) {
        super(config.getFonepay().client(), config.getFonepay().getMerchantId(), config.getFonepay().getSecretKey(),
                config.getFonepay().getInitiatePath(), config.getFonepay().getVerifyPath());
    }

    @Override
    public Payment.Provider provider() {
        return Payment.Provider.FONEPAY;
    }

    @Override
    protected Map<String, Object> extraFields(PaymentRequest request) {
        return Map.of("return_url", "", "merchant_order_id", describe(request));
    }
}
