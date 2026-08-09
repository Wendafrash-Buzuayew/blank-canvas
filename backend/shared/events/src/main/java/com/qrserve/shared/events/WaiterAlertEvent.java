package com.qrserve.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notification payload sent to the waiter alert STOMP topic.
 *
 * alertType values: ORDER_READY, CALL_WAITER, REQUEST_BILL
 *
 * This event is constructed by the notification-service from incoming
 * domain events (OrderStatusUpdatedEvent with READY, CustomerRequestEvent)
 * and forwarded to connected WebSocket clients via Redis Pub/Sub.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterAlertEvent {
    private UUID alertId;
    private UUID merchantId;
    private Long branchId;
    private UUID orderId;
    private String orderNumber;
    private Long tableId;
    private String tableNumber;
    private String alertType;
    private String message;
    private LocalDateTime createdAt;

    // Telemetry context for distributed tracing (Grafana/Loki)
    private String traceId;
    private String spanId;
}
