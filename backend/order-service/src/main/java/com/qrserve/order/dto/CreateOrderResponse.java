package com.qrserve.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {
    private UUID id;
    private String orderNumber;
    private String status;
    private Integer estimatedTime; // minutes
    private BigDecimal totalAmount;
    /**
     * Short-lived, anonymous token whose only capability is subscribing to
     * {@code /topic/orders/{id}} for this order. A guest who scanned a QR code has
     * no account, so without this they cannot receive live status updates at all.
     * Not an API credential — see JwtTokenProvider.TYPE_ORDER_STREAM.
     */
    private String streamToken;
}
