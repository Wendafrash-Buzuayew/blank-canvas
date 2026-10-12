package com.qrserve.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCustomerRequestDto {
    /** Ignored: the tenant is derived from the signed table. Kept for older clients. */
    private UUID merchantId;

    /** Ignored: the branch is derived from the signed table. Kept for older clients. */
    private Long branchId;

    @NotNull
    private Long tableId;

    @NotBlank
    private String requestType; // CALL_WAITER, WATER_REQUEST, REQUEST_BILL, ASSISTANCE

    private String note;
}