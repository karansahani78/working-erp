package com.educationerp.communication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns a raised {@link MessageEvent} into notifications, but only once the work that caused
 * it has actually committed.
 *
 * <p>This is the whole reason the event is published rather than written directly. A payment
 * that is rolled back, an attendance register that is thrown away, an admission that is
 * reversed — none of them may leave a parent believing it happened.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageDispatchListener {

    private final CommunicationService communication;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessage(MessageEvent event) {
        try {
            CommunicationDtos.DispatchSummary summary = communication.dispatch(event);
            if (summary.recipients() > 0 && summary.sent() == 0) {
                log.warn("No notification written for {} to {} recipient(s): no active template",
                        event.event().code(), summary.recipients());
            } else if (summary.sent() > 0) {
                log.debug("Wrote {} notification(s) for {} using template {}",
                        summary.sent(), event.event().code(), summary.templateCode());
            }
        } catch (RuntimeException e) {
            // A message that could not be written must not undo the business work that
            // triggered it, so the failure is logged and swallowed.
            log.error("Could not dispatch notifications for {}", event.event().code(), e);
        }
    }
}