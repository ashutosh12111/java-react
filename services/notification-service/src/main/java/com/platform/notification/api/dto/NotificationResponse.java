package com.platform.notification.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.platform.notification.domain.Channel;
import com.platform.notification.domain.Notification;
import com.platform.notification.domain.NotificationStatus;
import com.platform.notification.domain.NotificationTemplate;
import com.platform.notification.domain.Recipients;
import java.time.Instant;
import java.util.UUID;

/** The recipient is masked: listings are operational data and must not expose contact details. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
        UUID id,
        Channel channel,
        String recipient,
        NotificationTemplate template,
        String reference,
        NotificationStatus status,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant sentAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getChannel(), Recipients.mask(n.getRecipient()), n.getTemplate(),
                n.getReference(), n.getStatus(), n.getAttempts(), n.getLastError(), n.getCreatedAt(), n.getSentAt());
    }
}
