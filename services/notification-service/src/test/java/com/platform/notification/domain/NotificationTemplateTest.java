package com.platform.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTemplateTest {

    @Test
    void rendersVariablesAndBlanksMissingOnes() {
        var message = NotificationTemplate.ORDER_CONFIRMED.render(Map.of("orderId", "o-1", "total", "$5"));

        assertThat(message.subject()).isEqualTo("Order o-1 confirmed");
        assertThat(message.body()).isEqualTo("Your order o-1 for $5  is confirmed.");
    }

    @Test
    void channelsValidateRecipientFormat() {
        assertThat(Channel.EMAIL.acceptsRecipient("ada@example.com")).isTrue();
        assertThat(Channel.EMAIL.acceptsRecipient("+14155552671")).isFalse();
        assertThat(Channel.SMS.acceptsRecipient("+14155552671")).isTrue();
        assertThat(Channel.SMS.acceptsRecipient("555-1234")).isFalse();
    }

    @Test
    void masksRecipients() {
        assertThat(Recipients.mask("ada@example.com")).isEqualTo("a***@example.com");
        assertThat(Recipients.mask("+14155552671")).isEqualTo("+1***671");
    }
}
