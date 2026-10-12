package com.qrserve.shared.exceptions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private int status;
        private String message;
        private LocalDateTime timestamp;
    }

    /**
     * {@link ErrorResponse} plus the name of the dependency that failed.
     *
     * <p>A separate type rather than a nullable field on ErrorResponse: this
     * module has no Jackson on its compile classpath (so no
     * {@code @JsonInclude} to suppress a null), and every other error body
     * would otherwise have gained an always-null {@code upstream} key.
     */
    @Data
    @AllArgsConstructor
    public static class UpstreamErrorResponse {
        private int status;
        private String message;
        private LocalDateTime timestamp;
        /** A stable identifier, never a URL — see {@link UpstreamServiceException}. */
        private String upstream;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(UnauthorizedException ex) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("message", "Validation failed");
        response.put("errors", fieldErrors);
        response.put("timestamp", LocalDateTime.now());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Authorization failure — 403, not 500.
     *
     * <p>Without this handler, {@code @PreAuthorize} rejections (which arrive as
     * {@link AuthorizationDeniedException}, a subclass) fell through to the
     * catch-all and were returned as {@code 500 "Access Denied"}. That is wrong
     * twice over: a client cannot tell "you may not do this" from "the server is
     * broken", and it made a deliberate policy decision look like an outage.
     *
     * <p>The message is fixed rather than {@code ex.getMessage()} so it does not
     * disclose which rule denied the request.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Authorization denied: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "You do not have permission to perform this action.",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    /** Missing or invalid credentials — 401, distinct from 403 above. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Authentication is required.",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    /** A subscription-tier limit, not a role/permission failure — carries its real message, unlike handleAccessDenied above. */
    @ExceptionHandler(TierLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleTierLimitExceeded(TierLimitExceededException ex) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    /**
     * A named dependency failed — 502, and the body says which one.
     *
     * <p>Deliberately more informative than the 503 below: the message is
     * authored by the throw site (never an exception message from a client
     * library, which can carry internal URLs) and {@code upstream} is a
     * fixed identifier. The full cause and target URL go to the log only.
     */
    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<UpstreamErrorResponse> handleUpstreamServiceException(UpstreamServiceException ex) {
        log.error("Upstream dependency '{}' failed", ex.getUpstream(), ex);
        UpstreamErrorResponse response = new UpstreamErrorResponse(
                HttpStatus.BAD_GATEWAY.value(),
                ex.getMessage(),
                LocalDateTime.now(),
                ex.getUpstream()
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailableException(ServiceUnavailableException ex) {
        // Log the real cause; return a stable message. The client only needs to know
        // it should retry, not which upstream host failed.
        log.error("Downstream dependency unavailable", ex);
        ErrorResponse response = new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "A required service is temporarily unavailable. Please retry.",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** Malformed or unreadable request body (bad JSON, wrong field types) — 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        // The parser's message can echo payload fragments; keep it in the log.
        log.debug("Unreadable request body: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    /** A path/query parameter that cannot be converted to its declared type — 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'");
    }

    /** Upload over the configured multipart limit — 413. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.CONTENT_TOO_LARGE, "Uploaded file is too large");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        // Spring MVC's own client-error exceptions (missing parameter, 405, 415,
        // 404 NoResourceFoundException, ResponseStatusException, ...) all
        // implement org.springframework.web.ErrorResponse and carry their status.
        // Matched via that interface because ResponseEntityExceptionHandler and
        // NoResourceFoundException live in spring-webmvc, which is not on this
        // module's compile classpath.
        if (ex instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = springError.getStatusCode();
            String detail = springError.getBody().getDetail();
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String message = detail != null ? detail
                    : resolved != null ? resolved.getReasonPhrase() : "Request rejected";
            return new ResponseEntity<>(
                    new ErrorResponse(status.value(), message, LocalDateTime.now()),
                    springError.getHeaders(), status);
        }
        // Persistence conflicts — 409. Matched by class name: spring-tx/spring-orm
        // are not on this module's compile classpath, but are present at runtime
        // in every JPA service. Fixed messages: the cause can carry SQL.
        if (hasType(ex, DATA_INTEGRITY_VIOLATION)) {
            log.warn("Data integrity violation: {}", ex.getMessage());
            return error(HttpStatus.CONFLICT, "The request conflicts with existing data.");
        }
        if (hasType(ex, OPTIMISTIC_LOCKING_FAILURE)) {
            log.warn("Optimistic locking failure: {}", ex.getMessage());
            return error(HttpStatus.CONFLICT,
                    "The resource was modified by another request. Reload and try again.");
        }
        // Never return ex.getMessage() for an unhandled exception: it leaks SQL
        // fragments, downstream URLs, and stack hints to the client. Log the real
        // cause server-side and return a fixed, generic message instead.
        log.error("Unhandled exception", ex);
        ErrorResponse response = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected server error occurred",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** Also covers its subclasses, e.g. DuplicateKeyException. */
    static final String DATA_INTEGRITY_VIOLATION = "org.springframework.dao.DataIntegrityViolationException";
    /** Parent of spring-orm's ObjectOptimisticLockingFailureException. */
    static final String OPTIMISTIC_LOCKING_FAILURE = "org.springframework.dao.OptimisticLockingFailureException";

    /** True if {@code ex}'s class, or any superclass, has the given fully-qualified name. */
    static boolean hasType(Throwable ex, String className) {
        for (Class<?> c = ex.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return new ResponseEntity<>(new ErrorResponse(status.value(), message, LocalDateTime.now()), status);
    }
}
