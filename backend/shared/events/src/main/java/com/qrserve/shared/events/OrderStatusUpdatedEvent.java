package com.qrserve.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusUpdatedEvent {
    private UUID orderId;
    private String orderNumber;
    private UUID merchantId;
    private Long branchId;
    private Long tableId;
    private String previousStatus;
    private String newStatus;

    // Telemetry context for distributed tracing (Grafana/Loki)
    private String traceId;
    private String spanId;
}
