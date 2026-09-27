package com.platform.order.service;

import com.platform.order.domain.Order;

/** @param created {@code false} when an existing order was returned for an idempotent retry */
public record OrderCreation(Order order, boolean created) {
}
