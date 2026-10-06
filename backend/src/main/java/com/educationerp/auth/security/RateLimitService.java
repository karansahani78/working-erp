package com.educationerp.auth.security;

import com.educationerp.auth.config.SecurityProperties;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window in-process rate limiter.
 *
 * A single-instance deployment only needs local state; the interface keeps a distributed
 * implementation (Redis) possible without touching call sites.
 */
@Component
@RequiredArgsConstructor
public class RateLimitService {

    private final SecurityProperties properties;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public void checkLogin(HttpServletRequest request) {
        check("login:" + clientKey(request), properties.getRateLimit().getLoginPerMinute());
    }

    public void checkPasswordReset(HttpServletRequest request) {
        check("pwreset:" + clientKey(request), properties.getRateLimit().getPasswordResetPerMinute());
    }

    public void checkUpload(HttpServletRequest request) {
        check("upload:" + clientKey(request), properties.getRateLimit().getUploadPerMinute());
    }

    public void checkApi(HttpServletRequest request) {
        check("api:" + clientKey(request), properties.getRateLimit().getApiPerMinute());
    }

    private void check(String key, int limit) {
        if (!properties.getRateLimit().isEnabled()) {
            return;
        }
        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.minute() != minute) {
                return new Window(minute, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        if (window.count().get() > limit) {
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        if (windows.size() > 50_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute() < minute - 2);
        }
    }

    public static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private record Window(long minute, AtomicInteger count) {
    }
}
