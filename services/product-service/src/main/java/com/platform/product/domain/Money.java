package com.platform.product.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Monetary amount in minor-unit precision. Never use {@code double} for money. */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        amount = amount.setScale(2, RoundingMode.HALF_EVEN);
    }

    public Money times(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }
}
