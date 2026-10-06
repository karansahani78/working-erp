package com.educationerp.audit;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * Immutable description of one auditable action.
 */
@Getter
@Builder
@ToString(of = {"action", "entityType", "entityId"})
public class AuditEvent {

    private final AuditAction action;
    private final String entityType;
    private final String entityId;
    private final String entityLabel;
    private final String summary;
    private final Object before;
    private final Object after;
    private final String module;
    private final Map<String, Object> metadata;
    private final boolean succeeded;
    private final String failureReason;
}
