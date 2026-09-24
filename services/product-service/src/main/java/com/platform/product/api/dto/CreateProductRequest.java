package com.platform.product.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotNull @Pattern(regexp = "^[A-Z0-9-]{2,32}$", message = "must be an upper-case SKU, e.g. P100") String id,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO-4217 code, e.g. USD") String currency,
        @NotNull Boolean active) {
}
