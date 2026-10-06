package com.educationerp.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a correlation id to every request, echoes it back to the caller and records
 * request duration for the log pipeline.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger accessLog = LoggerFactory.getLogger("com.educationerp.web.access");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(RequestContext.HEADER);
        String requestId = (incoming == null || incoming.isBlank() || incoming.length() > 64)
                ? UUID.randomUUID().toString()
                : incoming;
        RequestContext.set(requestId);
        response.setHeader(RequestContext.HEADER, requestId);

        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - startNanos) / 1_000_000L;
            String user = request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
            MDC.put("durationMs", String.valueOf(millis));
            MDC.put("method", request.getMethod());
            MDC.put("uri", request.getRequestURI());
            if (response.getStatus() >= 500) {
                accessLog.error("status={} durationMs={} user={}", response.getStatus(), millis, user);
            } else {
                accessLog.debug("status={} durationMs={} user={}", response.getStatus(), millis, user);
            }
            RequestContext.clear();
        }
    }
}
