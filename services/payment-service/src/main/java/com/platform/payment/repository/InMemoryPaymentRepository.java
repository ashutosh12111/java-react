package com.platform.payment.repository;

import com.platform.common.web.paging.InMemoryPageSupport;
import com.platform.payment.domain.Payment;
import com.platform.payment.domain.PaymentStatus;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class InMemoryPaymentRepository implements PaymentRepository {

    private static final Map<String, Comparator<Payment>> SORTABLE = Map.of(
            "createdAt", Comparator.comparing(Payment::getCreatedAt),
            "amount", Comparator.comparing(Payment::getAmount));

    private final Map<UUID, Payment> payments = new ConcurrentHashMap<>();
    /** Plays the role of a unique index on idempotency_key. */
    private final Map<String, UUID> idempotencyIndex = new ConcurrentHashMap<>();

    @Override
    public Payment insert(Payment payment) {
        if (idempotencyIndex.putIfAbsent(payment.getIdempotencyKey(), payment.getId()) != null) {
            throw new DuplicateIdempotencyKeyException(payment.getIdempotencyKey());
        }
        payments.put(payment.getId(), payment);
        return payment;
    }

    @Override
    public Payment update(Payment payment) {
        payments.put(payment.getId(), payment);
        return payment;
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return Optional.ofNullable(payments.get(id));
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(idempotencyIndex.get(idempotencyKey)).map(payments::get);
    }

    @Override
    public Page<Payment> search(String orderId, PaymentStatus status, Pageable pageable) {
        return InMemoryPageSupport.page(
                payments.values().stream()
                        .filter(p -> orderId == null || p.getOrderId().equals(orderId))
                        .filter(p -> status == null || p.getStatus() == status),
                pageable, SORTABLE, Comparator.comparing(Payment::getCreatedAt).thenComparing(Payment::getId));
    }
}
