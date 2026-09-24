package com.platform.order.service;

import com.platform.common.web.error.ResourceNotFoundException;
import com.platform.order.api.dto.CreateOrderRequest;
import com.platform.order.domain.Order;
import com.platform.order.domain.OrderLine;
import com.platform.order.domain.OrderStatus;
import com.platform.order.repository.OrderRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final Clock clock;

    public OrderService(OrderRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Order create(CreateOrderRequest request) {
        List<OrderLine> lines = request.lines().stream()
                .map(l -> new OrderLine(l.productId(), l.productName(), l.quantity(), l.unitPrice()))
                .toList();
        return repository.save(new Order(UUID.randomUUID(), request.customerId(), request.currency(), lines, clock.instant()));
    }

    public Order get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    public Page<Order> search(String customerId, OrderStatus status, Pageable pageable) {
        return repository.search(customerId, status, pageable);
    }

    public Order confirm(UUID id) {
        Order order = get(id);
        order.confirm(clock.instant());
        return repository.save(order);
    }

    public Order cancel(UUID id, String reason) {
        Order order = get(id);
        order.cancel(reason, clock.instant());
        return repository.save(order);
    }
}
