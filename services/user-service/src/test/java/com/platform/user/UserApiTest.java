package com.platform.user;

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

/** Boots the whole service on a random port and exercises the public API over real HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserApiTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void userLifecycle() {
        ResponseEntity<JsonNode> created = http.postForEntity("/api/v1/users",
                Map.of("email", "grace@example.com", "firstName", "Grace", "lastName", "Hopper"), JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = created.getBody().get("id").asText();

        ResponseEntity<JsonNode> duplicate = http.postForEntity("/api/v1/users",
                Map.of("email", "GRACE@example.com", "firstName", "G", "lastName", "H"), JsonNode.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("code").asText()).isEqualTo("USER_EMAIL_ALREADY_EXISTS");

        JsonNode validation = http.getForObject("/api/v1/users/{id}/validation", JsonNode.class, id);
        assertThat(validation.get("eligibleForOrders").asBoolean()).isTrue();
        assertThat(validation.get("email").asText()).isEqualTo("grace@example.com");

        JsonNode page = http.getForObject("/api/v1/users?status=ACTIVE&size=10&sort=email,asc", JsonNode.class);
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        assertThat(page.get("sort").get(0).asText()).isEqualTo("email,asc");

        ResponseEntity<JsonNode> badSort = http.getForEntity("/api/v1/users?sort=passwordHash", JsonNode.class);
        assertThat(badSort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badSort.getBody().get("code").asText()).isEqualTo("INVALID_SORT_PROPERTY");
    }

    @Test
    void exposesSeparateLivenessAndReadinessProbes() {
        assertThat(http.getForEntity("/actuator/health/liveness", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/actuator/health/readiness", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void publishesOpenApiDocument() {
        JsonNode doc = http.getForObject("/v3/api-docs", JsonNode.class);
        assertThat(doc.get("info").get("title").asText()).isEqualTo("user-service");
        assertThat(doc.get("paths").has("/api/v1/users/{id}")).isTrue();
    }
}
