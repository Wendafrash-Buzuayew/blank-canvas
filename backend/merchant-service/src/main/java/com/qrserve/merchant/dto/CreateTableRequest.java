package com.qrserve.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTableRequest {
    @NotNull
    private Long branchId;

    @NotBlank
    private String tableNumber;

    @NotNull
    private Integer capacity;
}
