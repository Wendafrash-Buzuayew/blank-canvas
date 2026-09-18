package com.qrserve.shared.exceptions;

/**
 * A named third-party or inter-service dependency failed the request.
 *
 * <p>Maps to HTTP 502 in {@code GlobalExceptionHandler}, and unlike
 * {@link ServiceUnavailableException} (503, deliberately anonymous) the
 * response names WHICH dependency broke via {@link #getUpstream()}.
 *
 * <p>Why this exists: a Safaricom ETHQR call was misconfigured to a dead
 * mock host, and every attempt surfaced as a flat
 * {@code 503 "A required service is temporarily unavailable. Please retry."}
 * — a message that is true of any of the three remote calls behind that one
 * endpoint and so identified none of them. Diagnosing it required reading
 * container logs. A 502 naming {@code safaricom-ethqr} would have pointed
 * straight at it.
 *
 * <p>502 rather than 503 is also the honest status: 503 claims THIS service
 * is unavailable and invites a retry, while a broken upstream (or a
 * misconfigured URL) is a bad gateway that retrying will not fix.
 *
 * <p>{@code upstream} is a stable identifier — {@code "safaricom-ethqr"},
 * {@code "merchant-service"} — never a URL or host. Internal topology stays
 * in the logs; the client gets a label it can branch on and quote in a bug
 * report.
 */
public class UpstreamServiceException extends RuntimeException {

    private final String upstream;

    public UpstreamServiceException(String upstream, String message) {
        super(message);
        this.upstream = upstream;
    }

    public UpstreamServiceException(String upstream, String message, Throwable cause) {
        super(message, cause);
        this.upstream = upstream;
    }

    public String getUpstream() {
        return upstream;
    }
}
