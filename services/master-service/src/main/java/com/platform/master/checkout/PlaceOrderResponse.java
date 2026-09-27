package com.platform.master.checkout;

import com.platform.master.client.OrderClient.OrderView;
import com.platform.master.client.PaymentClient.PaymentView;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Aggregated result of a checkout.
 *
 * @param warnings non-fatal problems: the order succeeded but a follow-up step did not (yet), e.g.
 *                 {@code NOTIFICATION_NOT_SENT}. Clients show the order as placed.
 */
public record PlaceOrderResponse(
        UUID orderId,
        String status,
        String customerId,
        String currency,
        BigDecimal totalAmount,
        List<Line> lines,
        Payment payment,
        List<String> warnings) {

    public record Line(String productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record Payment(UUID id, String status, String providerReference) {
    }

    static PlaceOrderResponse of(OrderView order, PaymentView payment, List<String> warnings) {
        List<Line> lines = order.lines().stream()
                .map(l -> new Line(l.productId(), l.productName(), l.quantity(), l.unitPrice(), l.lineTotal()))
                .toList();
        return new PlaceOrderResponse(order.id(), order.status(), order.customerId(), order.currency(),
                order.totalAmount(), lines, new Payment(payment.id(), payment.status(), payment.providerReference()),
                List.copyOf(warnings));
    }
}
