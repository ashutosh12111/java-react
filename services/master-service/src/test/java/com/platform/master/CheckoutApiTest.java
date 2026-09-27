package com.platform.master;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs the real master-service over HTTP against WireMock standing in for all five downstream
 * services. Verifies behaviour that unit tests cannot: timeouts, connection faults, error-body
 * translation, header propagation and replay semantics.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CheckoutApiTest {

    @RegisterExtension
    static WireMockExtension downstream = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        for (String service : List.of("user", "product", "inventory", "order", "payment", "notification")) {
            registry.add("platform.downstream." + service + ".base-url", downstream::baseUrl);
        }
        registry.add("platform.downstream.payment.read-timeout", () -> "500ms");
    }

    private static final String ORDER_ID = "3f2b6a3e-0000-4000-8000-000000000001";
    private static final String RESERVATION_ID = "3f2b6a3e-0000-4000-8000-000000000002";

    @Autowired
    private TestRestTemplate http;

    @BeforeEach
    void stubHappyPath() {
        downstream.stubFor(get(urlPathMatching("/api/v1/users/.+/validation")).willReturn(okJson("""
                {"userId":"customer-1","status":"ACTIVE","eligibleForOrders":true,"email":"ada@example.com"}""")));
        downstream.stubFor(post("/api/v1/products/price-quotes").willReturn(okJson("""
                {"currency":"USD","total":99.80,
                 "lines":[{"productId":"P100","name":"Keyboard","quantity":2,"unitPrice":49.90,"lineTotal":99.80}]}""")));
        downstream.stubFor(post("/api/v1/reservations").willReturn(okJson(reservation("RESERVED"))));
        downstream.stubFor(post(urlPathMatching("/api/v1/reservations/.+/(commit|release)")).willReturn(okJson(reservation("COMMITTED"))));
        downstream.stubFor(post("/api/v1/orders").willReturn(okJson(order("PENDING"))));
        downstream.stubFor(post(urlPathMatching("/api/v1/orders/.+/confirm")).willReturn(okJson(order("CONFIRMED"))));
        downstream.stubFor(post(urlPathMatching("/api/v1/orders/.+/cancel")).willReturn(okJson(order("CANCELLED"))));
        downstream.stubFor(post("/api/v1/payments").willReturn(okJson(payment("COMPLETED", null))));
        downstream.stubFor(post("/api/v1/notifications").willReturn(aResponse().withStatus(202)));
    }

    private static String reservation(String status) {
        return "{\"id\":\"" + RESERVATION_ID + "\",\"reference\":\"x\",\"status\":\"" + status + "\"}";
    }

    private static String order(String status) {
        return """
                {"id":"%s","reference":"x","customerId":"customer-1","status":"%s","currency":"USD","totalAmount":99.80,
                 "lines":[{"productId":"P100","productName":"Keyboard","quantity":2,"unitPrice":49.90,"lineTotal":99.80}],
                 "history":[],"createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}"""
                .formatted(ORDER_ID, status);
    }

    private static String payment(String status, String failureReason) {
        return """
                {"id":"%s","orderId":"%s","amount":99.80,"currency":"USD","status":"%s","failureReason":%s,
                 "createdAt":"2026-01-01T00:00:00Z"}"""
                .formatted(UUID.randomUUID(), ORDER_ID, status, failureReason == null ? "null" : "\"" + failureReason + "\"");
    }

    private ResponseEntity<JsonNode> checkout(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-Id", "test-corr-0001");
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        Map<String, Object> body = Map.of("customerId", "customer-1",
                "items", List.of(Map.of("productId", "P100", "quantity", 2)), "paymentMethodToken", "tok_visa");
        return http.postForEntity("/api/v1/orders", new HttpEntity<>(body, headers), JsonNode.class);
    }

    @Test
    void placesOrderAcrossAllServicesAndPropagatesHeaders() {
        ResponseEntity<JsonNode> response = checkout("checkout-key-0001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasPath("/api/v1/orders/" + ORDER_ID);
        assertThat(response.getBody().get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(response.getBody().get("warnings")).isEmpty();

        // The correlation ID reached every service; the payment carried a deterministic idempotency key.
        downstream.verify(getRequestedFor(urlPathMatching("/api/v1/users/.+/validation"))
                .withHeader("X-Correlation-Id", equalTo("test-corr-0001")));
        downstream.verify(postRequestedFor(urlPathEqualTo("/api/v1/payments"))
                .withHeader("X-Correlation-Id", equalTo("test-corr-0001"))
                .withHeader("Idempotency-Key", matching("[0-9a-f-]{36}")));
        downstream.verify(postRequestedFor(urlPathMatching("/api/v1/reservations/.+/commit")));
    }

    @Test
    void repeatedCheckoutIsReplayedWithoutTouchingDownstream() {
        checkout("checkout-key-0002");
        ResponseEntity<JsonNode> replay = checkout("checkout-key-0002");

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        downstream.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/payments")));
    }

    @Test
    void declinedPaymentCompensatesAndReturns402() {
        downstream.stubFor(post("/api/v1/payments").willReturn(okJson(payment("FAILED", "card_declined"))));

        ResponseEntity<JsonNode> response = checkout("checkout-key-0003");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(response.getBody().get("code").asText()).isEqualTo("PAYMENT_DECLINED");
        downstream.verify(postRequestedFor(urlPathMatching("/api/v1/orders/.+/cancel")));
        downstream.verify(postRequestedFor(urlPathMatching("/api/v1/reservations/.+/release")));
    }

    @Test
    void paymentTimeoutReturns503AndLeavesStateForTheRetry() {
        downstream.stubFor(post("/api/v1/payments").willReturn(okJson(payment("COMPLETED", null)).withFixedDelay(2_000)));

        ResponseEntity<JsonNode> response = checkout("checkout-key-0004");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("code").asText()).isEqualTo("PAYMENT_OUTCOME_UNKNOWN");
        downstream.verify(0, postRequestedFor(urlPathMatching("/api/v1/orders/.+/cancel")));
        downstream.verify(0, postRequestedFor(urlPathMatching("/api/v1/reservations/.+/release")));

        // The payment-service finished in the meantime: a retry with the same key completes the checkout.
        downstream.stubFor(post("/api/v1/payments").willReturn(okJson(payment("COMPLETED", null))));
        assertThat(checkout("checkout-key-0004").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void businessRejectionsFromDomainServicesArePassedThrough() {
        downstream.stubFor(post("/api/v1/reservations").willReturn(aResponse().withStatus(422)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":422,\"code\":\"INSUFFICIENT_STOCK\",\"message\":\"Insufficient stock for products: [P100]\"}")));

        ResponseEntity<JsonNode> response = checkout("checkout-key-0005");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(response.getBody().get("correlationId").asText()).isEqualTo("test-corr-0001");
        downstream.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/orders")));
    }

    @Test
    void brokenConnectionToADownstreamServiceIs503() {
        downstream.stubFor(get(urlPathMatching("/api/v1/users/.+/validation"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        ResponseEntity<JsonNode> response = checkout("checkout-key-0006");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("code").asText()).isEqualTo("DOWNSTREAM_UNAVAILABLE");
        assertThat(response.getBody().get("message").asText()).contains("user-service");
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        assertThat(checkout(null).getBody().get("code").asText()).isEqualTo("MISSING_HEADER");
    }

    @Test
    void orderDetailsDegradeGracefullyWhenPaymentServiceIsDown() {
        downstream.stubFor(get("/api/v1/orders/" + ORDER_ID).willReturn(okJson(order("CONFIRMED"))));
        downstream.stubFor(get(urlPathEqualTo("/api/v1/payments")).willReturn(aResponse().withStatus(500)));

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-Id", "test-corr-0002");
        ResponseEntity<JsonNode> response = http.exchange("/api/v1/orders/" + ORDER_ID, HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(response.getBody().get("payment").isNull()).isTrue();
        assertThat(response.getBody().get("warnings").get(0).asText()).isEqualTo("PAYMENT_STATUS_UNAVAILABLE");
        // Both parallel calls ran on other threads and still carried the correlation ID.
        downstream.verify(getRequestedFor(urlPathEqualTo("/api/v1/payments")).withHeader("X-Correlation-Id", equalTo("test-corr-0002")));
        downstream.verify(getRequestedFor(urlPathEqualTo("/api/v1/orders/" + ORDER_ID)).withHeader("X-Correlation-Id", equalTo("test-corr-0002")));
    }

    @Test
    void unknownOrderIs404() {
        downstream.stubFor(get(urlPathMatching("/api/v1/orders/.+")).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json").withBody("{\"code\":\"ORDER_NOT_FOUND\"}")));
        downstream.stubFor(get(urlPathEqualTo("/api/v1/payments")).willReturn(okJson("""
                {"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0,"first":true,"last":true,"sort":[]}""")));

        ResponseEntity<JsonNode> response = http.getForEntity("/api/v1/orders/" + UUID.randomUUID(), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ORDER_NOT_FOUND");
    }
}
