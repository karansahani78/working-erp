package com.educationerp.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CommunicationDtos {

    private CommunicationDtos() {
    }

    public record TemplateRequest(
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 120) String name,
            @NotNull Channel channel,
            @NotBlank @Size(max = 60) String eventCode,
            @NotBlank @Size(max = 200) String subjectTemplate,
            @NotBlank String bodyTemplate,
            Boolean active) {
    }

    public record TemplateResponse(
            UUID id,
            String code,
            String name,
            Channel channel,
            String eventCode,
            String subjectTemplate,
            String bodyTemplate,
            boolean active,
            List<String> variables) {
    }

    public record NoticeRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank String body,
            @NotNull Notice.Audience audience,
            Instant expiresAt) {
    }

    public record NoticeResponse(
            UUID id,
            String title,
            String body,
            Notice.Audience audience,
            Notice.Status status,
            Instant publishedAt,
            Instant expiresAt,
            int recipientCount) {
    }

    public record NotificationResponse(
            UUID id,
            Channel channel,
            String eventCode,
            String subject,
            String body,
            Notification.Priority priority,
            boolean read,
            Instant sentAt,
            Instant readAt,
            String relatedType,
            UUID relatedId,
            UUID noticeId) {
    }

    public record UnreadCountResponse(long unread) {
    }

    /**
     * What a publish or an event actually achieved. Reported rather than assumed: a template
     * that is missing or inactive is a gap worth seeing, and so is a repeat event that was
     * correctly written only once.
     */
    public record DispatchSummary(int recipients, int sent, int skipped, String templateCode) {
    }
}