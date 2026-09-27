package com.platform.master.query;

import com.platform.common.web.error.ApiException;
import com.platform.common.web.error.ResourceNotFoundException;
import com.platform.common.web.paging.PageResponse;
import com.platform.common.web.paging.SortValidator;
import com.platform.master.client.DownstreamRejectedException;
import com.platform.master.client.OrderClient;
import com.platform.master.client.OrderClient.OrderView;
import com.platform.master.client.PaymentClient;
import com.platform.master.client.PaymentClient.PaymentView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * API aggregation: one client call, several services queried in parallel. The order is required;
 * payment data is optional, so if payment-service is down the order is still returned with a warning
 * instead of failing the whole request (graceful degradation).
 */
@Service
public class OrderQueryService {

    private static final Logger log = LoggerFactory.getLogger(OrderQueryService.class);
    private static final Set<String> SORTABLE = Set.of("createdAt", "totalAmount", "status");

    private final OrderClient orders;
    private final PaymentClient payments;
    private final AsyncTaskExecutor executor;

    public OrderQueryService(OrderClient orders, PaymentClient payments,
                             @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.orders = orders;
        this.payments = payments;
        this.executor = executor;
    }

    public OrderDetailsResponse get(UUID orderId) {
        CompletableFuture<OrderView> order = CompletableFuture.supplyAsync(() -> orders.get(orderId), executor);
        CompletableFuture<List<PaymentView>> paymentList =
                CompletableFuture.supplyAsync(() -> payments.findByOrder(orderId), executor);

        OrderView view;
        try {
            view = unwrap(order);
        } catch (DownstreamRejectedException e) {
            if (e.is(404)) {
                paymentList.cancel(true);
                throw new ResourceNotFoundException("Order", orderId);
            }
            throw e;
        }

        List<String> warnings = new ArrayList<>();
        PaymentView latestPayment = null;
        try {
            latestPayment = unwrap(paymentList).stream().findFirst().orElse(null);
        } catch (ApiException e) {
            log.warn("Payment status unavailable for order {}: {}", orderId, e.getMessage());
            warnings.add("PAYMENT_STATUS_UNAVAILABLE");
        }
        return OrderDetailsResponse.of(view, latestPayment, warnings);
    }

    public PageResponse<OrderSummaryResponse> list(String customerId, String status, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE);
        PageResponse<OrderView> page = orders.list(customerId, status, pageable);
        return new PageResponse<>(page.content().stream().map(OrderSummaryResponse::of).toList(), page.page(),
                page.size(), page.totalElements(), page.totalPages(), page.first(), page.last(), page.sort());
    }

    private static <T> T unwrap(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }
}
