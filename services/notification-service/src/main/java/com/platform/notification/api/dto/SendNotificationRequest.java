package com.platform.notification.api.dto;

import com.platform.notification.domain.Channel;
import com.platform.notification.domain.NotificationTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * @param reference optional business key (e.g. order ID) used to find notifications for an order.
 */
public record SendNotificationRequest(
        @NotNull Channel channel,
        @NotBlank @Size(max = 320) String recipient,
        @NotNull NotificationTemplate template,
        @Size(max = 20) Map<@NotBlank @Size(max = 50) String, @Size(max = 500) String> variables,
        @Size(max = 64) String reference) {

    public SendNotificationRequest {
        variables = variables == null ? Map.of() : variables;
    }
}
