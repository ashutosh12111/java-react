package com.platform.notification.service;

import com.platform.common.web.error.BadRequestException;
import com.platform.common.web.error.ResourceNotFoundException;
import com.platform.notification.api.dto.SendNotificationRequest;
import com.platform.notification.domain.Channel;
import com.platform.notification.domain.Notification;
import com.platform.notification.domain.NotificationStatus;
import com.platform.notification.repository.NotificationRepository;
import com.platform.notification.sender.NotificationSender;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Accepts notification requests and delivers them.
 *
 * <p>Phase 1 delivers inline with a single attempt. Phase 7 moves delivery behind Kafka consumers
 * with retry topics and a dead-letter topic; the API already answers 202 Accepted so clients do not
 * change when delivery becomes asynchronous.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final NotificationSender sender;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, NotificationSender sender, Clock clock) {
        this.repository = repository;
        this.sender = sender;
        this.clock = clock;
    }

    public Notification send(SendNotificationRequest request) {
        if (!request.channel().acceptsRecipient(request.recipient())) {
            throw new BadRequestException("INVALID_RECIPIENT", "Recipient is not valid for channel " + request.channel());
        }
        Notification notification = repository.save(new Notification(UUID.randomUUID(), request.channel(),
                request.recipient(), request.template(), request.variables(), request.reference(), clock.instant()));
        deliver(notification);
        return repository.save(notification);
    }

    public Notification get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification", id));
    }

    public Page<Notification> search(NotificationStatus status, Channel channel, String reference, Pageable pageable) {
        return repository.search(status, channel, reference, pageable);
    }

    private void deliver(Notification notification) {
        try {
            sender.send(notification.getChannel(), notification.getRecipient(),
                    notification.getTemplate().render(notification.getVariables()));
            notification.markSent(clock.instant());
        } catch (NotificationSender.DeliveryException e) {
            log.warn("Delivery of notification {} failed: {}", notification.getId(), e.getMessage());
            notification.markFailed(e.getMessage());
        }
    }
}
