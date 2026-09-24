package com.platform.inventory.api.dto;

import com.platform.inventory.domain.StockItem;
import java.time.Instant;

public record StockResponse(String productId, int available, int reserved, Instant updatedAt) {

    public static StockResponse from(StockItem s) {
        return new StockResponse(s.getProductId(), s.getAvailable(), s.getReserved(), s.getUpdatedAt());
    }
}
