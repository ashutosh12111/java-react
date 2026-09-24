package com.platform.order.domain;

import java.time.Instant;

public record StatusChange(OrderStatus status, Instant at, String reason) {
}
