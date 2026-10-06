package com.educationerp.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standard success envelope for every API response.
 *
 * <pre>
 * { "data": ..., "message": "Success" }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, "Success");
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(data, message);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(null, "Success");
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(null, message);
    }

    /**
     * Envelope carrying client-side cache metadata. The server timestamp lets the
     * frontend skip redundant public branding requests.
     */
    public record Branded<T>(T data, String message, Instant servedAt) {

        public static <T> Branded<T> of(T data) {
            return new Branded<>(data, "Success", Instant.now());
        }
    }
}
