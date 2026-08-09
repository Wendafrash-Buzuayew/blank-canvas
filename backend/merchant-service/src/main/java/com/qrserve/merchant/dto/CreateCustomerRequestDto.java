package com.qrserve.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCustomerRequestDto {
    @NotNull
    private UUID merchantId;

    @NotNull
    private Long branchId;

    @NotNull
    private Long tableId;

    @NotBlank
    private String requestType; // CALL_WAITER, WATER_REQUEST, REQUEST_BILL, ASSISTANCE

    private String note;
}