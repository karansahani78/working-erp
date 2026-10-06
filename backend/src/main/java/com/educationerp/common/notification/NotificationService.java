package com.educationerp.common.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Outbound email.
 *
 * Delivery is deliberately indirect. When no mail sender is configured, or the SMTP call
 * fails, the message is written to the application log instead of being handed back to
 * the API caller. That keeps secrets such as password-reset tokens out of HTTP responses
 * while still making a fresh development environment usable without an SMTP server.
 *
 * The most recent message is also retained in memory. That is what lets an automated test
 * assert on delivery without asserting on log output.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ObjectProvider<MailSender> mailSender;

    @Getter
    private volatile SentMessage lastMessage;

    public void sendEmail(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.warn("Refusing to send notification with no recipient. subject={}", subject);
            return;
        }
        this.lastMessage = new SentMessage(to, subject, body, Instant.now());
        MailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            logDelivery(subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("Notification sent. to={} subject={}", mask(to), subject);
        } catch (RuntimeException ex) {
            log.error("Notification delivery failed to={} subject={}", mask(to), subject, ex);
            logDelivery(subject, body);
        }
    }

    private void logDelivery(String subject, String body) {
        log.info("=== EMAIL (delivery disabled, logged instead) ===\nSubject: {}\n{}", subject, body);
    }

    private String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** A captured message, retained so tests can assert on what would have been sent. */
    public record SentMessage(String to, String subject, String body, Instant at) {

        /** Extracts the first {@code label: value} line value from the body. */
        public String field(String label) {
            for (String line : body.split("\\R")) {
                if (line.startsWith(label)) {
                    int colon = line.indexOf(':');
                    return line.substring(colon + 1).trim();
                }
            }
            return null;
        }
    }
}