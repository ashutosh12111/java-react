package com.platform.master.checkout;

import com.platform.common.web.error.ApiException;
import com.platform.common.web.error.BusinessRuleException;
import com.platform.common.web.error.ConflictException;
import com.platform.master.client.DownstreamRejectedException;
import com.platform.master.client.DownstreamUnavailableException;
import com.platform.master.client.InventoryClient;
import com.platform.master.client.InventoryClient.Reservation;
import com.platform.master.client.NotificationClient;
import com.platform.master.client.OrderClient;
import com.platform.master.client.OrderClient.CreateOrder;
import com.platform.master.client.OrderClient.OrderView;
import com.platform.master.client.PaymentClient;
import com.platform.master.client.PaymentClient.PaymentRequest;
import com.platform.master.client.PaymentClient.PaymentView;
import com.platform.master.client.ProductClient;
import com.platform.master.client.ProductClient.PriceQuote;
import com.platform.master.client.ProductClient.QuoteItem;
import com.platform.master.client.UserClient;
import com.platform.master.client.UserClient.CustomerValidation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a checkout across five services. It decides WHICH service to call, in WHAT order and
 * WHAT to do when a step fails. It never answers a domain question itself: eligibility, prices, stock
 * and payment rules all stay in their owning services.
 *
 * <pre>
 * 1. user-service      validate customer           (fail → 422, nothing to undo)
 * 2. product-service   price the basket            (fail → 422, nothing to undo)
 * 3. inventory-service reserve stock               (fail → 422, nothing to undo)
 * 4. order-service     create PENDING order        (definitive fail → release stock)
 * 5. payment-service   charge                      (declined → cancel order + release stock → 402)
 *                                                  (unknown  → change nothing → 503, client retries)
 * 6. order-service     confirm order     ┐
 * 7. inventory-service commit stock      ├ payment is taken: failures here become warnings,
 * 8. notification      confirmation email┘ never a failed checkout
 * </pre>
 *
 * <p>Every mutating step is idempotent, keyed on the {@code checkoutId}, so a retried checkout safely
 * resumes where the previous attempt stopped. Compensation happens only after DEFINITIVE failures.
 * Phase 7 replaces the in-request compensation with an event-driven saga and an outbox, which also
 * covers the master-service crashing mid-checkout.
 */
