package com.platform.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InventoryApiTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void reservationStatusCodes() {
        http.exchange("/api/v1/inventory/P100", HttpMethod.PUT, new HttpEntity<>(Map.of("available", 5)), JsonNode.class);
        Map<String, Object> body = Map.of("reference", "order-42", "lines", List.of(Map.of("productId", "P100", "quantity", 2)));

        ResponseEntity<JsonNode> created = http.postForEntity("/api/v1/reservations", body, JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> replay = http.postForEntity("/api/v1/reservations", body, JsonNode.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().get("id")).isEqualTo(created.getBody().get("id"));

        ResponseEntity<JsonNode> tooMany = http.postForEntity("/api/v1/reservations",
                Map.of("reference", "order-43", "lines", List.of(Map.of("productId", "P100", "quantity", 99))), JsonNode.class);
        assertThat(tooMany.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(tooMany.getBody().get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");

        String id = created.getBody().get("id").asText();
        assertThat(http.postForObject("/api/v1/reservations/{id}/release", null, JsonNode.class, id)
                .get("status").asText()).isEqualTo("RELEASED");
        assertThat(http.postForEntity("/api/v1/reservations/{id}/commit", null, JsonNode.class, id)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        JsonNode stock = http.getForObject("/api/v1/inventory/P100", JsonNode.class);
        assertThat(stock.get("available").asInt()).isEqualTo(5);
    }
}
