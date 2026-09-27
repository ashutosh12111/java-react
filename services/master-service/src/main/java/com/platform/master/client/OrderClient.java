package com.platform.master.client;

import com.platform.common.web.paging.PageResponse;
import com.platform.master.config.DownstreamProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
public class OrderClient {

    static final String SERVICE = "order-service";
    private static final ParameterizedTypeReference<PageResponse<OrderView>> PAGE_OF_ORDERS = new ParameterizedTypeReference<>() {
    };

    private final RestClient http;

    public OrderClient(DownstreamRestClientFactory factory, DownstreamProperties properties) {
        this.http = factory.create(SERVICE, properties.order());
    }

    /** Idempotent per reference: repeating it returns the same order. */
    public OrderView create(CreateOrder order) {
        return DownstreamRestClientFactory.call(SERVICE, () -> http.post()
                .uri("/api/v1/orders").body(order).retrieve().body(OrderView.class));
    }

    public OrderView get(UUID orderId) {
        return DownstreamRestClientFactory.call(SERVICE, () -> http.get()
                .uri("/api/v1/orders/{id}", orderId).retrieve().body(OrderView.class));
    }

    public OrderView confirm(UUID orderId) {
        return DownstreamRestClientFactory.call(SERVICE, () -> http.post()
                .uri("/api/v1/orders/{id}/confirm", orderId).retrieve().body(OrderView.class));
    }

    /** Compensation. */
    public OrderView cancel(UUID orderId, String reason) {
        return DownstreamRestClientFactory.call(SERVICE, () -> http.post()
                .uri("/api/v1/orders/{id}/cancel", orderId).body(new CancelRequest(reason)).retrieve().body(OrderView.class));
    }

    public PageResponse<OrderView> list(String customerId, String status, Pageable pageable) {
        return DownstreamRestClientFactory.call(SERVICE, () -> http.get()
                .uri(builder -> withPaging(builder.path("/api/v1/orders")
                        .queryParam("customerId", customerId)
                        .queryParamIfPresent("status", Optional.ofNullable(status)), pageable).build())
                .retrieve()
                .body(PAGE_OF_ORDERS));
    }

    private static UriBuilder withPaging(UriBuilder builder, Pageable pageable) {
        builder.queryParam("page", pageable.getPageNumber()).queryParam("size", pageable.getPageSize());
        pageable.getSort().forEach(o -> builder.queryParam("sort", o.getProperty() + "," + o.getDirection().name().toLowerCase()));
        return builder;
    }

    public record CreateOrder(String reference, String customerId, String currency, List<Line> lines) {
        public record Line(String productId, String productName, int quantity, BigDecimal unitPrice) {
        }
    }

    record CancelRequest(String reason) {
    }

    public record OrderView(
            UUID id, String reference, String customerId, String status, String currency, BigDecimal totalAmount,
            List<Line> lines, List<StatusChange> history, Instant createdAt, Instant updatedAt) {

        public record Line(String productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        }

        public record StatusChange(String status, Instant at, String reason) {
        }

        public boolean isPending() {
            return "PENDING".equals(status);
        }
    }
}
