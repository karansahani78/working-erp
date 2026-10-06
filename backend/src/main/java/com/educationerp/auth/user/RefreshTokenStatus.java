package com.educationerp.auth.user;

public enum RefreshTokenStatus {
    ACTIVE,
    ROTATED,
    REVOKED,
    COMPROMISED,
    EXPIRED
}
