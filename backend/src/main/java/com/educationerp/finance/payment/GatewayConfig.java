package com.educationerp.finance.payment;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.client.RestClient;

/**
 * Provider credentials and endpoints, read from configuration rather than compiled in.
 * Each gateway is only registered when its merchant id and secret are present, so an
 * uninstalled provider is absent from the map instead of present and failing at call time.
 */
@ConfigurationProperties(prefix = "payments")
@Getter
public class GatewayConfig {

    private Provider esewa = new Provider();
    private Provider khalti = new Provider();
    private Provider fonepay = new Provider();

    @Getter
    public static class Provider {
        private boolean enabled;
        private String baseUrl = "";
        private String merchantId = "";
        private String secretKey = "";
        private String initiatePath = "";
        private String verifyPath = "";

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public void setMerchantId(String merchantId) {
            this.merchantId = merchantId;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public void setInitiatePath(String initiatePath) {
            this.initiatePath = initiatePath;
        }

        public void setVerifyPath(String verifyPath) {
            this.verifyPath = verifyPath;
        }

        public boolean isConfigured() {
            return enabled && merchantId != null && !merchantId.isBlank()
                    && secretKey != null && !secretKey.isBlank()
                    && baseUrl != null && !baseUrl.isBlank();
        }

        public RestClient client() {
            return RestClient.builder().baseUrl(baseUrl).build();
        }
    }

}
