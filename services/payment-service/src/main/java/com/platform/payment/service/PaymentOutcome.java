package com.platform.payment.service;

import com.platform.payment.domain.Payment;

/** @param replayed {@code true} when a stored result was returned for a repeated idempotency key */
public record PaymentOutcome(Payment payment, boolean replayed) {
}
