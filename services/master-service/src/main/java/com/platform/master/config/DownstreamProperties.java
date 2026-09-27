package com.platform.master.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Where each downstream service lives and how long we are willing to wait for it. URLs come from the
 * environment (e.g. {@code ORDER_SERVICE_URL}) so the same artifact runs locally, in Compose and in Kubernetes.
 */
@Validated
@ConfigurationProperties("platform.downstream")
public record DownstreamProperties(
        @NotNull @Valid Endpoint user,
        @NotNull @Valid Endpoint product,
        @NotNull @Valid Endpoint inventory,
        @NotNull @Valid Endpoint order,
        @NotNull @Valid Endpoint payment,
        @NotNull @Valid Endpoint notification) {

    /**
     * @param connectTimeout how long to wait for a TCP connection. Short: a refused/unreachable host fails fast.
     * @param readTimeout    how long to wait for the response. Sized per service from its expected latency.
     */
    public record Endpoint(
            @NotNull URI baseUrl,
            @DefaultValue("1s") Duration connectTimeout,
            @DefaultValue("3s") Duration readTimeout) {
    }
}
