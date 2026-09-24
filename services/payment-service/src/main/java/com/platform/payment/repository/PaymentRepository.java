package com.platform.payment.repository;

import com.platform.payment.domain.Payment;
import com.platform.payment.domain.PaymentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Persistence port; in-memory in Phase 1, PostgreSQL (unique index on idempotency_key) from Phase 3. */
public interface PaymentRepository {

    /**
     * Inserts atomically with respect to the idempotency key.
     *
     * @throws DuplicateIdempotencyKeyException if the key is already taken
     */
    Payment insert(Payment payment);

    Payment update(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Page<Payment> search(String orderId, PaymentStatus status, Pageable pageable);
}
