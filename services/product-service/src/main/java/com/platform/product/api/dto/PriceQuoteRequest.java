package com.platform.product.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PriceQuoteRequest(@NotEmpty @Size(max = 50) List<@Valid Item> items) {

    public record Item(@NotBlank String productId, @Min(1) @Max(1000) int quantity) {
    }
}
