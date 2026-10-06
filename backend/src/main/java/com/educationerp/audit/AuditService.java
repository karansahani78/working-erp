package com.educationerp.audit;

import com.educationerp.common.error.AppException;
import com.educationerp.common.persistence.AuditorAwareImpl;
import com.educationerp.auth.user.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Writes the audit trail. Called explicitly from application services inside the same
 * transaction as the business change so an audit row can never be lost.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    /** Property names that must never reach the audit log. */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwordHash", "newPassword", "currentPassword", "confirmPassword",
            "token", "refreshToken", "accessToken", "secret", "jwt", "apiKey", "apiSecret",
            "pin", "cvv", "authorization", "cookie");

    private static final int MAX_SNAPSHOT_CHARS = 20_000;

    private final AuditLogRepository repository;
    private final ClientRequestInfo clientRequestInfo;
    private final ObjectMapper objectMapper;
    private final com.educationerp.auth.security.AuthorizationChecker authorization;

    public void record(AuditEvent event) {
        try {
            repository.save(toRow(event));
        } catch (Exception ex) {
            // Audit failures must not roll back a committed business transaction, but they
            // must be loud enough to be detected.
            log.error("Failed to persist audit event action={} entity={}/{}",
                    event.getAction(), event.getEntityType(), event.getEntityId(), ex);
        }
    }

    /**
     * Reading the trail needs its own permission. Writing does not: recording is a
     * consequence of an action that already passed its own authorisation check.
     */
    public void requireAuditRead() {
        authorization.requirePermission("AUDIT_READ");
    }

    /**
     * Filters are applied as individual specifications so an unused filter contributes no
     * parameter at all, rather than a null parameter the database has to type.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> search(UUID actorId,
                                 AuditAction action,
                                 String entityType,
                                 String entityId,
                                 Instant from,
                                 Instant to,
                                 String term,
                                 Pageable pageable) {
        return repository.findAll(specification(actorId, action, entityType, entityId, from, to, term), pageable);
    }

    @Transactional(readOnly = true)
    public AuditLog get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> AppException.notFound("Audit event"));
    }

    /**
     * The action vocabulary with occurrence counts, counted in one grouped query so the
     * filter can show what actually happened without a request per action.
     */
    @Transactional(readOnly = true)
    public AuditController.AuditActionSummary[] actionSummary() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : repository.countByAction()) {
            counts.put(((AuditAction) row[0]).name(), ((Number) row[1]).longValue());
        }
        return Arrays.stream(AuditAction.values())
                .map(action -> new AuditController.AuditActionSummary(
                        action.name(), counts.getOrDefault(action.name(), 0L)))
                .toArray(AuditController.AuditActionSummary[]::new);
    }

    private static Specification<AuditLog> specification(UUID actorId,
                                                          AuditAction action,
                                                          String entityType,
                                                          String entityId,
                                                          Instant from,
                                                          Instant to,
                                                          String term) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actorId != null) {
                predicates.add(builder.equal(root.get("actorId"), actorId));
            }
            if (action != null) {
                predicates.add(builder.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(builder.equal(builder.lower(root.get("entityType")), entityType.toLowerCase()));
            }
            if (entityId != null && !entityId.isBlank()) {
                predicates.add(builder.equal(root.get("entityId"), entityId));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (term != null && !term.isBlank()) {
                String pattern = "%" + term.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("summary")), pattern),
                        builder.like(builder.lower(root.get("actorUsername")), pattern)));
            }
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Records an event that must be stored even when the surrounding transaction rolls
     * back (for example a rejected login or an unauthorized attempt).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(AuditEvent event) {
        try {
            repository.save(toRow(event));
        } catch (Exception ex) {
            log.error("Failed to persist independent audit event action={} entity={}",
                    event.getAction(), event.getEntityType(), ex);
        }
    }

    private AuditLog toRow(AuditEvent event) {
        AuditLog row = new AuditLog();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser principal) {
            row.setActorId(principal.userId());
            row.setActorUsername(principal.username());
        }
        row.setAction(event.getAction());
        row.setEntityType(event.getEntityType());
        row.setEntityId(event.getEntityId());
        row.setEntityLabel(event.getEntityLabel());
        row.setSummary(event.getSummary());
        row.setBeforeState(snapshot(event.getBefore()));
        row.setAfterState(snapshot(event.getAfter()));
        row.setModule(event.getModule());
        row.setSucceeded(event.isSucceeded());
        row.setFailureReason(event.getFailureReason());
        row.setIpAddress(clientRequestInfo.ipAddress());
        row.setUserAgent(clientRequestInfo.userAgent());
        row.setRequestId(clientRequestInfo.requestId());
        return row;
    }

    private String snapshot(Object value) {
        if (value == null) {
            return null;
        }
        String json = toJson(value);
        if (json == null) {
            return null;
        }
        return json.length() > MAX_SNAPSHOT_CHARS
                ? json.substring(0, MAX_SNAPSHOT_CHARS) + "…"
                : json;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return scrub(objectMapper.copy()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsString(value));
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize audit snapshot of type {}", value.getClass().getName(), ex);
            return null;
        }
    }

    /** Defence in depth: the snapshot writer already filters, this catches anything else. */
    private String scrub(String json) {
        if (json == null) {
            return null;
        }
        String result = json;
        for (String key : SENSITIVE_KEYS) {
            result = result.replaceAll("(?i)\"" + key + "\"\\s*:\\s*\"[^\"]*\"", "\"" + key + "\":\"[redacted]\"");
        }
        return result;
    }

    public static UUID systemActor() {
        return null;
    }
}