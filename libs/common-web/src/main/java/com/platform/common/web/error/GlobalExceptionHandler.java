package com.platform.common.web.error;

import com.platform.common.web.correlation.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every exception into the platform {@link ApiError} contract.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so that all standard Spring MVC failures (405, 415,
 * missing headers, malformed JSON, ...) are covered, then funnels them through
 * {@link #handleExceptionInternal} to render one consistent body. Internal details (stack traces,
 * Jackson messages, SQL) are never returned to the client; they are logged with the correlation ID.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        log.debug("Request rejected: {} {}", ex.code(), ex.getMessage());
        return build(ex.status(), ex.code(), ex.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Invalid request", request.getRequestURI(), violations);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Quote the correlationId when reporting this issue.",
                request.getRequestURI(), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldViolation> violations = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> violations.add(toViolation(e)));
        ex.getBindingResult().getGlobalErrors().forEach(e ->
                violations.add(new ApiError.FieldViolation(e.getObjectName(), e.getDefaultMessage())));
        return asObject(build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Invalid request", path(request), violations));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ApiError.FieldViolation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return asObject(build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Invalid request", path(request), violations));
    }

    /** Catch-all for the standard Spring MVC exceptions handled by the superclass. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String message = switch (ex) {
            // Never leak parser internals (class names, field offsets) to clients.
            case HttpMessageNotReadableException ignored -> "Malformed or unreadable request body";
            case MethodArgumentTypeMismatchException mismatch ->
                    "Parameter '" + mismatch.getName() + "' has an invalid value";
            default -> body instanceof ProblemDetail pd && pd.getDetail() != null ? pd.getDetail() : status.getReasonPhrase();
        };
        String code = switch (ex) {
            case HttpMessageNotReadableException ignored -> "MALFORMED_REQUEST";
            case MissingRequestHeaderException ignored -> "MISSING_HEADER";
            case MissingServletRequestParameterException ignored -> "MISSING_PARAMETER";
            case MethodArgumentTypeMismatchException ignored -> "INVALID_PARAMETER";
            case HttpRequestMethodNotSupportedException ignored -> "METHOD_NOT_ALLOWED";
            case HttpMediaTypeNotSupportedException ignored -> "UNSUPPORTED_MEDIA_TYPE";
            case NoResourceFoundException ignored -> "NOT_FOUND";
            default -> status.name();
        };
        ResponseEntity<ApiError> response = build(status, code, message, path(request), List.of());
        return ResponseEntity.status(status).headers(headers).body(response.getBody());
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status, String code, String message, String path, List<ApiError.FieldViolation> violations) {
        ApiError body = new ApiError(Instant.now(clock), status.value(), code, message, path,
                CorrelationId.current().orElse(null), violations);
        return ResponseEntity.status(status).body(body);
    }

    private static ApiError.FieldViolation toViolation(FieldError error) {
        return new ApiError.FieldViolation(error.getField(), error.getDefaultMessage());
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResponseEntity<Object> asObject(ResponseEntity<ApiError> response) {
        return (ResponseEntity) response;
    }
}
