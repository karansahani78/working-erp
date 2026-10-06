package com.educationerp.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "erp.security")
public class SecurityProperties {

    @Valid
    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final RateLimit rateLimit = new RateLimit();
    private final Password password = new Password();

    private int maxFailedAttempts = 5;
    private Duration lockDuration = Duration.ofMinutes(15);

    public Jwt getJwt() {
        return jwt;
    }

    public Cors getCors() {
        return cors;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Password getPassword() {
        return password;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }

    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }

    public static class Jwt {
        @NotBlank
        private String secret;
        private String issuer = "education-erp";
        @NotNull
        private Duration accessTokenTtl = Duration.ofMinutes(30);
        @NotNull
        private Duration refreshTokenTtl = Duration.ofDays(7);

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int loginPerMinute = 10;
        private int passwordResetPerMinute = 5;
        private int apiPerMinute = 600;
        private int uploadPerMinute = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getLoginPerMinute() {
            return loginPerMinute;
        }

        public void setLoginPerMinute(int loginPerMinute) {
            this.loginPerMinute = loginPerMinute;
        }

        public int getPasswordResetPerMinute() {
            return passwordResetPerMinute;
        }

        public void setPasswordResetPerMinute(int passwordResetPerMinute) {
            this.passwordResetPerMinute = passwordResetPerMinute;
        }

        public int getApiPerMinute() {
            return apiPerMinute;
        }

        public void setApiPerMinute(int apiPerMinute) {
            this.apiPerMinute = apiPerMinute;
        }

        public int getUploadPerMinute() {
            return uploadPerMinute;
        }

        public void setUploadPerMinute(int uploadPerMinute) {
            this.uploadPerMinute = uploadPerMinute;
        }
    }

    public static class Password {
        private int minLength = 10;

        public int getMinLength() {
            return minLength;
        }

        public void setMinLength(int minLength) {
            this.minLength = minLength;
        }
    }
}
