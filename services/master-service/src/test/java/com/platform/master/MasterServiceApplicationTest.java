package com.platform.master;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MasterServiceApplicationTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void startsWithProbesAndCorrelation() {
        ResponseEntity<String> liveness = http.getForEntity("/actuator/health/liveness", String.class);

        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveness.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
        assertThat(http.getForEntity("/actuator/health/readiness", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unknownRoutesUseThePlatformErrorContract() {
        ResponseEntity<JsonNode> response = http.getForEntity("/api/v1/does-not-exist", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asText()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().get("correlationId").asText()).isNotBlank();
    }

    @Test
    void orderHistoryRequiresACustomer() {
        ResponseEntity<JsonNode> response = http.getForEntity("/api/v1/orders", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asText()).isEqualTo("MISSING_PARAMETER");
    }
}
