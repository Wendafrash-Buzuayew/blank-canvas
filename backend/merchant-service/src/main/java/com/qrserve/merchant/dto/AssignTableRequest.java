package com.qrserve.merchant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AssignTableRequest {
    @NotNull
    private UUID merchantId;

    @NotNull
    private Long branchId;

    @NotNull
    private Long tableId;

    @NotNull
    private Long waiterId;

    private String shift;
}