package com.educationerp.common.error;

import lombok.Getter;

import java.util.Map;

/**
 * Business exception. Carries a stable {@link ErrorCode} plus optional field errors and
 * an optional developer-facing detail that is logged but never returned to the browser.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> fieldErrors;
    private final String userMessage;

    public AppException(ErrorCode code) {
        this(code, code.defaultMessage(), Map.<String, String>of(), null);
    }

    public AppException(ErrorCode code, String userMessage) {
        this(code, userMessage, Map.<String, String>of(), null);
    }

    public AppException(ErrorCode code, String userMessage, Throwable cause) {
        super(code.name() + ": " + userMessage, cause);
        this.code = code;
        this.userMessage = userMessage;
        this.fieldErrors = Map.of();
    }

    public AppException(ErrorCode code, String userMessage, Map<String, String> fieldErrors) {
        this(code, userMessage, fieldErrors, null);
    }

    private AppException(ErrorCode code, String userMessage, Map<String, String> fieldErrors, Throwable cause) {
        super(code.name() + ": " + userMessage, cause);
        this.code = code;
        this.userMessage = userMessage;
        this.fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public static AppException notFound(String what) {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND, what + " could not be found.");
    }

    public static AppException duplicate(String message) {
        return new AppException(ErrorCode.DUPLICATE_RESOURCE, message);
    }

    public static AppException rule(String message) {
        return new AppException(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    public static AppException denied(String message) {
        return new AppException(ErrorCode.ACCESS_DENIED, message);
    }
}
