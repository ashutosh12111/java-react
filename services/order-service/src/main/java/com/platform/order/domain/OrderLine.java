package com.platform.order.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A snapshot of what was bought at which price. Product name and price are copied on purpose: an
 * order must not change when the catalog changes later.
 */
public record OrderLine(String productId, String productName, int quantity, BigDecimal unitPrice) {

    public OrderLine {
        unitPrice = unitPrice.setScale(2, RoundingMode.HALF_EVEN);
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
