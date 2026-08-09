package com.qrserve.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateMerchantRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String phone;

    @NotBlank
    private String city;

    @NotBlank
    private String address;

    @NotBlank
    private String category;
}
