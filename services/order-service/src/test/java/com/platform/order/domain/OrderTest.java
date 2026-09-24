package com.platform.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.common.web.error.ConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderTest {

    private static Order newOrder() {
        return new Order(UUID.randomUUID(), "customer-1", "USD", List.of(
                new OrderLine("P100", "Keyboard", 2, new BigDecimal("49.90")),
                new OrderLine("P200", "Mouse", 1, new BigDecimal("19.95"))), Instant.EPOCH);
    }

    @Test
    void computesTotalFromLines() {
        assertThat(newOrder().getTotalAmount()).isEqualByComparingTo("119.75");
    }

    @Test
    void recordsStatusHistory() {
        Order order = newOrder();
        order.cancel("payment declined", Instant.EPOCH.plusSeconds(5));

        assertThat(order.getHistory()).extracting(StatusChange::status)
                .containsExactly(OrderStatus.PENDING, OrderStatus.CANCELLED);
        assertThat(order.getHistory().getLast().reason()).isEqualTo("payment declined");
    }

    @Test
    void terminalStatesCannotChange() {
        Order order = newOrder();
        order.confirm(Instant.EPOCH);

        assertThatThrownBy(() -> order.cancel("too late", Instant.EPOCH)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> order.confirm(Instant.EPOCH)).isInstanceOf(ConflictException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING,CONFIRMED,true", "PENDING,CANCELLED,true", "PENDING,PENDING,false",
            "CONFIRMED,CANCELLED,false", "CANCELLED,CONFIRMED,false"})
    void transitionTable(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
