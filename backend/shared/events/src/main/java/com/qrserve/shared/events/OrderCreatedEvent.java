package com.qrserve.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private UUID orderId;
    private String orderNumber;
    private UUID merchantId;
    private Long branchId;
    private Long tableId;
    private String tableNumber;
    private String customerName;
    private BigDecimal totalAmount;
    private String note;
    private LocalDateTime createdAt;

    // Telemetry context for distributed tracing (Grafana/Loki)
    private String traceId;
    private String spanId;
}
