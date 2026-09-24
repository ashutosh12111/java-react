package com.platform.notification.sender;

import com.platform.notification.domain.Channel;
import com.platform.notification.domain.NotificationTemplate.RenderedMessage;

/** Port to delivery providers (SMTP, SMS gateway, push service). */
public interface NotificationSender {

    /** @throws DeliveryException when the provider rejects or cannot be reached */
    void send(Channel channel, String recipient, RenderedMessage message);

    class DeliveryException extends RuntimeException {
        public DeliveryException(String message) {
            super(message);
        }
    }
}
