package com.platform.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentApiTest {

    @Autowired
    private TestRestTemplate http;

    private ResponseEntity<JsonNode> pay(String key, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return http.postForEntity("/api/v1/payments", new HttpEntity<>(body, headers), JsonNode.class);
    }

    @Test
    void idempotentPaymentFlow() {
        Map<String, Object> body = Map.of("orderId", "order-1", "amount", "99.80", "currency", "USD", "paymentMethodToken", "tok_visa");

        ResponseEntity<JsonNode> first = pay("idem-key-0001", body);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().get("status").asText()).isEqualTo("COMPLETED");
        assertThat(first.getBody().toString()).doesNotContain("tok_visa");

        ResponseEntity<JsonNode> retry = pay("idem-key-0001", body);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.getBody().get("id")).isEqualTo(first.getBody().get("id"));

        JsonNode list = http.getForObject("/api/v1/payments?orderId=order-1", JsonNode.class);
        assertThat(list.get("totalElements").asInt()).as("retry did not create a second payment").isEqualTo(1);
    }

    @Test
    void declinedTokenProducesFailedPayment() {
        ResponseEntity<JsonNode> response = pay("idem-key-0002",
                Map.of("orderId", "order-2", "amount", "10", "currency", "USD", "paymentMethodToken", "tok_decline"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status").asText()).isEqualTo("FAILED");
        assertThat(response.getBody().get("failureReason").asText()).isEqualTo("card_declined");
    }

    @Test
    void rejectsMissingKeyAndRawCardNumbers() {
        Map<String, Object> body = Map.of("orderId", "o", "amount", "10", "currency", "USD", "paymentMethodToken", "tok_visa");
        assertThat(pay(null, body).getBody().get("code").asText()).isEqualTo("MISSING_HEADER");
        assertThat(pay("bad key!", body).getBody().get("code").asText()).isEqualTo("VALIDATION_ERROR");

        ResponseEntity<JsonNode> pan = pay("idem-key-0003",
                Map.of("orderId", "o", "amount", "10", "currency", "USD", "paymentMethodToken", "4111111111111111"));
        assertThat(pan.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pan.getBody().toString()).doesNotContain("4111111111111111");
    }
}
