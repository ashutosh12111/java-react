package com.platform.inventory.repository;

import com.platform.inventory.domain.Reservation;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {

    Reservation save(Reservation reservation);

    Optional<Reservation> findById(UUID id);

    Optional<Reservation> findByReference(String reference);
}
