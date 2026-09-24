package com.platform.notification.api;

import com.platform.common.web.paging.PageResponse;
import com.platform.notification.api.dto.NotificationResponse;
import com.platform.notification.api.dto.SendNotificationRequest;
import com.platform.notification.domain.Channel;
import com.platform.notification.domain.Notification;
import com.platform.notification.domain.NotificationStatus;
import com.platform.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @Operation(summary = "Request a notification",
            description = "202 Accepted: delivery is asynchronous by contract. Poll the Location for the outcome.")
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody SendNotificationRequest request) {
        Notification notification = notificationService.send(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(notification.getId()).toUri();
        return ResponseEntity.accepted().location(location).body(NotificationResponse.from(notification));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get notification delivery status")
    public NotificationResponse get(@PathVariable UUID id) {
        return NotificationResponse.from(notificationService.get(id));
    }

    @GetMapping
    @Operation(summary = "List notifications", description = "Filter by status, channel, reference. Sortable by: createdAt")
    public PageResponse<NotificationResponse> list(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) Channel channel,
            @RequestParam(required = false) String reference,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(notificationService.search(status, channel, reference, pageable), NotificationResponse::from);
    }
}