@Service
public class OrderPlacementService {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacementService.class);

    private final UserClient users;
    private final ProductClient products;
    private final InventoryClient inventory;
    private final OrderClient orders;
    private final PaymentClient payments;
    private final NotificationClient notifications;

    public OrderPlacementService(UserClient users, ProductClient products, InventoryClient inventory,
                                 OrderClient orders, PaymentClient payments, NotificationClient notifications) {
        this.users = users;
        this.products = products;
        this.inventory = inventory;
        this.orders = orders;
        this.payments = payments;
        this.notifications = notifications;
    }

    public PlaceOrderResponse place(String checkoutId, PlaceOrderRequest request) {
        CustomerValidation customer = validateCustomer(request.customerId());
        PriceQuote quote = priceBasket(request.items());
        Reservation reservation = reserveStock(checkoutId, quote);
        OrderView order = createOrder(checkoutId, request.customerId(), quote, reservation);
        PaymentView payment = pay(checkoutId, order, reservation, request.paymentMethodToken(), customer);

        List<String> warnings = new ArrayList<>();
        OrderView confirmed = confirmOrder(order, warnings);
        commitStock(reservation, warnings);
        notifyCustomer(customer, "ORDER_CONFIRMED", confirmed,
                Map.of("total", confirmed.totalAmount().toPlainString(), "currency", confirmed.currency()), warnings);
        log.info("Checkout {} placed order {} with status {}", checkoutId, confirmed.id(), confirmed.status());
        return PlaceOrderResponse.of(confirmed, payment, warnings);
    }

    private CustomerValidation validateCustomer(String customerId) {
        CustomerValidation customer;
        try {
            customer = users.validate(customerId);
        } catch (DownstreamRejectedException e) {
            if (e.is(404) || e.is(400)) {
                throw new BusinessRuleException("CUSTOMER_NOT_FOUND", "Customer '" + customerId + "' does not exist");
            }
            throw e;
        }
        if (!customer.eligibleForOrders()) {
            throw new BusinessRuleException("CUSTOMER_NOT_ELIGIBLE", "Customer '" + customerId + "' may not place orders");
        }
        return customer;
    }

    private PriceQuote priceBasket(List<PlaceOrderRequest.Item> items) {
        try {
            return products.quote(items.stream().map(i -> new QuoteItem(i.productId(), i.quantity())).toList());
        } catch (DownstreamRejectedException e) {
            throw passThroughBusinessRule(e);
        }
    }

    private Reservation reserveStock(String checkoutId, PriceQuote quote) {
        Reservation reservation;
        try {
            reservation = inventory.reserve(checkoutId, quote.lines().stream()
                    .map(l -> new InventoryClient.Line(l.productId(), l.quantity()))
                    .toList());
        } catch (DownstreamRejectedException e) {
            throw passThroughBusinessRule(e);
        }
        if ("RELEASED".equals(reservation.status())) {
            // A previous attempt with this key was compensated; it must not be resurrected.
            throw new ConflictException("CHECKOUT_ALREADY_CANCELLED",
                    "This checkout was cancelled. Start a new checkout with a new Idempotency-Key.");
        }
        return reservation;
    }

    private OrderView createOrder(String checkoutId, String customerId, PriceQuote quote, Reservation reservation) {
        List<CreateOrder.Line> lines = quote.lines().stream()
                .map(l -> new CreateOrder.Line(l.productId(), l.name(), l.quantity(), l.unitPrice()))
                .toList();
        try {
            return orders.create(new CreateOrder(checkoutId, customerId, quote.currency(), lines));
        } catch (DownstreamRejectedException definitive) {
            releaseQuietly(reservation);
            throw definitive;
        }
        // DownstreamUnavailableException propagates WITHOUT compensation: the order may exist. A retry
        // resumes; an abandoned checkout's reservation is reclaimed by reservation expiry (Phase 7).
    }

    private PaymentView pay(String checkoutId, OrderView order, Reservation reservation, String token,
                            CustomerValidation customer) {
        PaymentView payment;
        try {
            // The checkout ID doubles as the payment Idempotency-Key: repeating this call can never double-charge.
            payment = payments.pay(checkoutId,
                    new PaymentRequest(order.id().toString(), order.totalAmount(), order.currency(), token));
        } catch (DownstreamUnavailableException unknown) {
            log.warn("Payment outcome unknown for order {} ({}); leaving order and reservation untouched",
                    order.id(), unknown.detail());
            throw new PaymentOutcomeUnknownException(order.id());
        } catch (DownstreamRejectedException e) {
            if (e.is(409)) {
                throw new PaymentOutcomeUnknownException(order.id()); // same checkout is being charged right now
            }
            compensate(order, reservation, "payment_rejected: " + e.downstreamCode());
            throw e;
        }

        if (payment.isFailed()) {
            compensate(order, reservation, "payment_declined: " + payment.failureReason());
            notifyCustomer(customer, "PAYMENT_FAILED", order, Map.of(), new ArrayList<>());
            throw new PaymentDeclinedException(order.id(), payment.failureReason());
        }
        if (!payment.isCompleted()) {
            throw new PaymentOutcomeUnknownException(order.id());
        }
        return payment;
    }

    private OrderView confirmOrder(OrderView order, List<String> warnings) {
        if (!order.isPending()) {
            return order; // resumed checkout that was already confirmed
        }
        try {
            return orders.confirm(order.id());
        } catch (ApiException e) {
            log.error("Payment taken but order {} could not be confirmed; needs reconciliation", order.id(), e);
            warnings.add("ORDER_CONFIRMATION_PENDING");
            return order;
        }
    }

    private void commitStock(Reservation reservation, List<String> warnings) {
        try {
            inventory.commit(reservation.id());
        } catch (ApiException e) {
            log.error("Reservation {} could not be committed; needs reconciliation", reservation.id(), e);
            warnings.add("STOCK_COMMIT_PENDING");
        }
    }

    /** Notifications are best-effort: a missing email must never fail a paid order. */
    private void notifyCustomer(CustomerValidation customer, String template, OrderView order,
                                Map<String, String> extra, List<String> warnings) {
        Map<String, String> variables = new HashMap<>(extra);
        variables.put("orderId", order.id().toString());
        try {
            notifications.sendEmail(customer.email(), template, variables, order.id().toString());
        } catch (ApiException e) {
            log.warn("Notification {} for order {} not sent: {}", template, order.id(), e.getMessage());
            warnings.add("NOTIFICATION_NOT_SENT");
        }
    }

    private void compensate(OrderView order, Reservation reservation, String reason) {
        if (order.isPending()) {
            try {
                orders.cancel(order.id(), reason);
            } catch (ApiException e) {
                log.error("COMPENSATION FAILED: could not cancel order {} ({})", order.id(), reason, e);
            }
        }
        releaseQuietly(reservation);
    }

    private void releaseQuietly(Reservation reservation) {
        try {
            inventory.release(reservation.id());
        } catch (ApiException e) {
            log.error("COMPENSATION FAILED: could not release reservation {}", reservation.id(), e);
        }
    }

    /** 422s from domain services carry user-facing codes (e.g. INSUFFICIENT_STOCK); forward them as-is. */
    private static ApiException passThroughBusinessRule(DownstreamRejectedException e) {
        if (e.is(422)) {
            return new BusinessRuleException(e.downstreamCode(), e.downstreamMessage());
        }
        return e;
    }
}
