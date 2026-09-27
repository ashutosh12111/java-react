package com.platform.master.client;

import com.platform.master.config.DownstreamProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryClient {

    static final String SERVICE = "inventory-service";
    private final RestClient http;

    public InventoryClient(DownstreamRestClientFactory factory, DownstreamProperties properties) {
        this.http = factory.create(SERVICE, properties.inventory());
    }

    /** Idempotent per reference: repeating it returns the same reservation. */
    public Reservation reserve(String reference, List<Line> lines) {
        return DownstreamRestClientFactory.call(SERVICE, () -> http.post()
                .uri("/api/v1/reservations")
                .body(new ReserveRequest(reference, lines))
                .retrieve()
                .body(Reservation.class));
    }

    /** Compensation. Idempotent. */
    public Reservation release(UUID reservationId) {
        return DownstreamRestClientFactory.call(SERVICE, () -> http.post()
                .uri("/api/v1/reservations/{id}/release", reservationId)
                .retrieve()
                .body(Reservation.class));
    }

    /** Idempotent. */
    public Reservation commit(UUID reservationId) {
        return DownstreamRestClientFactory.call(SERVICE, () -> http.post()
                .uri("/api/v1/reservations/{id}/commit", reservationId)
                .retrieve()
                .body(Reservation.class));
    }

    public record Line(String productId, int quantity) {
    }

    record ReserveRequest(String reference, List<Line> lines) {
    }

    public record Reservation(UUID id, String reference, String status) {
    }
}
