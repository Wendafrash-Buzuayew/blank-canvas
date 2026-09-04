package com.qrserve.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBranchRequest {
    @NotNull
    private UUID merchantId;

    @NotBlank
    private String name;

    @NotBlank
    private String phone;

    @NotBlank
    private String slug;

    private String address;
}
