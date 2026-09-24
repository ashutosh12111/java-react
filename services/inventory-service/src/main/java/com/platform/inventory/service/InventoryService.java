package com.platform.inventory.service;

import com.platform.common.web.error.BusinessRuleException;
import com.platform.common.web.error.ConflictException;
import com.platform.common.web.error.ResourceNotFoundException;
import com.platform.inventory.api.dto.CreateReservationRequest;
import com.platform.inventory.domain.Reservation;
import com.platform.inventory.domain.ReservationLine;
import com.platform.inventory.domain.StockItem;
import com.platform.inventory.repository.ReservationRepository;
import com.platform.inventory.repository.StockRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Stock management and reservations.
 *
 * <p>Reservations are all-or-nothing: either every line is reserved or none is. In Phase 1 the data is
 * in memory, so a single lock serialises stock mutations. Phase 3 replaces the lock with a database
 * transaction plus optimistic locking ({@code @Version}) so the guarantee also holds across replicas.
 * A {@link ReentrantLock} is used instead of {@code synchronized} to avoid pinning virtual threads.
 */
@Service
public class InventoryService {

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;
    private final ReentrantLock stockLock = new ReentrantLock();

    public InventoryService(StockRepository stockRepository, ReservationRepository reservationRepository, Clock clock) {
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    public StockItem setStock(String productId, int available) {
        return locked(() -> {
            Instant now = clock.instant();
            StockItem item = stockRepository.findByProductId(productId).orElseGet(() -> new StockItem(productId, 0, now));
            item.restock(available, now);
            return stockRepository.save(item);
        });
    }

    public StockItem getStock(String productId) {
        return stockRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock item", productId));
    }

    public Page<StockItem> searchStock(Integer maxAvailable, Pageable pageable) {
        return stockRepository.search(maxAvailable, pageable);
    }

    public ReservationOutcome reserve(CreateReservationRequest request) {
        List<ReservationLine> lines = mergeLines(request.lines());
        return locked(() -> {
            Optional<Reservation> existing = reservationRepository.findByReference(request.reference());
            if (existing.isPresent()) {
                return idempotentReplay(existing.get(), lines);
            }

            Map<String, StockItem> stock = new LinkedHashMap<>();
            lines.forEach(line -> stockRepository.findByProductId(line.productId())
                    .ifPresent(item -> stock.put(line.productId(), item)));

            List<String> insufficient = lines.stream()
                    .filter(line -> !stock.containsKey(line.productId())
                            || !stock.get(line.productId()).canReserve(line.quantity()))
                    .map(ReservationLine::productId)
                    .toList();
            if (!insufficient.isEmpty()) {
                throw new BusinessRuleException("INSUFFICIENT_STOCK", "Insufficient stock for products: " + insufficient);
            }

            Instant now = clock.instant();
            for (ReservationLine line : lines) {
                StockItem item = stock.get(line.productId());
                item.reserve(line.quantity(), now);
                stockRepository.save(item);
            }
            Reservation reservation = new Reservation(UUID.randomUUID(), request.reference(), lines, now);
            return new ReservationOutcome(reservationRepository.save(reservation), true);
        });
    }

    public Reservation getReservation(UUID id) {
        return reservationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Reservation", id));
    }

    /** Compensating action: returns held stock to the sellable pool. Safe to call repeatedly. */
    public Reservation release(UUID id) {
        return locked(() -> {
            Reservation reservation = getReservation(id);
            Instant now = clock.instant();
            if (reservation.release(now)) {
                forEachStockItem(reservation, (item, qty) -> item.release(qty, now));
                reservationRepository.save(reservation);
            }
            return reservation;
        });
    }

    /** Finalises the sale: reserved stock leaves the warehouse. Safe to call repeatedly. */
    public Reservation commit(UUID id) {
        return locked(() -> {
            Reservation reservation = getReservation(id);
            Instant now = clock.instant();
            if (reservation.commit(now)) {
                forEachStockItem(reservation, (item, qty) -> item.commit(qty, now));
                reservationRepository.save(reservation);
            }
            return reservation;
        });
    }

    private void forEachStockItem(Reservation reservation, StockChange change) {
        for (ReservationLine line : reservation.getLines()) {
            StockItem item = stockRepository.findByProductId(line.productId())
                    .orElseThrow(() -> new IllegalStateException("Reserved stock item disappeared: " + line.productId()));
            change.apply(item, line.quantity());
            stockRepository.save(item);
        }
    }

    private static ReservationOutcome idempotentReplay(Reservation existing, List<ReservationLine> requested) {
        if (!existing.getLines().equals(requested)) {
            throw new ConflictException("RESERVATION_REFERENCE_CONFLICT",
                    "Reference '" + existing.getReference() + "' is already used by a different reservation");
        }
        return new ReservationOutcome(existing, false);
    }

    private static List<ReservationLine> mergeLines(List<CreateReservationRequest.Line> lines) {
        Map<String, Integer> merged = lines.stream().collect(Collectors.toMap(
                CreateReservationRequest.Line::productId, CreateReservationRequest.Line::quantity,
                Integer::sum, LinkedHashMap::new));
        return merged.entrySet().stream().map(e -> new ReservationLine(e.getKey(), e.getValue())).toList();
    }

    private <T> T locked(Supplier<T> action) {
        stockLock.lock();
        try {
            return action.get();
        } finally {
            stockLock.unlock();
        }
    }

    @FunctionalInterface
    private interface StockChange {
        void apply(StockItem item, int quantity);
    }
}
