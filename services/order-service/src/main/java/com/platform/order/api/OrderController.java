package com.platform.order.api;

import com.platform.common.web.paging.PageResponse;
import com.platform.order.api.dto.CancelOrderRequest;
import com.platform.order.api.dto.CreateOrderRequest;
import com.platform.order.api.dto.OrderResponse;
import com.platform.order.domain.Order;
import com.platform.order.domain.OrderStatus;
import com.platform.order.service.OrderCreation;
import com.platform.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Create an order in PENDING state",
            description = "Idempotent per `reference`: 201 when created, 200 when the same request is repeated, "
                    + "409 ORDER_REFERENCE_CONFLICT if the reference was used for a different order.")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderCreation creation = orderService.create(request);
        Order order = creation.order();
        if (!creation.created()) {
            return ResponseEntity.ok(OrderResponse.from(order));
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(order.getId()).toUri();
        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order, including its status history")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(orderService.get(id));
    }

    @GetMapping
    @Operation(summary = "Order history", description = "Filter by customer and/or status. Sortable by: createdAt, totalAmount, status")
    public PageResponse<OrderResponse> list(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(orderService.search(customerId, status, pageable), OrderResponse::from);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm a pending order", description = "409 INVALID_ORDER_STATE if the order is not PENDING")
    public OrderResponse confirm(@PathVariable UUID id) {
        return OrderResponse.from(orderService.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending order", description = "409 INVALID_ORDER_STATE if the order is not PENDING")
    public OrderResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelOrderRequest request) {
        return OrderResponse.from(orderService.cancel(id, request.reason()));
    }
}
