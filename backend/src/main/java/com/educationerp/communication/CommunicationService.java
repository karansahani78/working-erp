package com.educationerp.communication;

import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.role.Role;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.auth.user.AuthenticatedUser;
import com.educationerp.auth.user.UserRepository;
import com.educationerp.auth.user.UserStatus;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.common.api.PageResponse;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import com.educationerp.student.GuardianRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Notices, templates and the inbox that reads them.
 *
 * <p>The rule this class exists to enforce is that wording lives in templates, not in code. A
 * caller hands over an event, a list of people and a bag of values; what actually arrives is
 * decided by whatever template is active for that event. That is why a missing template is not
 * an error in the caller — a payroll run or a payment must not fail because a message was not
 * configured — but it is recorded, so the gap is visible.
 */
@Service
@RequiredArgsConstructor
public class CommunicationService {

    /** How many unread messages a batched inbox write handles at a time. */
    private static final int BATCH = 200;

    private final NotificationTemplateRepository templates;
    private final NoticeRepository notices;
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final GuardianRepository guardians;
    private final AuthorizationChecker auth;
    private final AuditService audit;
    private final InstitutionService institutions;

    // --------------------------------------------------------------- templates

    @Transactional(readOnly = true)
    public List<CommunicationDtos.TemplateResponse> listTemplates() {
        institutions.requireModuleEnabled(ModuleKey.COMMUNICATION);
        auth.requirePermission("TEMPLATE_READ");
        return templates.findAllByOrderByEventCodeAscChannelAsc().stream()
                .map(this::toTemplateResponse)
                .toList();
    }

    @Transactional
    public CommunicationDtos.TemplateResponse createTemplate(
            CommunicationDtos.TemplateRequest request) {
        institutions.requireModuleEnabled(ModuleKey.COMMUNICATION);
        auth.requirePermission("TEMPLATE_MANAGE");
        String code = normaliseCode(request.code());
        if (templates.existsByCodeIgnoreCase(code)) {
            throw AppException.duplicate("A template with this code already exists.");
        }
        if (templates.existsByEventCodeAndChannel(request.eventCode(), request.channel())) {
            // Two live templates for one event would leave it to chance which one is used.
            throw AppException.duplicate("This event already has a template for that channel.");
        }
        NotificationTemplate template = NotificationTemplate.of(
                code,
                request.name().trim(),
                request.channel(),
                eventOf(request.eventCode()),
                request.subjectTemplate().trim(),
                request.bodyTemplate().trim());
        if (request.active() != null) {
            template.setActive(request.active());
        }
        return toTemplateResponse(templates.save(template));
    }

    @Transactional
    public CommunicationDtos.TemplateResponse updateTemplate(UUID id,
            CommunicationDtos.TemplateRequest request) {
        institutions.requireModuleEnabled(ModuleKey.COMMUNICATION);
        auth.requirePermission("TEMPLATE_MANAGE");
        NotificationTemplate template = requireTemplate(id);
        String code = normaliseCode(request.code());
        Optional<NotificationTemplate> clash = templates.findByCodeIgnoreCase(code);
        if (clash.isPresent() && !clash.get().getId().equals(id)) {
            throw AppException.duplicate("A template with this code already exists.");
        }
        String eventCode = eventOf(request.eventCode()).code();
        Optional<NotificationTemplate> sameEvent =
                templates.findByEventCodeAndChannel(eventCode, request.channel());
        if (sameEvent.isPresent() && !sameEvent.get().getId().equals(id)) {
            throw AppException.duplicate("This event already has a template for that channel.");
        }
        template.setCode(code);
        template.setName(request.name().trim());
        template.setChannel(request.channel());
        template.setEventCode(eventCode);
        template.setSubjectTemplate(request.subjectTemplate().trim());
        template.setBodyTemplate(request.bodyTemplate().trim());
        if (request.active() != null) {
            template.setActive(request.active());
        }
        return toTemplateResponse(templates.save(template));
    }

    // ------------------------------------------------------------------ notices

