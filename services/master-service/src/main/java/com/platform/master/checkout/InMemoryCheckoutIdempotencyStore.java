package com.platform.master.checkout;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Single-instance store with a 24h retention window. Replaced by Redis in Phase 6. */
@Component
class InMemoryCheckoutIdempotencyStore implements CheckoutIdempotencyStore {

    static final Duration RETENTION = Duration.ofHours(24);

    private record Slot(Entry entry, Instant createdAt) {
    }

    private final Map<String, Slot> slots = new ConcurrentHashMap<>();
    private final Clock clock;

    InMemoryCheckoutIdempotencyStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<Entry> claim(String key, String fingerprint) {
        Instant now = clock.instant();
        AtomicReference<Entry> existing = new AtomicReference<>();
        slots.compute(key, (k, slot) -> {
            if (slot != null && slot.createdAt().plus(RETENTION).isAfter(now)) {
                existing.set(slot.entry());
                return slot;
            }
            return new Slot(new Entry(fingerprint, null), now);
        });
        return Optional.ofNullable(existing.get());
    }

    @Override
    public void complete(String key, StoredOutcome outcome) {
        slots.computeIfPresent(key, (k, slot) -> new Slot(new Entry(slot.entry().fingerprint(), outcome), slot.createdAt()));
    }

    @Override
    public void release(String key) {
        slots.remove(key);
    }
}
