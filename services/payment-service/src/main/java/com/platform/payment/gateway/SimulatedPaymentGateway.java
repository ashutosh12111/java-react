package com.platform.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in for a real provider, driven by well-known test tokens (mirroring how real
 * providers expose test cards):
 * <ul>
 *   <li>{@code tok_decline...} → declined: card_declined</li>
 *   <li>{@code tok_insufficient_funds} → declined: insufficient_funds</li>
 *   <li>anything else → approved</li>
 * </ul>
 */
@Component
class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    @Override
    public ChargeResult charge(UUID paymentId, BigDecimal amount, String currency, String paymentMethodToken) {
        log.info("Simulated charge paymentId={} amount={} {}", paymentId, amount, currency); // never log the token
        if (paymentMethodToken.startsWith("tok_decline")) {
            return new ChargeResult.Declined("card_declined");
        }
        if (paymentMethodToken.equals("tok_insufficient_funds")) {
            return new ChargeResult.Declined("insufficient_funds");
        }
        return new ChargeResult.Approved("sim_" + paymentId);
    }
}
