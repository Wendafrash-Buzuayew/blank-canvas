package com.qrserve.shared.exceptions;

/**
 * A downstream dependency was unreachable or returned a server error.
 *
 * <p>Exists to separate "the thing you asked for does not exist" from "we could not
 * reach the service that knows". Several services previously caught every exception
 * from an inter-service call and rethrew {@link ResourceNotFoundException}, so a
 * connection refused or a 500 was reported to the client as "Table not found".
 *
 * <p>Maps to HTTP 503 in {@code GlobalExceptionHandler}.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
