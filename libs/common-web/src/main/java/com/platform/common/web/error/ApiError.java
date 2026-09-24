package com.platform.common.web.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * The single error contract returned by every service in the platform.
 *
 * <p>Clients (and the master-service when it aggregates downstream failures) can rely on this
 * shape regardless of which service produced the error.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        List<FieldViolation> errors) {

    public ApiError {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** A single invalid input. The rejected value is deliberately NOT echoed back (it may be sensitive). */
    public record FieldViolation(String field, String message) {
    }
}
