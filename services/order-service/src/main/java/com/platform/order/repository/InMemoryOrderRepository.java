package com.platform.order.repository;

import com.platform.common.web.paging.InMemoryPageSupport;
import com.platform.order.domain.Order;
import com.platform.order.domain.OrderStatus;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class InMemoryOrderRepository implements OrderRepository {

    private static final Map<String, Comparator<Order>> SORTABLE = Map.of(
            "createdAt", Comparator.comparing(Order::getCreatedAt),
            "totalAmount", Comparator.comparing(Order::getTotalAmount),
            "status", Comparator.comparing(Order::getStatus));

    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();

    @Override
    public Order save(Order order) {
        orders.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public Page<Order> search(String customerId, OrderStatus status, Pageable pageable) {
        return InMemoryPageSupport.page(
                orders.values().stream()
                        .filter(o -> customerId == null || o.getCustomerId().equals(customerId))
                        .filter(o -> status == null || o.getStatus() == status),
                pageable, SORTABLE, Comparator.comparing(Order::getCreatedAt).thenComparing(Order::getId));
    }
}
