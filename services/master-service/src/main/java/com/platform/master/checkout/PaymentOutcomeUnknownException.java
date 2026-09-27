package com.platform.master.checkout;

import com.platform.common.web.error.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * The payment call timed out or failed mid-flight, so we do not know whether the customer was charged.
 * We must NOT cancel the order or release stock (the charge may have succeeded) and must NOT blindly
 * re-send the payment. The client retries with the same Idempotency-Key; every step is idempotent, so
 * the retry resumes the checkout and the payment-service replays the real result instead of charging again.
 */
public class PaymentOutcomeUnknownException extends ApiException {

    public PaymentOutcomeUnknownException(UUID orderId) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_OUTCOME_UNKNOWN",
                "Order " + orderId + " is awaiting payment confirmation. Retry with the same Idempotency-Key.");
    }
}
