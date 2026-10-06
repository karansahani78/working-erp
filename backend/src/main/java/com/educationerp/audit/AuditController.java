package com.educationerp.audit;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import com.educationerp.common.error.AppException;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only access to the audit trail.
 *
 * <p>The trail is append-only by construction: this controller exposes no write, update or
 * delete mapping at all, so nothing reachable over HTTP can alter history.
 */
@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService service;
    private final InstitutionService institutions;

    @GetMapping("/events")
    public ApiResponse<PageResponse<AuditEventRow>> search(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {

        institutions.requireModuleEnabled(ModuleKey.AUDIT);
        service.requireAuditRead();

        AuditAction parsedAction = null;
        if (action != null && !action.isBlank()) {
            try {
                parsedAction = AuditAction.valueOf(action.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw AppException.rule("Unknown action: " + action);
            }
        }

        String lowered = term == null || term.isBlank() ? null : "%" + term.trim().toLowerCase() + "%";
        String loweredEntity = entityType == null || entityType.isBlank() ? null : entityType.trim();

        return ApiResponse.ok(PageResponse.from(service.search(
                actorId, parsedAction, loweredEntity,
                entityId == null || entityId.isBlank() ? null : entityId.trim(),
                from, to, lowered, pageable).map(AuditController::toRow)));
    }

    /** A single event, for the detail drawer. Snapshots are returned as stored JSON text. */
    @GetMapping("/events/{id}")
    public ApiResponse<AuditEventRow> get(@PathVariable UUID id) {
        institutions.requireModuleEnabled(ModuleKey.AUDIT);
        service.requireAuditRead();
        return ApiResponse.ok(toRow(service.get(id)));
    }

    @GetMapping("/actions")
    public ApiResponse<AuditActionSummary[]> actions() {
        institutions.requireModuleEnabled(ModuleKey.AUDIT);
        service.requireAuditRead();
        return ApiResponse.ok(service.actionSummary());
    }

    private static AuditEventRow toRow(AuditLog row) {
        return new AuditEventRow(
                row.getId(),
                row.getActorId(),
                row.getActorUsername(),
                row.getAction().name(),
                row.getEntityType(),
                row.getEntityId(),
                row.getEntityLabel(),
                row.getSummary(),
                row.getBeforeState(),
                row.getAfterState(),
                row.getModule(),
                row.isSucceeded(),
                row.getFailureReason(),
                row.getIpAddress(),
                row.getRequestId(),
                row.getCreatedAt());
    }

    public record AuditEventRow(
            UUID id,
            UUID actorId,
            String actorUsername,
            String action,
            String entityType,
            String entityId,
            String entityLabel,
            String summary,
            String beforeState,
            String afterState,
            String module,
            boolean succeeded,
            String failureReason,
            String ipAddress,
            String requestId,
            Instant occurredAt) {
    }

    public record AuditActionSummary(String action, long occurrences) {
    }
}