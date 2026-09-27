package com.platform.master.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.web.error.ApiException;
import com.platform.master.client.DownstreamUnavailableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckoutServiceTest {

    private final OrderPlacementService placement = mock(OrderPlacementService.class);
    private final CheckoutService service = new CheckoutService(placement,
            new InMemoryCheckoutIdempotencyStore(Clock.systemUTC()), new ObjectMapper());

    private static PlaceOrderRequest request(int quantity) {
        return new PlaceOrderRequest("customer-1", List.of(new PlaceOrderRequest.Item("P100", quantity)), "tok_visa");
    }

    private static PlaceOrderResponse response() {
        return new PlaceOrderResponse(UUID.randomUUID(), "CONFIRMED", "customer-1", "USD", BigDecimal.TEN, List.of(),
                new PlaceOrderResponse.Payment(UUID.randomUUID(), "COMPLETED", "sim_1"), List.of());
    }

    @Test
    void replaysSuccessfulCheckoutWithoutRunningItAgain() {
        PlaceOrderResponse placed = response();
        given(placement.place(anyString(), any())).willReturn(placed);

        var first = service.checkout("key-00000001", request(1));
        var second = service.checkout("key-00000001", request(1));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.response()).isEqualTo(placed);
        verify(placement, times(1)).place(anyString(), any());
    }

    @Test
    void replaysFinalClientErrors() {
        given(placement.place(anyString(), any())).willThrow(new PaymentDeclinedException(UUID.randomUUID(), "card_declined"));

        assertThatThrownBy(() -> service.checkout("key-00000002", request(1))).isInstanceOf(PaymentDeclinedException.class);
        assertThatThrownBy(() -> service.checkout("key-00000002", request(1)))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("PAYMENT_DECLINED");
        verify(placement, times(1)).place(anyString(), any());
    }

    @Test
    void transientFailuresCanBeRetriedWithTheSameKey() {
        given(placement.place(anyString(), any()))
                .willThrow(new DownstreamUnavailableException("payment-service", "timeout", null))
                .willReturn(response());

        assertThatThrownBy(() -> service.checkout("key-00000003", request(1))).isInstanceOf(DownstreamUnavailableException.class);
        assertThat(service.checkout("key-00000003", request(1)).replayed()).isFalse();
        verify(placement, times(2)).place(anyString(), any());
    }

    @Test
    void reusingAKeyForADifferentBasketIsRejected() {
        given(placement.place(anyString(), any())).willReturn(response());
        service.checkout("key-00000004", request(1));

        assertThatThrownBy(() -> service.checkout("key-00000004", request(2)))
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void checkoutIdIsDeterministicAndScopedPerCustomer() {
        assertThat(CheckoutService.checkoutId("c1", "k")).isEqualTo(CheckoutService.checkoutId("c1", "k"));
        assertThat(CheckoutService.checkoutId("c1", "k")).isNotEqualTo(CheckoutService.checkoutId("c2", "k"));
    }

    @Test
    void retriesUseTheSameCheckoutIdSoDownstreamStepsDeduplicate() {
        given(placement.place(anyString(), any()))
                .willThrow(new DownstreamUnavailableException("order-service", "timeout", null))
                .willReturn(response());
        String expected = CheckoutService.checkoutId("customer-1", "key-00000005");

        assertThatThrownBy(() -> service.checkout("key-00000005", request(1)));
        service.checkout("key-00000005", request(1));

        verify(placement, times(2)).place(org.mockito.ArgumentMatchers.eq(expected), any());
    }
}
