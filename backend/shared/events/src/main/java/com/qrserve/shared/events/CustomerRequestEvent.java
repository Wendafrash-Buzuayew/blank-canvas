package com.qrserve.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when a customer makes a service request from their table.
 * Published to Kafka topic "customer-request" by merchant-service.
 *
 * requestType values: CALL_WAITER, WATER_REQUEST, REQUEST_BILL, ASSISTANCE
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRequestEvent {
    private UUID requestId;
    private UUID merchantId;
    private Long branchId;
    private Long tableId;
    private String tableNumber;
    private String requestType;
    private String note;
    private LocalDateTime createdAt;

    // Telemetry context for distributed tracing (Grafana/Loki)
    private String traceId;
    private String spanId;
}
