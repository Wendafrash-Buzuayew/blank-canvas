package com.qrserve.merchant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateWaiterRequest {
    @NotNull
    private UUID merchantId;

    @NotNull
    private Long branchId;

    @NotNull
    private UUID userId;

    private String status;

    private String shift;
}