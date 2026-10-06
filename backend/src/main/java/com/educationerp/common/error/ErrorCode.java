package com.educationerp.common.error;

/**
 * Stable, machine-readable error codes. The frontend maps these to human readable
 * copy; developers never see raw exception text (see {@code ErrorResponse}).
 */
public enum ErrorCode {

    VALIDATION_ERROR(400, "Please correct the highlighted fields."),
    BAD_REQUEST(400, "The request could not be processed."),
    MALFORMED_JSON(400, "The request body could not be read."),
    MISSING_PARAMETER(400, "A required value is missing."),
    TYPE_MISMATCH(400, "A value has the wrong format."),

    AUTHENTICATION_REQUIRED(401, "Please sign in to continue."),
    INVALID_CREDENTIALS(401, "The login ID or password is incorrect."),
    ACCOUNT_LOCKED(423, "This account is temporarily locked. Please try again later."),
    ACCOUNT_INACTIVE(403, "This account is not active. Please contact the administrator."),
    INVALID_TOKEN(401, "Your session is no longer valid. Please sign in again."),
    EMAIL_NOT_VERIFIED(403, "Please verify your email address first."),
    MFA_REQUIRED(401, "Additional verification is required."),

    ACCESS_DENIED(403, "You do not have permission to perform this action."),
    MODULE_DISABLED(403, "This module is not enabled for this institution."),

    RESOURCE_NOT_FOUND(404, "The requested record could not be found."),
    DUPLICATE_RESOURCE(409, "A record with these details already exists."),
    BUSINESS_RULE_VIOLATION(422, "This action is not allowed at this time."),
    INVALID_STATE_TRANSITION(409, "This record cannot move to the requested state."),
    CONFLICTING_OPERATION(409, "Another operation is in progress for this record."),
    OPTIMISTIC_LOCK_FAILURE(409, "This record was modified by someone else. Please reload and try again."),

    RESULT_ALREADY_PUBLISHED(409, "These results have already been published."),
    RESULT_LOCKED(409, "This result is locked and cannot be modified."),
    PAYMENT_FAILED(402, "The payment could not be completed."),
    PAYMENT_ALREADY_PROCESSED(409, "This payment has already been processed."),
    REFUND_NOT_ALLOWED(422, "A refund cannot be created for this payment."),

    SETUP_ALREADY_COMPLETED(409, "Initial setup has already been completed."),
    SETUP_NOT_AVAILABLE(403, "Initial setup is not available on this installation."),

    RATE_LIMIT_EXCEEDED(429, "Too many requests. Please wait a moment and try again."),
    FILE_TOO_LARGE(413, "The uploaded file is too large."),
    UNSUPPORTED_MEDIA_TYPE(415, "This file type is not supported."),

    INTERNAL_ERROR(500, "Something went wrong on our side. Please try again."),
    SERVICE_UNAVAILABLE(503, "The service is temporarily unavailable.");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
