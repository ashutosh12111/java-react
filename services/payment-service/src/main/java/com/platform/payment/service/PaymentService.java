package com.platform.payment.service;

import com.platform.common.web.error.BusinessRuleException;
import com.platform.common.web.error.ConflictException;
import com.platform.common.web.error.ResourceNotFoundException;
import com.platform.payment.api.dto.CreatePaymentRequest;
import com.platform.payment.domain.Payment;
import com.platform.payment.domain.PaymentStatus;
import com.platform.payment.gateway.PaymentGateway;
import com.platform.payment.gateway.PaymentGateway.ChargeResult;
import com.platform.payment.repository.DuplicateIdempotencyKeyException;
import com.platform.payment.repository.PaymentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Idempotent payment processing.
 *
 * <p>Every payment request carries a client-generated {@code Idempotency-Key}. The provider is called
 * at most once per key, so network retries by the master-service can never double-charge a customer:
 * <ol>
 *   <li>Key seen before with the same request → return the stored result (no provider call).</li>
 *   <li>Key seen before with a different request → 422 IDEMPOTENCY_KEY_REUSED.</li>
 *   <li>Key seen before but still PENDING → 409 PAYMENT_IN_PROGRESS (a concurrent duplicate).</li>
 *   <li>New key → insert PENDING first (claims the key atomically), then charge, then record result.</li>
 * </ol>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository repository;
    private final PaymentGateway gateway;
    private final Clock clock;

    public PaymentService(PaymentRepository repository, PaymentGateway gateway, Clock clock) {
        this.repository = repository;
        this.gateway = gateway;
        this.clock = clock;
    }

    public PaymentOutcome pay(String idempotencyKey, CreatePaymentRequest request) {
        String fingerprint = fingerprint(request);
        var existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }

        Payment payment = new Payment(UUID.randomUUID(), request.orderId(), request.amount(), request.currency(),
                idempotencyKey, fingerprint, clock.instant());
        try {
            repository.insert(payment);
        } catch (DuplicateIdempotencyKeyException raced) {
            return replay(repository.findByIdempotencyKey(idempotencyKey).orElseThrow(), fingerprint);
        }

        ChargeResult result = gateway.charge(payment.getId(), payment.getAmount(), payment.getCurrency(),
                request.paymentMethodToken());
        switch (result) {
            case ChargeResult.Approved approved -> payment.complete(approved.providerReference(), clock.instant());
            case ChargeResult.Declined declined -> payment.fail(declined.reason(), clock.instant());
        }
        log.info("Payment {} for order {} finished with status {}", payment.getId(), payment.getOrderId(), payment.getStatus());
        return new PaymentOutcome(repository.update(payment), false);
    }

    public Payment get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }

    public Page<Payment> search(String orderId, PaymentStatus status, Pageable pageable) {
        return repository.search(orderId, status, pageable);
    }

    private static PaymentOutcome replay(Payment existing, String fingerprint) {
        if (!existing.getRequestFingerprint().equals(fingerprint)) {
            throw new BusinessRuleException("IDEMPOTENCY_KEY_REUSED",
                    "The Idempotency-Key was already used for a different payment request");
        }
        if (existing.getStatus() == PaymentStatus.PENDING) {
            throw new ConflictException("PAYMENT_IN_PROGRESS",
                    "A payment with this Idempotency-Key is still being processed; retry later");
        }
        return new PaymentOutcome(existing, true);
    }

    /** One-way hash of the request; lets us compare requests without storing the payment token. */
    static String fingerprint(CreatePaymentRequest r) {
        String canonical = String.join("|", r.orderId(), r.amount().stripTrailingZeros().toPlainString(),
                r.currency(), r.paymentMethodToken());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
