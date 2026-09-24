package com.platform.product.api.dto;

import com.platform.product.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        String id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice().amount(),
                p.getPrice().currency(), p.isActive(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
