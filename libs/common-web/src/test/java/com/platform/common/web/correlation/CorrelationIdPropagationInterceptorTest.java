package com.platform.common.web.correlation;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CorrelationIdPropagationInterceptorTest {

    private final RestClient.Builder builder =
            RestClient.builder().requestInterceptor(new CorrelationIdPropagationInterceptor());
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RestClient client = builder.build();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void forwardsCurrentCorrelationId() {
        MDC.put(CorrelationId.MDC_KEY, "corr-abc-123");
        server.expect(header(CorrelationId.HEADER, "corr-abc-123")).andRespond(withSuccess());

        client.get().uri("http://downstream/x").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void sendsNothingOutsideARequest() {
        server.expect(headerDoesNotExist(CorrelationId.HEADER)).andRespond(withSuccess());

        client.get().uri("http://downstream/x").retrieve().toBodilessEntity();

        server.verify();
    }
}
