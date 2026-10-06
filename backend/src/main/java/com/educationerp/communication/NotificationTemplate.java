package com.educationerp.communication;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * The editable wording of a message. One live template exists per event per channel, so an
 * institution can reword a receipt without touching the code that raises it.
 */
@Entity
@Table(name = "notification_templates",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_templates_event_channel",
                columnNames = {"event_code", "channel"}))
@Getter
@Setter
public class NotificationTemplate extends BaseEntity {

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private Channel channel = Channel.IN_APP;

    @Column(name = "event_code", nullable = false, length = 60)
    private String eventCode;

    @Column(name = "subject_template", nullable = false, length = 200)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public static NotificationTemplate of(String code, String name, Channel channel,
            CommunicationEventCode event, String subjectTemplate, String bodyTemplate) {
        NotificationTemplate template = new NotificationTemplate();
        template.code = code;
        template.name = name;
        template.channel = channel;
        template.eventCode = event.code();
        template.subjectTemplate = subjectTemplate;
        template.bodyTemplate = bodyTemplate;
        return template;
    }

    public List<String> variables() {
        return TemplateRenderer.variablesOf(subjectTemplate + " " + bodyTemplate);
    }
}