    @Transactional(readOnly = true)
    public PageResponse<CommunicationDtos.NoticeResponse> listNotices(
            Notice.Status status, Pageable pageable) {
        institutions.requireModuleEnabled(ModuleKey.COMMUNICATION);
        auth.requirePermission("NOTICE_READ");
        Page<Notice> page = status == null
                ? notices.findAllByOrderByCreatedAtDesc(pageable)
                : notices.findByStatusOrderByPublishedAtDesc(status, pageable);
        return PageResponse.from(page, this::toNoticeResponse);
    }

    @Transactional
    public CommunicationDtos.NoticeResponse createNotice(CommunicationDtos.NoticeRequest request) {
        institutions.requireModuleEnabled(ModuleKey.COMMUNICATION);
        auth.requirePermission("NOTICE_CREATE");
        Notice notice = new Notice();
        notice.setTitle(request.title().trim());
        notice.setBody(request.body().trim());
        notice.setAudience(request.audience());
        notice.setExpiresAt(request.expiresAt());
        Notice saved = notices.save(notice);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("Notice")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getTitle())
                .summary("Drafted a notice for " + saved.getAudience())
                .module(ModuleKey.COMMUNICATION.name())
                .succeeded(true)
                .build());
        return toNoticeResponse(saved);
    }

    /**
     * Publishes a notice to everyone in its audience. This is the step that actually talks to
     * people, so it is the step that is audited and counted.
     */
    @Transactional
    public CommunicationDtos.DispatchSummary publishNotice(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.COMMUNICATION);
        auth.requirePermission("NOTICE_PUBLISH");
        Notice notice = requireNotice(id);
        if (notice.getStatus() == Notice.Status.PUBLISHED) {
            // Publishing twice would double everybody's inbox for no gain.
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This notice has already been published.");
        }
        if (notice.isExpired(Instant.now())) {
            throw AppException.rule("This notice has already expired and cannot be published.");
        }

        List<UUID> audience = audienceOf(notice.getAudience());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("noticeTitle", notice.getTitle());
        values.put("noticeBody", notice.getBody());
        values.put("institution.name", institutionName());

        int sent = writeAll(audience, CommunicationEventCode.NOTICE_PUBLISHED, values,
                "Notice", notice.getId(), Notification.Priority.NORMAL, notice.getId());
        notice.publish(auth.requireUser().userId(), sent);
        notices.save(notice);

        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("Notice")
                .entityId(notice.getId().toString())
                .entityLabel(notice.getTitle())
                .summary("Published to " + sent + " recipient(s)")
                .module(ModuleKey.COMMUNICATION.name())
                .succeeded(true)
                .build());
        return new CommunicationDtos.DispatchSummary(audience.size(), sent,
                audience.size() - sent, "notice-published");
    }

    @Transactional(readOnly = true)
    public CommunicationDtos.NoticeResponse getNotice(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.COMMUNICATION);
        auth.requirePermission("NOTICE_READ");
        return toNoticeResponse(requireNotice(id));
    }

    // ------------------------------------------------------------------- inbox

    @Transactional(readOnly = true)
    public PageResponse<CommunicationDtos.NotificationResponse> inbox(boolean unreadOnly,
            Pageable pageable) {
        UUID me = auth.requireUser().userId();
        Page<Notification> page = unreadOnly
                ? notifications.inbox(me, true, pageable)
                : notifications.inbox(me, false, pageable);
        return PageResponse.from(page, this::toNotificationResponse);
    }

    @Transactional(readOnly = true)
    public CommunicationDtos.UnreadCountResponse unreadCount() {
        return new CommunicationDtos.UnreadCountResponse(
                notifications.countByRecipientUserIdAndReadAtIsNull(auth.requireUser().userId()));
    }

    @Transactional
    public CommunicationDtos.NotificationResponse markRead(UUID id) {
        UUID me = auth.requireUser().userId();
        Notification notification = notifications.findByIdAndRecipientUserId(id, me)
                .orElseThrow(() -> AppException.notFound("Notification"));
        if (!notification.isRead()) {
            notification.markRead();
            notifications.save(notification);
        }
        return toNotificationResponse(notification);
    }

    /**
     * Marks every unread message read. The name promises the whole inbox, so it clears the
     * whole inbox: the unread ones are loaded and written in batches rather than a page at a
     * time, which would leave a heavy inbox partly unread behind a button labelled "mark all".
     */
    @Transactional
    public CommunicationDtos.UnreadCountResponse markAllRead() {
        UUID me = auth.requireUser().userId();
        int changed = 0;
        while (true) {
            List<Notification> batch = notifications
                    .inbox(me, true, org.springframework.data.domain.PageRequest.of(0, BATCH))
                    .getContent();
            if (batch.isEmpty()) {
                break;
            }
            batch.forEach(Notification::markRead);
            notifications.saveAll(batch);
            changed += batch.size();
            if (batch.size() < BATCH) {
                break;
            }
        }
        return new CommunicationDtos.UnreadCountResponse(changed);
    }

    // ---------------------------------------------------------------- dispatch

    /**
     * Writes the notifications for an event. Called after the work that caused it has committed,
     * so a rolled-back payment or a re-marked attendance record never leaves anybody notified.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CommunicationDtos.DispatchSummary dispatch(MessageEvent event) {
        institutions.requireModuleEnabled(ModuleKey.COMMUNICATION);
        if (event.recipientUserIds() == null || event.recipientUserIds().isEmpty()) {
            return new CommunicationDtos.DispatchSummary(0, 0, 0, null);
        }
        Map<String, String> values = new LinkedHashMap<>(event.variables() == null
                ? Map.of()
                : event.variables());
        values.putIfAbsent("institution.name", institutionName());
        int sent = writeAll(event.recipientUserIds(), event.event(), values, event.relatedType(),
                event.relatedId(), event.priority(), null);
        return new CommunicationDtos.DispatchSummary(event.recipientUserIds().size(), sent,
                event.recipientUserIds().size() - sent, event.event().code());
    }

    private int writeAll(List<UUID> recipientUserIds, CommunicationEventCode event,
            Map<String, String> values, String relatedType, UUID relatedId,
            Notification.Priority priority, UUID noticeId) {
        Optional<NotificationTemplate> template =
                templates.findByEventCodeAndChannelAndActiveTrue(event.code(), Channel.IN_APP);
        if (template.isEmpty()) {
            // No wording configured is a gap to fix, not a reason to fail the caller.
            audit.record(AuditEvent.builder()
                    .action(AuditAction.CREATE)
                    .entityType("Notification")
                    .entityId("-")
                    .entityLabel(event.code())
                    .summary("No active template for " + event.code()
                            + "; no notification was written")
                    .module(ModuleKey.COMMUNICATION.name())
                    .succeeded(false)
                    .build());
            return 0;
        }
        NotificationTemplate active = template.get();
        String subject = TemplateRenderer.render(active.getSubjectTemplate(), values);
        String body = TemplateRenderer.render(active.getBodyTemplate(), values);

        int written = 0;
        // A recipient may hold several roles, so the same person can arrive twice.
        for (UUID recipient : new LinkedHashSet<>(recipientUserIds)) {
            if (recipient == null) {
                continue;
            }
            if (relatedId != null && notifications
                    .existsByRecipientUserIdAndEventCodeAndRelatedTypeAndRelatedId(
                            recipient, event.code(), relatedType, relatedId)) {
                continue;
            }
            Notification notification = new Notification();
            notification.setRecipientUserId(recipient);
            notification.setEventCode(event.code());
            notification.setSubject(subject);
            notification.setBody(body);
            notification.setPriority(priority);
            notification.setRelatedType(relatedType);
            notification.setRelatedId(relatedId);
            notification.setNoticeId(noticeId);
            notifications.save(notification);
            written++;
        }
        return written;
    }

    // --------------------------------------------------------------- recipients

    /** Everyone signed in under a role, which is how an audience is addressed. */
    private List<UUID> audienceOf(Notice.Audience audience) {
        List<UUID> ids = new ArrayList<>();
        switch (audience) {
            case ALL -> {
                addAll(ids, Role.STUDENT);
                addAll(ids, Role.PARENT);
                addAll(ids, Role.TEACHER);
                addAll(ids, Role.ACCOUNTANT);
                addAll(ids, Role.LIBRARIAN);
                addAll(ids, Role.HR);
            }
            case STUDENTS -> addAll(ids, Role.STUDENT);
            case PARENTS -> addAll(ids, Role.PARENT);
            case TEACHERS -> addAll(ids, Role.TEACHER);
            case STAFF -> {
                addAll(ids, Role.TEACHER);
                addAll(ids, Role.ACCOUNTANT);
                addAll(ids, Role.LIBRARIAN);
                addAll(ids, Role.HR);
            }
        }
        return ids;
    }

    private void addAll(List<UUID> target, Role role) {
        users.findByPrimaryRoleAndStatus(role, UserStatus.ACTIVE)
                .forEach(user -> target.add(user.getId()));
    }

    /** A student and the guardians of theirs who have an account. */
    public List<UUID> recipientsForStudent(UUID studentId, String studentName) {
        List<UUID> recipients = new ArrayList<>();
        if (studentId == null) {
            return recipients;
        }
        users.findByStudentId(studentId).ifPresent(user -> recipients.add(user.getId()));
        recipients.addAll(guardians.accountIdsForStudent(studentId));
        return recipients;
    }

    /** The account of one member of staff, if they have one. */
    public List<UUID> recipientsForEmployee(UUID employeeId) {
        if (employeeId == null) {
            return List.of();
        }
        return users.findByEmployeeId(employeeId).map(user -> List.of(user.getId()))
                .orElseGet(List::of);
    }

    private String institutionName() {
        return institutions.requireInstitution().getName();
    }

    // ------------------------------------------------------------------ lookup

    private NotificationTemplate requireTemplate(UUID id) {
        return templates.findById(id)
                .orElseThrow(() -> AppException.notFound("Notification template"));
    }

    private Notice requireNotice(UUID id) {
        return notices.findById(id).orElseThrow(() -> AppException.notFound("Notice"));
    }

    private CommunicationEventCode eventOf(String eventCode) {
        for (CommunicationEventCode candidate : CommunicationEventCode.values()) {
            if (candidate.code().equalsIgnoreCase(eventCode)
                    || candidate.name().equalsIgnoreCase(eventCode)) {
                return candidate;
            }
        }
        throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Unknown notification event '" + eventCode + "'.");
    }

    private String normaliseCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private CommunicationDtos.TemplateResponse toTemplateResponse(NotificationTemplate t) {
        return new CommunicationDtos.TemplateResponse(t.getId(), t.getCode(), t.getName(),
                t.getChannel(), t.getEventCode(), t.getSubjectTemplate(), t.getBodyTemplate(),
                t.isActive(), t.variables());
    }

    private CommunicationDtos.NoticeResponse toNoticeResponse(Notice n) {
        return new CommunicationDtos.NoticeResponse(n.getId(), n.getTitle(), n.getBody(),
                n.getAudience(), n.getStatus(), n.getPublishedAt(), n.getExpiresAt(),
                n.getRecipientCount());
    }

    private CommunicationDtos.NotificationResponse toNotificationResponse(Notification n) {
        return new CommunicationDtos.NotificationResponse(n.getId(), n.getChannel(),
                n.getEventCode(), n.getSubject(), n.getBody(), n.getPriority(), n.isRead(),
                n.getSentAt(), n.getReadAt(), n.getRelatedType(), n.getRelatedId(),
                n.getNoticeId());
    }

    /** Guards a portal read: the caller must be in scope for this student, not merely signed in. */
    public void requireStudentScope(UUID studentId) {
        AuthenticatedUser user = auth.requireUser();
        if (!auth.canAccessStudent(user, studentId)) {
            throw AppException.denied("You do not have access to this student's records.");
        }
    }
}