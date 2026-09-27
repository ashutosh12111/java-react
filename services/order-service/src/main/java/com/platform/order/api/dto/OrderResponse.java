package com.platform.order.api.dto;

import com.platform.order.domain.Order;
import com.platform.order.domain.OrderStatus;
import com.platform.order.domain.StatusChange;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String reference,
        String customerId,
        OrderStatus status,
        String currency,
        BigDecimal totalAmount,
        List<Line> lines,
        List<StatusChange> history,
        Instant createdAt,
        Instant updatedAt) {

    public record Line(String productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public static OrderResponse from(Order o) {
        List<Line> lines = o.getLines().stream()
                .map(l -> new Line(l.productId(), l.productName(), l.quantity(), l.unitPrice(), l.lineTotal()))
                .toList();
        return new OrderResponse(o.getId(), o.getReference(), o.getCustomerId(), o.getStatus(), o.getCurrency(), o.getTotalAmount(),
                lines, o.getHistory(), o.getCreatedAt(), o.getUpdatedAt());
    }
}
