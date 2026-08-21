package com.qrserve.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A terminal label now identifies this table. Immutable identity, never revised.
 *
 * <p>order-service projects these into a local lookup table so that resolving a
 * payment webhook's terminal label needs no call to merchant-service. A synchronous
 * call there would put a second service in the money path, where it could fail while
 * a guest's payment is already in flight.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableQrProvisionedEvent {
    private String terminalLabel;
    private Long tableId;
    private UUID merchantId;
    private Long branchId;
    private String tableNumber;
    private int version;
    private LocalDateTime provisionedAt;
    private String traceId;
    private String spanId;
}
