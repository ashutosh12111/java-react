package com.platform.master.query;

import com.platform.master.client.OrderClient.OrderView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id, String status, String currency, BigDecimal totalAmount, int itemCount, Instant createdAt) {

    static OrderSummaryResponse of(OrderView order) {
        int items = order.lines().stream().mapToInt(OrderView.Line::quantity).sum();
        return new OrderSummaryResponse(order.id(), order.status(), order.currency(), order.totalAmount(), items, order.createdAt());
    }
}
