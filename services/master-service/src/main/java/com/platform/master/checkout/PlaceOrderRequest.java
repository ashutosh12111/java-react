package com.platform.master.checkout;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Public checkout request. Deliberately contains NO prices: the browser cannot be trusted with them,
 * so the master-service obtains authoritative prices from the product-service.
 */
public record PlaceOrderRequest(
        @NotBlank @Size(max = 64) String customerId,
        @NotEmpty @Size(max = 50) List<@Valid Item> items,
        @NotBlank
        @Pattern(regexp = "^tok_[A-Za-z0-9_]{1,64}$",
                message = "must be a provider token (tok_...); raw card data is not accepted")
        @Schema(example = "tok_visa")
        String paymentMethodToken) {

    public record Item(@NotBlank @Size(max = 32) String productId, @Min(1) @Max(1000) int quantity) {
    }

    @Override
    public String toString() {
        return "PlaceOrderRequest[customerId=" + customerId + ", items=" + items + ", paymentMethodToken=***]";
    }
}
