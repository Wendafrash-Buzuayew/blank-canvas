package com.qrserve.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {
    @NotNull
    private Long tableId;

    /**
     * The QR signature from the scanned table code. Proves the guest is at this
     * table: without it, anyone could post orders against any tableId.
     */
    @NotBlank
    private String signature;

    private String customerName;

    private String note;

    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {
        @NotNull
        private Long productId;

        @NotNull
        @Min(1)
        @Max(99)
        private Integer quantity;

        private String notes;
    }
}
