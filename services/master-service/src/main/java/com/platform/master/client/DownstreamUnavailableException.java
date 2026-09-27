package com.platform.master.client;

import com.platform.common.web.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A downstream call failed without a definitive answer: connection refused, timeout, 5xx, or an
 * unreadable response. For non-idempotent operations the outcome is UNKNOWN (the work may or may
 * not have happened), which is why the orchestrator never compensates a payment on this error.
 */
public class DownstreamUnavailableException extends ApiException {

    private final String service;
    private final String detail;

    public DownstreamUnavailableException(String service, String detail, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "DOWNSTREAM_UNAVAILABLE",
                service + " is temporarily unavailable. Please retry later.", cause);
        this.service = service;
        this.detail = detail;
    }

    public String service() {
        return service;
    }

    /** Internal diagnostic (for logs only, never returned to clients). */
    public String detail() {
        return detail;
    }
}
