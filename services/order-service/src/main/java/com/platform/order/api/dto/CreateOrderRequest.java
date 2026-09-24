package com.platform.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Internal API used by the master-service. Unit prices come from the product-service price quote,
 * never from the browser; the order-service just records them.
 */
public record CreateOrderRequest(
        @NotBlank @Size(max = 64) String customerId,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO-4217 code, e.g. USD") String currency,
        @NotEmpty @Size(max = 50) List<@Valid Line> lines) {

    public record Line(
            @NotBlank @Size(max = 32) String productId,
            @NotBlank @Size(max = 200) String productName,
            @Min(1) @Max(1000) int quantity,
            @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal unitPrice) {
    }
}
