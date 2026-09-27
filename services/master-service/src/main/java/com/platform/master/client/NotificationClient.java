package com.platform.master.client;

import com.platform.master.config.DownstreamProperties;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Phase 2 only: notifications move to Kafka events in Phase 7 and this client goes away. */
@Component
public class NotificationClient {

    static final String SERVICE = "notification-service";
    private final RestClient http;

    public NotificationClient(DownstreamRestClientFactory factory, DownstreamProperties properties) {
        this.http = factory.create(SERVICE, properties.notification());
    }

    public void sendEmail(String recipient, String template, Map<String, String> variables, String reference) {
        DownstreamRestClientFactory.call(SERVICE, () -> http.post()
                .uri("/api/v1/notifications")
                .body(new SendRequest("EMAIL", recipient, template, variables, reference))
                .retrieve()
                .toBodilessEntity());
    }

    record SendRequest(String channel, String recipient, String template, Map<String, String> variables, String reference) {
    }
}
