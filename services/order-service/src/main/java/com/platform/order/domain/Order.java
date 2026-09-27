package com.platform.order.domain;

import com.platform.common.web.error.ConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Order aggregate. All state changes go through methods that enforce {@link OrderStatus} rules. */
public class Order {

    private final UUID id;
    private final String reference;
    private final String customerId;
    private final String currency;
    private final List<OrderLine> lines;
    private final BigDecimal totalAmount;
    private OrderStatus status;
    private final List<StatusChange> history = new ArrayList<>();
    private final Instant createdAt;
    private Instant updatedAt;

    public Order(UUID id, String reference, String customerId, String currency, List<OrderLine> lines, Instant now) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one line");
        }
        this.id = Objects.requireNonNull(id);
        this.reference = Objects.requireNonNull(reference);
        this.customerId = Objects.requireNonNull(customerId);
        this.currency = Objects.requireNonNull(currency);
        this.lines = List.copyOf(lines);
        this.totalAmount = lines.stream().map(OrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.status = OrderStatus.PENDING;
        this.history.add(new StatusChange(OrderStatus.PENDING, now, null));
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void confirm(Instant now) {
        transitionTo(OrderStatus.CONFIRMED, now, null);
    }

    public void cancel(String reason, Instant now) {
        transitionTo(OrderStatus.CANCELLED, now, reason);
    }

    private void transitionTo(OrderStatus target, Instant now, String reason) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("INVALID_ORDER_STATE",
                    "Order " + id + " is " + status + " and cannot become " + target);
        }
        status = target;
        updatedAt = now;
        history.add(new StatusChange(target, now, reason));
    }

    public UUID getId() {
        return id;
    }

    /** Caller's idempotency reference (the master-service checkout ID). Unique per order. */
    public String getReference() {
        return reference;
    }

    /** True if this order was created from exactly the given content (used for idempotent replays). */
    public boolean hasSameContent(String customerId, String currency, List<OrderLine> lines) {
        return this.customerId.equals(customerId) && this.currency.equals(currency) && this.lines.equals(lines);
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCurrency() {
        return currency;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<StatusChange> getHistory() {
        return List.copyOf(history);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
