package com.platform.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Port to an external payment service provider (Stripe, Adyen, ...). The provider holds the card
 * data; we only ever see an opaque token. {@code paymentId} is sent as the provider-side idempotency
 * key so a retried call can never double-charge.
 */
public interface PaymentGateway {

    ChargeResult charge(UUID paymentId, BigDecimal amount, String currency, String paymentMethodToken);

    sealed interface ChargeResult {
        record Approved(String providerReference) implements ChargeResult {
        }

        record Declined(String reason) implements ChargeResult {
        }
    }
}
