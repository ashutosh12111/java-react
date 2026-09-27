package com.platform.master.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.master.config.DownstreamProperties.Endpoint;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.function.Supplier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Builds one {@link RestClient} per downstream service with explicit timeouts and uniform error
 * translation. The injected builder already carries the correlation-ID interceptor (common-web).
 */
@Component
public class DownstreamRestClientFactory {

    private final RestClient.Builder builder;
    private final ObjectMapper objectMapper;

    public DownstreamRestClientFactory(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.builder = builder;
        this.objectMapper = objectMapper;
    }

    public RestClient create(String service, Endpoint endpoint) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(endpoint.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(endpoint.readTimeout());

        return builder.clone()
                .baseUrl(endpoint.baseUrl().toString())
                .requestFactory(requestFactory)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw translate(service, response);
                })
                .build();
    }

    /**
     * Runs a call and converts transport-level failures (timeouts, refused connections, unreadable
     * bodies) into {@link DownstreamUnavailableException}.
     */
    public static <T> T call(String service, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException e) {
            throw new DownstreamUnavailableException(service, e.getMessage(), e);
        }
    }

    private RuntimeException translate(String service, ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.is5xxServerError()) {
            return new DownstreamUnavailableException(service, "HTTP " + status.value(), null);
        }
        String code = "HTTP_" + status.value();
        String message = null;
        try (InputStream body = response.getBody()) {
            JsonNode error = objectMapper.readTree(body);
            if (error != null && error.hasNonNull("code")) {
                code = error.get("code").asText();
                message = error.path("message").asText(null);
            }
        } catch (IOException unreadableBody) {
            // Keep the status-derived code; the status alone is enough to act on.
        }
        return new DownstreamRejectedException(service, status.value(), code, message);
    }
}
