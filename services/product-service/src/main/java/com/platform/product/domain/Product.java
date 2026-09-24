package com.platform.product.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A catalog entry. The ID is the business SKU (e.g. {@code P100}) because that is what clients,
 * inventory and orders refer to.
 */
public class Product {

    private final String id;
    private String name;
    private String description;
    private Money price;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public Product(String id, String name, String description, Money price, boolean active, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.name = name;
        this.description = description;
        this.price = Objects.requireNonNull(price);
        this.active = active;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String description, Money price, boolean active, Instant now) {
        this.name = name;
        this.description = description;
        this.price = Objects.requireNonNull(price);
        this.active = active;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Money getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Convenience for sorting by price amount. */
    public BigDecimal getPriceAmount() {
        return price.amount();
    }
}
