package com.platform.user.domain;

public enum UserStatus {
    ACTIVE,
    SUSPENDED;

    public boolean canPlaceOrders() {
        return this == ACTIVE;
    }
}
