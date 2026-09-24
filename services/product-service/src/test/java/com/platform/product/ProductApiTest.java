package com.platform.product;

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
class ProductApiTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void catalogAndPricing() {
        for (var p : List.of(Map.of("id", "P100", "name", "Keyboard", "price", "49.90", "currency", "USD", "active", true),
                             Map.of("id", "P200", "name", "Mouse", "price", "19.95", "currency", "USD", "active", true))) {
            assertThat(http.postForEntity("/api/v1/products", p, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        assertThat(http.postForEntity("/api/v1/products",
                Map.of("id", "P100", "name", "Dup", "price", "1", "currency", "USD", "active", true), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        JsonNode page = http.getForObject("/api/v1/products?sort=price,desc", JsonNode.class);
        assertThat(page.get("content").get(0).get("id").asText()).isEqualTo("P100");

        JsonNode filtered = http.getForObject("/api/v1/products?q=mou", JsonNode.class);
        assertThat(filtered.get("totalElements").asInt()).isEqualTo(1);

        JsonNode quote = http.postForObject("/api/v1/products/price-quotes",
                Map.of("items", List.of(Map.of("productId", "P100", "quantity", 2))), JsonNode.class);
        assertThat(quote.get("total").decimalValue()).isEqualByComparingTo("99.80");

        ResponseEntity<JsonNode> unknown = http.postForEntity("/api/v1/products/price-quotes",
                Map.of("items", List.of(Map.of("productId", "NOPE", "quantity", 1))), JsonNode.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(unknown.getBody().get("code").asText()).isEqualTo("PRODUCT_UNAVAILABLE");
    }
}
