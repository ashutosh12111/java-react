package com.platform.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderApiTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void orderLifecycle() {
        Map<String, Object> request = Map.of("customerId", "customer-7", "currency", "USD", "lines",
                List.of(Map.of("productId", "P100", "productName", "Keyboard", "quantity", 2, "unitPrice", "49.90")));

        ResponseEntity<JsonNode> created = http.postForEntity("/api/v1/orders", request, JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("status").asText()).isEqualTo("PENDING");
        assertThat(created.getBody().get("totalAmount").decimalValue()).isEqualByComparingTo("99.80");
        String id = created.getBody().get("id").asText();

        assertThat(http.postForObject("/api/v1/orders/{id}/confirm", null, JsonNode.class, id)
                .get("status").asText()).isEqualTo("CONFIRMED");

        ResponseEntity<JsonNode> cancel = http.postForEntity("/api/v1/orders/{id}/cancel",
                Map.of("reason", "changed mind"), JsonNode.class, id);
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        JsonNode history = http.getForObject("/api/v1/orders?customerId=customer-7&status=CONFIRMED", JsonNode.class);
        assertThat(history.get("totalElements").asInt()).isEqualTo(1);
        assertThat(history.get("content").get(0).get("history").size()).isEqualTo(2);
    }
}
