package com.platform.inventory.service;

import com.platform.inventory.domain.Reservation;

/** @param created {@code false} when an existing reservation was returned for an idempotent retry */
public record ReservationOutcome(Reservation reservation, boolean created) {
}
