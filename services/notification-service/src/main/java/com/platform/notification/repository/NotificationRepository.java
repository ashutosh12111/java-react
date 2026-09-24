package com.platform.notification.repository;

import com.platform.notification.domain.Channel;
import com.platform.notification.domain.Notification;
import com.platform.notification.domain.NotificationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    Page<Notification> search(NotificationStatus status, Channel channel, String reference, Pageable pageable);
}
