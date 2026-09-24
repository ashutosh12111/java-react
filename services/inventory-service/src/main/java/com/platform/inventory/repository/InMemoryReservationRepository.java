package com.platform.inventory.repository;

import com.platform.inventory.domain.Reservation;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
class InMemoryReservationRepository implements ReservationRepository {

    private final Map<UUID, Reservation> reservations = new ConcurrentHashMap<>();

    @Override
    public Reservation save(Reservation reservation) {
        reservations.put(reservation.getId(), reservation);
        return reservation;
    }

    @Override
    public Optional<Reservation> findById(UUID id) {
        return Optional.ofNullable(reservations.get(id));
    }

    @Override
    public Optional<Reservation> findByReference(String reference) {
        return reservations.values().stream().filter(r -> r.getReference().equals(reference)).findFirst();
    }
}
