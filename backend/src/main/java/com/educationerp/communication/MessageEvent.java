package com.educationerp.communication;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Something worth telling somebody about.
 *
 * <p>A module that has done its work publishes one of these and forgets about it. It does not
 * know who will be told, what the message will read, or whether a template even exists: that is
 * the communication module's problem, and keeping it out of the finance and attendance modules
 * is what stops a wording change from turning into a change in four places.
 *
 * @param event         which template to use
 * @param recipientUserIds who to tell; an empty list means the event is not worth sending
 * @param variables     values for the template's placeholders
 * @param relatedType   what the message is about, so the portal can link to it
 * @param relatedId     the record's id
 * @param priority      whether it should stand out in the inbox
 */
public record MessageEvent(
        CommunicationEventCode event,
        List<UUID> recipientUserIds,
        Map<String, String> variables,
        String relatedType,
        UUID relatedId,
        Notification.Priority priority) {

    public static MessageEvent of(CommunicationEventCode event, List<UUID> recipients,
            Map<String, String> variables, String relatedType, UUID relatedId) {
        return new MessageEvent(event, recipients, variables, relatedType, relatedId,
                Notification.Priority.NORMAL);
    }
}