package com.platform.notification.sender;

import com.platform.notification.domain.Channel;
import com.platform.notification.domain.NotificationTemplate.RenderedMessage;
import com.platform.notification.domain.Recipients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Development sender: "delivers" by logging. Real providers are plugged in per environment later
 * without touching {@code NotificationService}. Recipients ending in {@code .invalid} (RFC 2606)
 * simulate a provider rejection so failure handling can be exercised.
 */
@Component
class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(Channel channel, String recipient, RenderedMessage message) {
        if (recipient.endsWith(".invalid")) {
            throw new DeliveryException("Provider rejected recipient");
        }
        log.info("Delivered {} to {}: {}", channel, Recipients.mask(recipient), message.subject());
    }
}
