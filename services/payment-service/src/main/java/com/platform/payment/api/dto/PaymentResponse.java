package com.platform.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.platform.payment.domain.Payment;
import com.platform.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(
        UUID id,
        String orderId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String providerReference,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getCurrency(), p.getStatus(),
                p.getProviderReference(), p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
