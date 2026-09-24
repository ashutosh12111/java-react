package com.platform.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.common.web.error.ConflictException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationTest {

    private final Reservation reservation =
            new Reservation(UUID.randomUUID(), "order-1", List.of(new ReservationLine("P100", 2)), Instant.EPOCH);

    @Test
    void releaseIsIdempotent() {
        assertThat(reservation.release(Instant.EPOCH)).isTrue();
        assertThat(reservation.release(Instant.EPOCH)).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void cannotCommitAReleasedReservation() {
        reservation.release(Instant.EPOCH);

        assertThatThrownBy(() -> reservation.commit(Instant.EPOCH)).isInstanceOf(ConflictException.class);
    }
}
