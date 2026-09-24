package com.platform.payment.domain;

/** PENDING while the provider is being called; COMPLETED and FAILED are terminal. */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED
}
