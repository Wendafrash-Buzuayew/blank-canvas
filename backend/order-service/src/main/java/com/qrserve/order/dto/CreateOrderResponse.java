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
}
