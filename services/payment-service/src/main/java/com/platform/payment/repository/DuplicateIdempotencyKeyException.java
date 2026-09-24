package com.platform.payment.repository;

/** Another request inserted a payment with the same idempotency key first (a concurrent retry). */
public class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(String key) {
        super("Idempotency key already used: " + key);
    }
}
