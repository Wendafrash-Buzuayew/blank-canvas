package com.qrserve.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenOrderResponse {
    private UUID id;
    private String orderNumber;
    private UUID merchantId;
    private Long branchId;
    private Long tableId;
    private String tableNumber;
    private String customerName;
    private String status;
    private BigDecimal totalAmount;
    private String note;
    private LocalDateTime createdAt;
    private List<ItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDto {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
        private String notes;
    }
}
