package com.platform.inventory.api.dto;

import com.platform.inventory.domain.Reservation;
import com.platform.inventory.domain.ReservationLine;
import com.platform.inventory.domain.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id, String reference, ReservationStatus status, List<ReservationLine> lines, Instant createdAt, Instant updatedAt) {

    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(r.getId(), r.getReference(), r.getStatus(), r.getLines(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
