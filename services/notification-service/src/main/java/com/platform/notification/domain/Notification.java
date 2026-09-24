package com.platform.notification.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Notification {

    private final UUID id;
    private final Channel channel;
    private final String recipient;
    private final NotificationTemplate template;
    private final Map<String, String> variables;
    private final String reference;
    private NotificationStatus status;
    private int attempts;
    private String lastError;
    private final Instant createdAt;
    private Instant sentAt;

    public Notification(UUID id, Channel channel, String recipient, NotificationTemplate template,
                        Map<String, String> variables, String reference, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.channel = Objects.requireNonNull(channel);
        this.recipient = Objects.requireNonNull(recipient);
        this.template = Objects.requireNonNull(template);
        this.variables = Map.copyOf(variables);
        this.reference = reference;
        this.status = NotificationStatus.PENDING;
        this.createdAt = now;
    }

    public void markSent(Instant now) {
        attempts++;
        status = NotificationStatus.SENT;
        sentAt = now;
        lastError = null;
    }

    public void markFailed(String error) {
        attempts++;
        status = NotificationStatus.FAILED;
        lastError = error;
    }

    public UUID getId() {
        return id;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public NotificationTemplate getTemplate() {
        return template;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public String getReference() {
        return reference;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
