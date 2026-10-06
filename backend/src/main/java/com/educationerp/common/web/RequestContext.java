package com.educationerp.common.web;

import org.slf4j.MDC;

/**
 * Per-request correlation id, surfaced in logs, responses and audit rows.
 */
public final class RequestContext {

    public static final String REQUEST_ID = "requestId";
    public static final String HEADER = "X-Request-Id";

    private static final ThreadLocal<String> REQUEST_ID_HOLDER = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void set(String requestId) {
        REQUEST_ID_HOLDER.set(requestId);
        MDC.put(REQUEST_ID, requestId);
    }

    public static String getRequestId() {
        return REQUEST_ID_HOLDER.get();
    }

    public static void clear() {
        REQUEST_ID_HOLDER.remove();
        MDC.remove(REQUEST_ID);
    }
}
