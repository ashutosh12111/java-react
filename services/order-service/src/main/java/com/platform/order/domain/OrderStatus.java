package com.platform.order.domain;

import java.util.Set;

/**
 * Order lifecycle. Transitions are declared here so the rules exist in exactly one place.
 *
 * <pre>
 * PENDING ──confirm──▶ CONFIRMED
 *    │
 *    └─────cancel────▶ CANCELLED
 * </pre>
 *
 * Phase 7 extends this with saga states (e.g. PAYMENT_PENDING) without changing callers.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> Set.of(CONFIRMED, CANCELLED).contains(target);
            case CONFIRMED, CANCELLED -> false;
        };
    }
}
