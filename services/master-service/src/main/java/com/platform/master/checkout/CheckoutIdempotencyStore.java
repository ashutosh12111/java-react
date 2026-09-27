package com.platform.master.checkout;

import java.util.Optional;

/**
 * Remembers checkout outcomes per idempotency key. In-memory in Phase 2; Phase 6 moves it to Redis so
 * it is shared by all master-service replicas and survives restarts.
 */
public interface CheckoutIdempotencyStore {

    /**
     * Atomically claims {@code key} for a new attempt.
     *
     * @return empty if the claim succeeded; otherwise the existing entry (in progress or completed)
     */
    Optional<Entry> claim(String key, String fingerprint);

    void complete(String key, StoredOutcome outcome);

    /** Forgets an attempt whose outcome was not final (5xx), so the client may retry with the same key. */
    void release(String key);

    /** @param outcome {@code null} while the first attempt is still running */
    record Entry(String fingerprint, StoredOutcome outcome) {
    }

    sealed interface StoredOutcome {
        record Success(PlaceOrderResponse response) implements StoredOutcome {
        }

        record Failure(int status, String code, String message) implements StoredOutcome {
        }
    }
}
