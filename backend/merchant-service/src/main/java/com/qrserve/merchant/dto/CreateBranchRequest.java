package com.qrserve.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateBranchRequest {
    @NotNull
    private UUID merchantId;

    @NotBlank
    private String name;

    @NotBlank
    private String phone;

    private String address;
}
