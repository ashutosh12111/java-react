package com.platform.inventory.domain;

import com.platform.common.web.error.ConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A hold on stock for one business reference (normally an order ID). */
public class Reservation {

    private final UUID id;
    private final String reference;
    private final List<ReservationLine> lines;
    private ReservationStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public Reservation(UUID id, String reference, List<ReservationLine> lines, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.reference = Objects.requireNonNull(reference);
        this.lines = List.copyOf(lines);
        this.status = ReservationStatus.RESERVED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * @return {@code true} if the state changed; {@code false} if already released (idempotent retry).
     */
    public boolean release(Instant now) {
        return transition(ReservationStatus.RELEASED, now);
    }

    public boolean commit(Instant now) {
        return transition(ReservationStatus.COMMITTED, now);
    }

    private boolean transition(ReservationStatus target, Instant now) {
        if (status == target) {
            return false;
        }
        if (status != ReservationStatus.RESERVED) {
            throw new ConflictException("INVALID_RESERVATION_STATE",
                    "Reservation " + id + " is " + status + " and cannot become " + target);
        }
        status = target;
        updatedAt = now;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public List<ReservationLine> getLines() {
        return lines;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
