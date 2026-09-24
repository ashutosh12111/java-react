package com.platform.payment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A payment attempt for an order.
 *
 * <p>Deliberately holds NO card data and not even the payment-method token: only the provider's
 * reference is kept. {@code requestFingerprint} is a one-way hash used to detect idempotency-key reuse.
 */
public class Payment {

    private final UUID id;
    private final String orderId;
    private final BigDecimal amount;
    private final String currency;
    private final String idempotencyKey;
    private final String requestFingerprint;
    private PaymentStatus status;
    private String providerReference;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;

    public Payment(UUID id, String orderId, BigDecimal amount, String currency,
                   String idempotencyKey, String requestFingerprint, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.orderId = Objects.requireNonNull(orderId);
        this.amount = amount.setScale(2, RoundingMode.HALF_EVEN);
        this.currency = Objects.requireNonNull(currency);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.requestFingerprint = Objects.requireNonNull(requestFingerprint);
        this.status = PaymentStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void complete(String providerReference, Instant now) {
        requirePending();
        this.status = PaymentStatus.COMPLETED;
        this.providerReference = providerReference;
        this.updatedAt = now;
    }

    public void fail(String reason, Instant now) {
        requirePending();
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    private void requirePending() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment " + id + " is already " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
