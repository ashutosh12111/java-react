package com.platform.payment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * @param paymentMethodToken opaque token issued by the payment provider's client-side SDK. Raw card
 *                           numbers are rejected by the pattern and must never reach our servers.
 */
public record CreatePaymentRequest(
        @NotBlank @Size(max = 64) String orderId,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO-4217 code, e.g. USD") String currency,
        @NotBlank
        @Pattern(regexp = "^tok_[A-Za-z0-9_]{1,64}$",
                message = "must be a provider token (tok_...); raw card data is not accepted")
        @Schema(example = "tok_visa")
        String paymentMethodToken) {

    /** Masks the token so an accidental {@code log.info("{}", request)} cannot leak it. */
    @Override
    public String toString() {
        return "CreatePaymentRequest[orderId=" + orderId + ", amount=" + amount + ", currency=" + currency
                + ", paymentMethodToken=***]";
    }
}
