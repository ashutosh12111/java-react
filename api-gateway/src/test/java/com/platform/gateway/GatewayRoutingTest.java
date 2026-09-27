package com.platform.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Real HTTP end to end: WebTestClient talks to the gateway on its random port (NOT a mock server,
 * which would skip CORS/host handling) and the gateway proxies to WireMock upstreams.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRoutingTest {

    @RegisterExtension
    static WireMockExtension master = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @RegisterExtension
    static WireMockExtension product = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry registry) {
        registry.add("MASTER_SERVICE_URL", master::baseUrl);
        registry.add("PRODUCT_SERVICE_URL", product::baseUrl);
        registry.add("USER_SERVICE_URL", () -> "http://127.0.0.1:" + closedPort()); // nothing listens here
        registry.add("spring.cloud.gateway.server.webflux.httpclient.response-timeout", () -> "500ms");
    }

    @Autowired
    private WebTestClient web;


    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void routesCheckoutToMasterAndAssignsCorrelationId() {
        master.stubFor(post("/api/v1/orders").willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBody("{\"orderId\":\"o-1\"}")));

        web.post().uri("/api/v1/orders")
                .header("Idempotency-Key", "key-00000001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerId\":\"c-1\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("X-Correlation-Id", "[0-9a-f-]{36}")
                .expectBody().jsonPath("$.orderId").isEqualTo("o-1");

        master.verify(postRequestedFor(urlEqualTo("/api/v1/orders"))
                .withHeader("X-Correlation-Id", matching("[0-9a-f-]{36}"))
                .withHeader("Idempotency-Key", equalTo("key-00000001")));
    }

    @Test
    void keepsAValidClientCorrelationId() {
        product.stubFor(get("/api/v1/products/P100").willReturn(okJson("{\"id\":\"P100\"}")));

        web.get().uri("/api/v1/products/P100").header("X-Correlation-Id", "browser-trace-42")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "browser-trace-42");
    }

    @ParameterizedTest(name = "{0} {1} is not public")
    @CsvSource({
            "GET,/api/v1/payments",
            "POST,/api/v1/payments",
            "POST,/api/v1/reservations",
            "PUT,/api/v1/inventory/P100",
            "POST,/api/v1/notifications",
            "POST,/api/v1/orders/o-1/confirm",
            "POST,/api/v1/orders/o-1/cancel",
            "POST,/api/v1/products",
            "DELETE,/api/v1/products/P100",
            "GET,/api/v1/users"})
    void internalEndpointsAreNotExposed(String method, String path) {
        web.method(HttpMethod.valueOf(method)).uri(path)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.correlationId").exists();
    }

    @Test
    void slowUpstreamIs504InThePlatformErrorFormat() {
        product.stubFor(get("/api/v1/products").willReturn(okJson("{}").withFixedDelay(2_000)));

        web.get().uri("/api/v1/products")
                .exchange()
                .expectStatus().isEqualTo(504)
                .expectBody().jsonPath("$.code").isEqualTo("GATEWAY_TIMEOUT");
    }

    @Test
    void downUpstreamIs503() {
        web.get().uri("/api/v1/users/u-1")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void corsAllowsOnlyTheConfiguredFrontendOrigin() {
        web.options().uri("/api/v1/orders")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Idempotency-Key,Content-Type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");

        web.options().uri("/api/v1/orders")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void exposesHealthProbes() {
        web.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
        web.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
    }
}
