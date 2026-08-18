package com.qrserve.shared.exceptions;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }

    /** Preserves the underlying cause; without this, wrapping loses the stack trace. */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
