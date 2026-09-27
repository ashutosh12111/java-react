package com.platform.master.checkout;

import com.platform.common.web.error.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Definitive outcome: the provider declined, the order was cancelled and the stock released. */
public class PaymentDeclinedException extends ApiException {

    public PaymentDeclinedException(UUID orderId, String reason) {
        super(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_DECLINED",
                "Payment was declined (" + reason + "). Order " + orderId + " has been cancelled.");
    }
}
