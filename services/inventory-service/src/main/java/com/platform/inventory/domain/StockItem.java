package com.platform.inventory.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Stock for one product. {@code available} can be promised to new orders; {@code reserved} is held
 * for orders that have not completed yet. Invariant: neither quantity is ever negative.
 */
public class StockItem {

    private final String productId;
    private int available;
    private int reserved;
    private Instant updatedAt;

    public StockItem(String productId, int available, Instant now) {
        this.productId = Objects.requireNonNull(productId);
        this.available = requireNonNegative(available);
        this.updatedAt = now;
    }

    public boolean canReserve(int quantity) {
        return available >= quantity;
    }

    public void reserve(int quantity, Instant now) {
        if (!canReserve(quantity)) {
            throw new IllegalStateException("Insufficient stock for " + productId);
        }
        available -= quantity;
        reserved += quantity;
        updatedAt = now;
    }

    /** Returns reserved units to the sellable pool (order cancelled / payment failed). */
    public void release(int quantity, Instant now) {
        reserved = requireNonNegative(reserved - quantity);
        available += quantity;
        updatedAt = now;
    }

    /** Reserved units have been sold and leave the warehouse. */
    public void commit(int quantity, Instant now) {
        reserved = requireNonNegative(reserved - quantity);
        updatedAt = now;
    }

    public void restock(int newAvailable, Instant now) {
        available = requireNonNegative(newAvailable);
        updatedAt = now;
    }

    private static int requireNonNegative(int value) {
        if (value < 0) {
            throw new IllegalStateException("Stock quantity cannot be negative");
        }
        return value;
    }

    public String getProductId() {
        return productId;
    }

    public int getAvailable() {
        return available;
    }

    public int getReserved() {
        return reserved;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
