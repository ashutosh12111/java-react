package com.platform.master.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.platform.common.web.error.ApiException;
import com.platform.common.web.error.BusinessRuleException;
import com.platform.common.web.error.ConflictException;
import com.platform.master.client.DownstreamRejectedException;
import com.platform.master.client.DownstreamUnavailableException;
import com.platform.master.client.InventoryClient;
import com.platform.master.client.InventoryClient.Reservation;
import com.platform.master.client.NotificationClient;
import com.platform.master.client.OrderClient;
import com.platform.master.client.OrderClient.OrderView;
import com.platform.master.client.PaymentClient;
import com.platform.master.client.PaymentClient.PaymentView;
import com.platform.master.client.ProductClient;
import com.platform.master.client.ProductClient.PriceQuote;
import com.platform.master.client.UserClient;
import com.platform.master.client.UserClient.CustomerValidation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderPlacementServiceTest {

    private static final String CHECKOUT = "checkout-1";
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID RESERVATION_ID = UUID.randomUUID();

    @Mock private UserClient users;
    @Mock private ProductClient products;
    @Mock private InventoryClient inventory;
    @Mock private OrderClient orders;
    @Mock private PaymentClient payments;
    @Mock private NotificationClient notifications;

    @InjectMocks
    private OrderPlacementService service;

    private final PlaceOrderRequest request =
            new PlaceOrderRequest("customer-1", List.of(new PlaceOrderRequest.Item("P100", 2)), "tok_visa");

    private static OrderView order(String status) {
        return new OrderView(ORDER_ID, CHECKOUT, "customer-1", status, "USD", new BigDecimal("99.80"),
                List.of(new OrderView.Line("P100", "Keyboard", 2, new BigDecimal("49.90"), new BigDecimal("99.80"))),
                List.of(), Instant.EPOCH, Instant.EPOCH);
    }

    private static PaymentView payment(String status, String failureReason) {
        return new PaymentView(UUID.randomUUID(), ORDER_ID.toString(), new BigDecimal("99.80"), "USD", status,
                "COMPLETED".equals(status) ? "sim_1" : null, failureReason, Instant.EPOCH);
    }

    private static DownstreamRejectedException rejected(String service, int status, String code) {
        return new DownstreamRejectedException(service, status, code, code + " message");
    }

    private static DownstreamUnavailableException unavailable(String service) {
        return new DownstreamUnavailableException(service, "read timed out", null);
    }

    /** Stubs steps 1-4 as successful. */
    @BeforeEach
    void happyPathUpToPayment() {
        lenient().when(users.validate("customer-1"))
                .thenReturn(new CustomerValidation("customer-1", "ACTIVE", true, "ada@example.com"));
        lenient().when(products.quote(anyList())).thenReturn(new PriceQuote("USD",
                List.of(new PriceQuote.Line("P100", "Keyboard", 2, new BigDecimal("49.90"), new BigDecimal("99.80"))),
                new BigDecimal("99.80")));
        lenient().when(inventory.reserve(eq(CHECKOUT), anyList()))
                .thenReturn(new Reservation(RESERVATION_ID, CHECKOUT, "RESERVED"));
        lenient().when(orders.create(any())).thenReturn(order("PENDING"));
    }

    @Test
    void happyPathConfirmsOrderCommitsStockAndNotifies() {
        given(payments.pay(eq(CHECKOUT), any())).willReturn(payment("COMPLETED", null));
        given(orders.confirm(ORDER_ID)).willReturn(order("CONFIRMED"));

        PlaceOrderResponse response = service.place(CHECKOUT, request);

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.payment().status()).isEqualTo("COMPLETED");
        assertThat(response.warnings()).isEmpty();
        verify(inventory).commit(RESERVATION_ID);
        verify(notifications).sendEmail(eq("ada@example.com"), eq("ORDER_CONFIRMED"), anyMap(), eq(ORDER_ID.toString()));
    }

    @Test
    void paymentIsChargedForTheOrderTotalWithTheCheckoutAsIdempotencyKey() {
        given(payments.pay(eq(CHECKOUT), any())).willReturn(payment("COMPLETED", null));
        given(orders.confirm(ORDER_ID)).willReturn(order("CONFIRMED"));

        service.place(CHECKOUT, request);

        verify(payments).pay(CHECKOUT, new PaymentClient.PaymentRequest(ORDER_ID.toString(), new BigDecimal("99.80"), "USD", "tok_visa"));
    }

    @Test
    void unknownCustomerFailsBeforeAnythingIsReserved() {
        given(users.validate("customer-1")).willThrow(rejected("user-service", 404, "USER_NOT_FOUND"));

        assertThatThrownBy(() -> service.place(CHECKOUT, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CUSTOMER_NOT_FOUND");
        verifyNoInteractions(inventory, orders, payments);
    }

    @Test
    void suspendedCustomerIsRejected() {
        given(users.validate("customer-1")).willReturn(new CustomerValidation("customer-1", "SUSPENDED", false, "a@b.io"));

        assertThatThrownBy(() -> service.place(CHECKOUT, request))
                .extracting("code").isEqualTo("CUSTOMER_NOT_ELIGIBLE");
        verifyNoInteractions(products, inventory);
    }

    @Test
    void insufficientStockIsPassedThroughAndNoOrderIsCreated() {
        given(inventory.reserve(eq(CHECKOUT), anyList())).willThrow(rejected("inventory-service", 422, "INSUFFICIENT_STOCK"));

        assertThatThrownBy(() -> service.place(CHECKOUT, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INSUFFICIENT_STOCK");
        verifyNoInteractions(orders, payments);
    }

    @Test
    void definitiveOrderFailureReleasesTheReservation() {
        given(orders.create(any())).willThrow(rejected("order-service", 400, "VALIDATION_ERROR"));

        assertThatThrownBy(() -> service.place(CHECKOUT, request)).isInstanceOf(DownstreamRejectedException.class);
        verify(inventory).release(RESERVATION_ID);
        verifyNoInteractions(payments);
    }

    @Test
    void orderServiceTimeoutDoesNotCompensateBecauseTheOrderMayExist() {
        given(orders.create(any())).willThrow(unavailable("order-service"));

        assertThatThrownBy(() -> service.place(CHECKOUT, request)).isInstanceOf(DownstreamUnavailableException.class);
        verify(inventory, never()).release(any());
    }

    @Test
    void declinedPaymentCancelsOrderReleasesStockAndReturns402() {
        given(payments.pay(eq(CHECKOUT), any())).willReturn(payment("FAILED", "card_declined"));

        assertThatThrownBy(() -> service.place(CHECKOUT, request))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessageContaining("card_declined");
        verify(orders).cancel(eq(ORDER_ID), startsWith("payment_declined"));
        verify(inventory).release(RESERVATION_ID);
        verify(orders, never()).confirm(any());
        verify(notifications).sendEmail(anyString(), eq("PAYMENT_FAILED"), anyMap(), anyString());
    }

    @Test
    void paymentTimeoutNeverCompensatesAndNeverRetriesTheCharge() {
        given(payments.pay(eq(CHECKOUT), any())).willThrow(unavailable("payment-service"));

        assertThatThrownBy(() -> service.place(CHECKOUT, request))
                .isInstanceOf(PaymentOutcomeUnknownException.class)
                .extracting("code").isEqualTo("PAYMENT_OUTCOME_UNKNOWN");
        verify(payments).pay(eq(CHECKOUT), any()); // exactly once
        verify(orders, never()).cancel(any(), anyString());
        verify(inventory, never()).release(any());
    }

    @Test
    void concurrentPaymentInProgressIsTreatedAsUnknown() {
        given(payments.pay(eq(CHECKOUT), any())).willThrow(rejected("payment-service", 409, "PAYMENT_IN_PROGRESS"));

        assertThatThrownBy(() -> service.place(CHECKOUT, request)).isInstanceOf(PaymentOutcomeUnknownException.class);
        verify(inventory, never()).release(any());
    }

    @Test
    void failuresAfterPaymentBecomeWarningsNotErrors() {
        given(payments.pay(eq(CHECKOUT), any())).willReturn(payment("COMPLETED", null));
        given(orders.confirm(ORDER_ID)).willReturn(order("CONFIRMED"));
        given(inventory.commit(RESERVATION_ID)).willThrow(unavailable("inventory-service"));
        willThrow(unavailable("notification-service")).given(notifications).sendEmail(anyString(), anyString(), anyMap(), anyString());

        PlaceOrderResponse response = service.place(CHECKOUT, request);

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.warnings()).containsExactly("STOCK_COMMIT_PENDING", "NOTIFICATION_NOT_SENT");
    }

    @Test
    void failedCompensationIsLoggedButDoesNotHideTheDecline() {
        given(payments.pay(eq(CHECKOUT), any())).willReturn(payment("FAILED", "insufficient_funds"));
        given(orders.cancel(eq(ORDER_ID), anyString())).willThrow(unavailable("order-service"));

        assertThatThrownBy(() -> service.place(CHECKOUT, request)).isInstanceOf(PaymentDeclinedException.class);
        verify(inventory).release(RESERVATION_ID); // still attempted
    }

    @Test
    void resumedCheckoutSkipsAlreadyConfirmedOrder() {
        given(orders.create(any())).willReturn(order("CONFIRMED"));
        given(payments.pay(eq(CHECKOUT), any())).willReturn(payment("COMPLETED", null));

        assertThat(service.place(CHECKOUT, request).status()).isEqualTo("CONFIRMED");
        verify(orders, never()).confirm(any());
    }

    @Test
    void releasedReservationMeansTheCheckoutWasAlreadyCancelled() {
        given(inventory.reserve(eq(CHECKOUT), anyList())).willReturn(new Reservation(RESERVATION_ID, CHECKOUT, "RELEASED"));

        assertThatThrownBy(() -> service.place(CHECKOUT, request))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("CHECKOUT_ALREADY_CANCELLED");
        verifyNoInteractions(orders, payments);
    }
}
