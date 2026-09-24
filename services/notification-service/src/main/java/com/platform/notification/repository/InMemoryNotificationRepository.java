package com.platform.notification.repository;

import com.platform.common.web.paging.InMemoryPageSupport;
import com.platform.notification.domain.Channel;
import com.platform.notification.domain.Notification;
import com.platform.notification.domain.NotificationStatus;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class InMemoryNotificationRepository implements NotificationRepository {

    private static final Map<String, Comparator<Notification>> SORTABLE =
            Map.of("createdAt", Comparator.comparing(Notification::getCreatedAt));

    private final Map<UUID, Notification> notifications = new ConcurrentHashMap<>();

    @Override
    public Notification save(Notification notification) {
        notifications.put(notification.getId(), notification);
        return notification;
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return Optional.ofNullable(notifications.get(id));
    }

    @Override
    public Page<Notification> search(NotificationStatus status, Channel channel, String reference, Pageable pageable) {
        return InMemoryPageSupport.page(
                notifications.values().stream()
                        .filter(n -> status == null || n.getStatus() == status)
                        .filter(n -> channel == null || n.getChannel() == channel)
                        .filter(n -> reference == null || reference.equals(n.getReference())),
                pageable, SORTABLE, Comparator.comparing(Notification::getCreatedAt).thenComparing(Notification::getId));
    }
}
