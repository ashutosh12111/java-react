package com.platform.master.api;

import com.platform.common.web.paging.PageResponse;
import com.platform.master.checkout.CheckoutService;
import com.platform.master.checkout.CheckoutService.CheckoutResult;
import com.platform.master.checkout.PlaceOrderRequest;
import com.platform.master.checkout.PlaceOrderResponse;
import com.platform.master.query.OrderDetailsResponse;
import com.platform.master.query.OrderQueryService;
import com.platform.master.query.OrderSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Public order API, reached through the API gateway. */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
public class OrderController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REPLAYED = "Idempotent-Replayed";

    private final CheckoutService checkoutService;
    private final OrderQueryService queryService;

    public OrderController(CheckoutService checkoutService, OrderQueryService queryService) {
        this.checkoutService = checkoutService;
        this.queryService = queryService;
    }

    @PostMapping
    @Operation(summary = "Place an order (checkout)", description = """
            Orchestrates user → product → inventory → order → payment → notification.
            Requires an Idempotency-Key (one per checkout attempt; reuse it when retrying).
            201 placed · 200 replay of a finished checkout · 402 PAYMENT_DECLINED · 409 CHECKOUT_IN_PROGRESS ·
            422 CUSTOMER_NOT_FOUND / CUSTOMER_NOT_ELIGIBLE / PRODUCT_UNAVAILABLE / INSUFFICIENT_STOCK / IDEMPOTENCY_KEY_REUSED ·
            503 DOWNSTREAM_UNAVAILABLE / PAYMENT_OUTCOME_UNKNOWN (retry with the same key).""")
    public ResponseEntity<PlaceOrderResponse> placeOrder(
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$") String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        CheckoutResult result = checkoutService.checkout(idempotencyKey, request);
        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAYED, "true").body(result.response());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(result.response().orderId()).toUri();
        return ResponseEntity.created(location).body(result.response());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Order details, aggregated with payment status")
    public OrderDetailsResponse get(@PathVariable UUID id) {
        return queryService.get(id);
    }

    @GetMapping
    @Operation(summary = "A customer's order history", description = "Sortable by: createdAt, totalAmount, status")
    public PageResponse<OrderSummaryResponse> list(
            @RequestParam @NotBlank String customerId,
            @RequestParam(required = false) String status,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return queryService.list(customerId, status, pageable);
    }
}
