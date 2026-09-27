package com.platform.gateway;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * The gateway is where a request's correlation ID is born (unless the browser supplied a valid one).
 * It is forwarded to the routed service, echoed to the client, and written to one access-log line per
 * request, so the gateway log is the entry point for tracing any request through the platform.
 *
 * <p>A {@link WebFilter} rather than a gateway {@code GlobalFilter}: global filters only run for
 * requests that matched a route, but rejected requests (404, CORS) need an ID too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdWebFilter implements WebFilter {

    static final String HEADER = "X-Correlation-Id";
    static final String ATTRIBUTE = CorrelationIdWebFilter.class.getName() + ".id";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");
    private static final Logger accessLog = LoggerFactory.getLogger("gateway.access");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER, correlationId))
                .build();
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        exchange.getAttributes().put(ATTRIBUTE, correlationId);

        long start = System.nanoTime();
        return chain.filter(exchange.mutate().request(request).build())
                .doFinally(signal -> accessLog.info("{} {} -> {} in {}ms correlationId={}",
                        request.getMethod(), request.getPath().value(), exchange.getResponse().getStatusCode(),
                        (System.nanoTime() - start) / 1_000_000, correlationId));
    }
}
