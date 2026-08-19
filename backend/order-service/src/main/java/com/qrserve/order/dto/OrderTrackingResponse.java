package com.qrserve.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What a seated guest may read back about their own order.
 *
 * <p>Deliberately narrower than {@code OrderEntity}: no merchant id, no branch or
 * table id, no payment fields. Everything here was already handed to the guest in
 * {@link CreateOrderResponse} or pushed to them on
 * {@code /topic/orders/{id}}, so this endpoint exposes nothing new — it only
 * makes the current value readable, which a WebSocket push cannot do after the
 * fact.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTrackingResponse {
    private UUID id;
    private String orderNumber;
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
