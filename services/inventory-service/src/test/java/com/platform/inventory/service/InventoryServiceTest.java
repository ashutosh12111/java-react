package com.platform.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.common.web.error.BusinessRuleException;
import com.platform.common.web.error.ConflictException;
import com.platform.inventory.api.dto.CreateReservationRequest;
import com.platform.inventory.api.dto.CreateReservationRequest.Line;
import com.platform.inventory.domain.ReservationStatus;
import com.platform.inventory.domain.StockItem;
import com.platform.inventory.repository.StockRepository;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exercises the service against the real (in-memory) repositories, because the interesting behaviour
 * is the interplay between stock items and reservations, not individual repository calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InventoryServiceTest {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private InventoryService service;

    @BeforeEach
    void setUp() {
        service.setStock("P100", 10);
        service.setStock("P200", 1);
    }

    private static CreateReservationRequest request(String ref, Line... lines) {
        return new CreateReservationRequest(ref, List.of(lines));
    }

    @Test
    void reservesAndReleasesStock() {
        var outcome = service.reserve(request("order-1", new Line("P100", 3)));

        assertThat(outcome.created()).isTrue();
        assertThat(stock("P100").getAvailable()).isEqualTo(7);
        assertThat(stock("P100").getReserved()).isEqualTo(3);

        service.release(outcome.reservation().getId());
        service.release(outcome.reservation().getId()); // idempotent retry must not double-release

        assertThat(stock("P100").getAvailable()).isEqualTo(10);
        assertThat(stock("P100").getReserved()).isZero();
    }

    @Test
    void commitRemovesReservedStock() {
        var reservation = service.reserve(request("order-1", new Line("P100", 3))).reservation();

        assertThat(service.commit(reservation.getId()).getStatus()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(stock("P100").getAvailable()).isEqualTo(7);
        assertThat(stock("P100").getReserved()).isZero();
    }

    @Test
    void isAllOrNothing() {
        assertThatThrownBy(() -> service.reserve(request("order-1", new Line("P100", 1), new Line("P200", 5))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("P200");

        assertThat(stock("P100").getAvailable()).as("no partial reservation").isEqualTo(10);
    }

    @Test
    void unknownProductHasNoStock() {
        assertThatThrownBy(() -> service.reserve(request("order-1", new Line("UNKNOWN", 1))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void sameReferenceIsIdempotent() {
        var first = service.reserve(request("order-1", new Line("P100", 2)));
        var retry = service.reserve(request("order-1", new Line("P100", 2)));

        assertThat(retry.created()).isFalse();
        assertThat(retry.reservation().getId()).isEqualTo(first.reservation().getId());
        assertThat(stock("P100").getAvailable()).isEqualTo(8);

        assertThatThrownBy(() -> service.reserve(request("order-1", new Line("P100", 5))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void neverOversellsUnderConcurrency() throws Exception {
        AtomicInteger successes = new AtomicInteger();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, 50).forEach(i -> pool.submit(() -> {
                try {
                    service.reserve(request("order-" + i, new Line("P100", 1)));
                    successes.incrementAndGet();
                } catch (BusinessRuleException expected) {
                    // out of stock
                }
            }));
        }
        assertThat(successes.get()).isEqualTo(10);
        assertThat(stock("P100").getAvailable()).isZero();
    }

    private StockItem stock(String productId) {
        return stockRepository.findByProductId(productId).orElseThrow();
    }
}
