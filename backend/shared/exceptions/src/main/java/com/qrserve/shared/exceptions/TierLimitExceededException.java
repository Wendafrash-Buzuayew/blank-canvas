package com.qrserve.shared.exceptions;

/**
 * A subscription-tier limit blocked the request (e.g. a Free-tier merchant
 * hitting the 1-branch cap). Distinct from {@link BusinessException} (400,
 * a request that is simply invalid) — this is a request that would be valid
 * on a higher tier, so the client needs a 403 it can distinguish and turn
 * into an upgrade prompt rather than a form-validation error.
 */
public class TierLimitExceededException extends RuntimeException {
    public TierLimitExceededException(String message) {
        super(message);
    }
}
