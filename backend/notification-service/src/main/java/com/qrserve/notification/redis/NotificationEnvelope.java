package com.qrserve.notification.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper published over Redis Pub/Sub so every notification-service
 * instance can forward the payload to its own locally connected STOMP
 * clients (cross-pod broadcast).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEnvelope {

    /** STOMP destination, e.g. {@code /topic/orders/{orderId}}. */
    private String destination;

    /** Logical event name, e.g. ORDER_CREATED, ORDER_READY, CUSTOMER_REQUEST. */
    private String eventType;

    /** JSON-serialisable payload forwarded verbatim to subscribers. */
    private Object payload;

    /** Telemetry context preserved end-to-end for Grafana/Loki. */
    private String traceId;
    private String spanId;
}
