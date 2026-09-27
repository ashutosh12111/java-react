package com.platform.master.client;

import com.platform.master.config.DownstreamProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserClient {

    static final String SERVICE = "user-service";
    private final RestClient http;

    public UserClient(DownstreamRestClientFactory factory, DownstreamProperties properties) {
        this.http = factory.create(SERVICE, properties.user());
    }

    public CustomerValidation validate(String customerId) {
        return DownstreamRestClientFactory.call(SERVICE, () -> http.get()
                .uri("/api/v1/users/{id}/validation", customerId)
                .retrieve()
                .body(CustomerValidation.class));
    }

    public record CustomerValidation(String userId, String status, boolean eligibleForOrders, String email) {
    }
}
