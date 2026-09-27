package com.platform.master.client;

import com.platform.common.web.paging.PageResponse;
import com.platform.master.config.DownstreamProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentClient {

    static final String SERVICE = "payment-service";
    private static final ParameterizedTypeReference<PageResponse<PaymentView>> PAGE_OF_PAYMENTS = new ParameterizedTypeReference<>() {
    };

    private final RestClient http;

    public PaymentClient(DownstreamRestClientFactory factory, DownstreamProperties properties) {
        this.http = factory.create(SERVICE, properties.payment());
    }

    /**
     * NOT naturally idempotent, so it is always sent with a deterministic Idempotency-Key: repeating
     * the call with the same key can never charge twice.
     */
    public PaymentView pay(String idempotencyKey, PaymentRequest request) {
        return DownstreamRestClientFactory.call(SERVICE, () -> http.post()
                .uri("/api/v1/payments")
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .body(PaymentView.class));
    }

    public List<PaymentView> findByOrder(UUID orderId) {
        return DownstreamRestClientFactory.call(SERVICE, () -> http.get()
                .uri("/api/v1/payments?orderId={orderId}&sort=createdAt,desc", orderId)
                .retrieve()
                .body(PAGE_OF_PAYMENTS)).content();
    }

    public record PaymentRequest(String orderId, BigDecimal amount, String currency, String paymentMethodToken) {

        @Override
        public String toString() {
            return "PaymentRequest[orderId=" + orderId + ", amount=" + amount + ", currency=" + currency + ", token=***]";
        }
    }

    public record PaymentView(
            UUID id, String orderId, BigDecimal amount, String currency, String status,
            String providerReference, String failureReason, Instant createdAt) {

        public boolean isCompleted() {
            return "COMPLETED".equals(status);
        }

        public boolean isFailed() {
            return "FAILED".equals(status);
        }
    }
}
