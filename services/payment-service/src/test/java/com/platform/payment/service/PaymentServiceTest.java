package com.platform.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.platform.common.web.error.BusinessRuleException;
import com.platform.common.web.error.ConflictException;
import com.platform.payment.api.dto.CreatePaymentRequest;
import com.platform.payment.domain.Payment;
import com.platform.payment.domain.PaymentStatus;
import com.platform.payment.gateway.PaymentGateway;
import com.platform.payment.gateway.PaymentGateway.ChargeResult;
import com.platform.payment.repository.DuplicateIdempotencyKeyException;
import com.platform.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String KEY = "key-00000001";

    @Mock
    private PaymentRepository repository;

    @Mock
    private PaymentGateway gateway;

    private PaymentService service;

    private final CreatePaymentRequest request =
            new CreatePaymentRequest("order-1", new BigDecimal("99.80"), "USD", "tok_visa");

    @BeforeEach
    void setUp() {
        service = new PaymentService(repository, gateway, Clock.systemUTC());
    }

    private Payment stored(PaymentStatus status, CreatePaymentRequest r) {
        Payment p = new Payment(UUID.randomUUID(), r.orderId(), r.amount(), r.currency(), KEY,
                PaymentService.fingerprint(r), Instant.now());
        if (status == PaymentStatus.COMPLETED) {
            p.complete("sim_1", Instant.now());
        }
        return p;
    }

    @Test
    void newKeyChargesOnceAndRecordsResult() {
        given(repository.findByIdempotencyKey(KEY)).willReturn(Optional.empty());
        given(repository.insert(any())).willAnswer(inv -> inv.getArgument(0));
        given(repository.update(any())).willAnswer(inv -> inv.getArgument(0));
        given(gateway.charge(any(), any(), anyString(), anyString())).willReturn(new ChargeResult.Approved("sim_123"));

        PaymentOutcome outcome = service.pay(KEY, request);

        assertThat(outcome.replayed()).isFalse();
        assertThat(outcome.payment().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(outcome.payment().getProviderReference()).isEqualTo("sim_123");
    }

    @Test
    void declinedPaymentIsRecordedAsFailed() {
        given(repository.findByIdempotencyKey(KEY)).willReturn(Optional.empty());
        given(repository.insert(any())).willAnswer(inv -> inv.getArgument(0));
        given(repository.update(any())).willAnswer(inv -> inv.getArgument(0));
        given(gateway.charge(any(), any(), anyString(), anyString())).willReturn(new ChargeResult.Declined("card_declined"));

        Payment payment = service.pay(KEY, request).payment();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("card_declined");
    }

    @Test
    void repeatedKeyReplaysWithoutCallingProvider() {
        Payment completed = stored(PaymentStatus.COMPLETED, request);
        given(repository.findByIdempotencyKey(KEY)).willReturn(Optional.of(completed));

        PaymentOutcome outcome = service.pay(KEY, request);

        assertThat(outcome.replayed()).isTrue();
        assertThat(outcome.payment()).isSameAs(completed);
        verify(gateway, never()).charge(any(), any(), anyString(), anyString());
    }

    @Test
    void reusingKeyForDifferentRequestIsRejected() {
        given(repository.findByIdempotencyKey(KEY)).willReturn(Optional.of(stored(PaymentStatus.COMPLETED, request)));
        CreatePaymentRequest different = new CreatePaymentRequest("order-1", new BigDecimal("1.00"), "USD", "tok_visa");

        assertThatThrownBy(() -> service.pay(KEY, different))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void concurrentDuplicateLosingTheInsertRaceNeverCharges() {
        given(repository.findByIdempotencyKey(KEY))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(stored(PaymentStatus.PENDING, request)));
        given(repository.insert(any())).willThrow(new DuplicateIdempotencyKeyException(KEY));

        assertThatThrownBy(() -> service.pay(KEY, request))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("PAYMENT_IN_PROGRESS");
        verify(gateway, never()).charge(any(), any(), anyString(), anyString());
    }

    @Test
    void fingerprintIgnoresAmountFormattingButNotValue() {
        assertThat(PaymentService.fingerprint(request)).isEqualTo(PaymentService.fingerprint(
                new CreatePaymentRequest("order-1", new BigDecimal("99.8"), "USD", "tok_visa")));
        assertThat(PaymentService.fingerprint(request)).isNotEqualTo(PaymentService.fingerprint(
                new CreatePaymentRequest("order-1", new BigDecimal("99.81"), "USD", "tok_visa")));
    }

    @Test
    void requestToStringNeverContainsToken() {
        assertThat(request.toString()).doesNotContain("tok_visa");
    }
}
