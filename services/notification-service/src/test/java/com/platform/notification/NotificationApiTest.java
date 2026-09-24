package com.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
class NotificationApiTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void acceptsAndReportsDelivery() {
        ResponseEntity<JsonNode> accepted = http.postForEntity("/api/v1/notifications", Map.of(
                "channel", "EMAIL", "recipient", "ada@example.com", "template", "ORDER_CONFIRMED",
                "variables", Map.of("orderId", "o-1"), "reference", "o-1"), JsonNode.class);

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(accepted.getHeaders().getLocation()).isNotNull();
        assertThat(accepted.getBody().get("status").asText()).isEqualTo("SENT");
        assertThat(accepted.getBody().get("recipient").asText()).isEqualTo("a***@example.com");

        ResponseEntity<JsonNode> failed = http.postForEntity("/api/v1/notifications", Map.of(
                "channel", "EMAIL", "recipient", "bounce@mail.invalid", "template", "WELCOME", "reference", "o-1"), JsonNode.class);
        assertThat(failed.getBody().get("status").asText()).isEqualTo("FAILED");

        JsonNode byOrder = http.getForObject("/api/v1/notifications?reference=o-1&status=SENT", JsonNode.class);
        assertThat(byOrder.get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void unknownTemplateIsRejected() {
        ResponseEntity<JsonNode> response = http.postForEntity("/api/v1/notifications", Map.of(
                "channel", "EMAIL", "recipient", "ada@example.com", "template", "NOPE"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asText()).isEqualTo("MALFORMED_REQUEST");
    }
}
