package com.platform.order.repository;

import com.platform.order.domain.Order;
import com.platform.order.domain.OrderStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Persistence port; in-memory in Phase 1, Spring Data JPA over PostgreSQL from Phase 3. */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    Optional<Order> findByReference(String reference);

    Page<Order> search(String customerId, OrderStatus status, Pageable pageable);
}
