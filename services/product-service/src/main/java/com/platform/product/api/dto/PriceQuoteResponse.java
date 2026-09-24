package com.platform.product.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Authoritative prices at quote time. The master-service passes these to the order-service so that
 * order totals are always based on catalog prices, never on prices sent by the browser.
 */
public record PriceQuoteResponse(String currency, List<Line> lines, BigDecimal total) {

    public record Line(String productId, String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }
}
