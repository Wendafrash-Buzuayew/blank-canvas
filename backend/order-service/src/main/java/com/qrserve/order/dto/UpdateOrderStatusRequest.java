package com.qrserve.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateOrderStatusRequest {
    @NotBlank
    private String status; // PENDING, ACCEPTED, PREPARING, READY, DELIVERED, PAID, CANCELLED
}
