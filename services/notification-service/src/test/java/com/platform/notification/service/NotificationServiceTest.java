package com.platform.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import com.platform.common.web.error.BadRequestException;
import com.platform.notification.api.dto.SendNotificationRequest;
import com.platform.notification.domain.Channel;
import com.platform.notification.domain.Notification;
import com.platform.notification.domain.NotificationStatus;
import com.platform.notification.domain.NotificationTemplate;
import com.platform.notification.repository.NotificationRepository;
import com.platform.notification.sender.NotificationSender;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private NotificationSender sender;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, sender, Clock.systemUTC());
    }

    private static SendNotificationRequest email(String to) {
        return new SendNotificationRequest(Channel.EMAIL, to, NotificationTemplate.WELCOME, Map.of("firstName", "Ada"), null);
    }

    @Test
    void successfulDeliveryIsMarkedSent() {
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        Notification n = service.send(email("ada@example.com"));

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getAttempts()).isEqualTo(1);
        assertThat(n.getSentAt()).isNotNull();
    }

    @Test
    void providerFailureIsRecordedNotThrown() {
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));
        willThrow(new NotificationSender.DeliveryException("smtp down"))
                .given(sender).send(eq(Channel.EMAIL), any(), any());

        Notification n = service.send(email("ada@example.com"));

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(n.getLastError()).isEqualTo("smtp down");
    }

    @Test
    void recipientMustMatchChannel() {
        assertThatThrownBy(() -> service.send(email("+14155552671"))).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(sender, repository);
    }
}
