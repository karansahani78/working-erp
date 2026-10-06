package com.educationerp.audit;

/**
 * Audit action vocabulary from the specification.
 */
public enum AuditAction {

    LOGIN,
    LOGOUT,
    LOGIN_FAILED,
    CREATE,
    READ,
    UPDATE,
    DELETE,
    APPROVE,
    REJECT,
    PUBLISH,
    POST,
    REVERSE,
    PAYMENT,
    REFUND,
    RESULT_CHANGE,
    PERMISSION_CHANGE,
    PASSWORD_CHANGE,
    SECURITY_EVENT,
    SETUP,
    IMPORT,
    EXPORT,
    CORRECTION,
    CONFIG_CHANGE
}
