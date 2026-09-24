package com.platform.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected, client-facing failures. Each subclass maps to exactly one HTTP status and
 * carries a stable, machine-readable {@code code} that clients can switch on.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
