package com.platform.payment.api;

import com.platform.common.web.paging.PageResponse;
import com.platform.payment.api.dto.CreatePaymentRequest;
import com.platform.payment.api.dto.PaymentResponse;
import com.platform.payment.domain.PaymentStatus;
import com.platform.payment.service.PaymentOutcome;
import com.platform.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
public class PaymentController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REPLAYED = "Idempotent-Replayed";

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Initiate a payment",
            description = "Requires an Idempotency-Key header. 201 for a new payment (check `status` for the outcome); "
                    + "200 with `Idempotent-Replayed: true` when the same request is retried.")
    public ResponseEntity<PaymentResponse> pay(
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentOutcome outcome = paymentService.pay(idempotencyKey, request);
        PaymentResponse body = PaymentResponse.from(outcome.payment());
        if (outcome.replayed()) {
            return ResponseEntity.ok().header(REPLAYED, "true").body(body);
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment status")
    public PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.from(paymentService.get(id));
    }

    @GetMapping
    @Operation(summary = "List payments", description = "Filter by order and/or status. Sortable by: createdAt, amount")
    public PageResponse<PaymentResponse> list(
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) PaymentStatus status,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(paymentService.search(orderId, status, pageable), PaymentResponse::from);
    }
}
