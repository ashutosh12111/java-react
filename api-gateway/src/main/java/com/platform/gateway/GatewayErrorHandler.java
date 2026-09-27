package com.platform.gateway;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Renders gateway-level failures (no route, upstream down, upstream too slow) in the same JSON shape
 * the services use, so clients handle one error format no matter where a request failed.
 */
@Component
@Order(-2) // before Spring Boot's DefaultErrorWebExceptionHandler
class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    private final ObjectMapper objectMapper;

    GatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ErrorBody(Instant timestamp, int status, String code, String message, String path, String correlationId,
                     List<Object> errors) {
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        HttpStatus status;
        String code;
        String message;
        if (hasCause(ex, ConnectException.class) || hasCause(ex, UnknownHostException.class)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = "SERVICE_UNAVAILABLE";
            message = "The service is temporarily unavailable. Please retry later.";
        } else if (ex instanceof ResponseStatusException rse && rse.getStatusCode().value() == 504) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            code = "GATEWAY_TIMEOUT";
            message = "The service did not respond in time.";
        } else if (ex instanceof ResponseStatusException rse && rse.getStatusCode().is4xxClientError()) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            code = status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : status.name();
            message = status == HttpStatus.NOT_FOUND ? "No public API matches this path and method" : status.getReasonPhrase();
        } else {
            status = HttpStatus.BAD_GATEWAY;
            code = "BAD_GATEWAY";
            message = "The request could not be completed.";
        }
        String correlationId = exchange.getAttribute(CorrelationIdWebFilter.ATTRIBUTE);
        if (status.is5xxServerError()) {
            log.warn("Gateway error {} on {} correlationId={}: {}", status.value(),
                    exchange.getRequest().getPath(), correlationId, ex.toString());
        }

        ErrorBody body = new ErrorBody(Instant.now(), status.value(), code, message,
                exchange.getRequest().getPath().value(), correlationId, List.of());
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.fromSupplier(() -> toBuffer(exchange, body)));
    }

    private DataBuffer toBuffer(ServerWebExchange exchange, ErrorBody body) {
        try {
            return exchange.getResponse().bufferFactory().wrap(objectMapper.writeValueAsBytes(body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
