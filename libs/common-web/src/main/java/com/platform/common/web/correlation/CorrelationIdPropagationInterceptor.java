package com.platform.common.web.correlation;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Forwards the current request's correlation ID on outgoing HTTP calls, so one ID follows a request
 * through every service it touches.
 */
public class CorrelationIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        CorrelationId.current().ifPresent(id -> {
            if (!request.getHeaders().containsKey(CorrelationId.HEADER)) {
                request.getHeaders().set(CorrelationId.HEADER, id);
            }
        });
        return execution.execute(request, body);
    }
}
