package com.platform.master.query;

import com.platform.master.client.OrderClient.OrderView;
import com.platform.master.client.PaymentClient.PaymentView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Order view aggregated from order-service and payment-service.
 *
 * @param payment  {@code null} if no payment exists or payment-service could not be reached
 * @param warnings e.g. {@code PAYMENT_STATUS_UNAVAILABLE} when only part of the data could be loaded
 */
public record OrderDetailsResponse(
        UUID id,
        String status,
        String customerId,
        String currency,
        BigDecimal totalAmount,
        List<Line> lines,
        List<StatusChange> history,
        Payment payment,
        Instant createdAt,
        List<String> warnings) {

    public record Line(String productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record StatusChange(String status, Instant at, String reason) {
    }

    public record Payment(UUID id, String status, String failureReason, Instant createdAt) {
    }

    static OrderDetailsResponse of(OrderView order, PaymentView payment, List<String> warnings) {
        return new OrderDetailsResponse(order.id(), order.status(), order.customerId(), order.currency(), order.totalAmount(),
                order.lines().stream()
                        .map(l -> new Line(l.productId(), l.productName(), l.quantity(), l.unitPrice(), l.lineTotal()))
                        .toList(),
                order.history().stream().map(h -> new StatusChange(h.status(), h.at(), h.reason())).toList(),
                payment == null ? null : new Payment(payment.id(), payment.status(), payment.failureReason(), payment.createdAt()),
                order.createdAt(),
                List.copyOf(warnings));
    }
}
