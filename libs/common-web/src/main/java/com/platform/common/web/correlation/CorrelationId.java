package com.platform.common.web.correlation;

import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Correlation ID conventions shared by all services.
 *
 * <p>A correlation ID identifies one business request end-to-end (browser → gateway → master →
 * services → Kafka). It is carried in the {@value #HEADER} header and stored in the logging MDC under
 * {@value #MDC_KEY}, so every log line written while serving the request contains it.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Restrictive on purpose: prevents log injection and unbounded header values. */
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    private CorrelationId() {
    }

    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }

    static boolean isValid(String candidate) {
        return candidate != null && VALID.matcher(candidate).matches();
    }
}
