package com.educationerp.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Wire format for every error response.
 *
 * <pre>
 * {
 *   "timestamp": "...",
 *   "status": 400,
 *   "code": "VALIDATION_ERROR",
 *   "message": "Please correct the highlighted fields.",
 *   "fieldErrors": { "email": "Invalid email address." },
 *   "path": "/api/v1/students"
 * }
 * </pre>
 *
 * Internal exception text is never exposed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors,
        String path,
        String requestId
) {
}
