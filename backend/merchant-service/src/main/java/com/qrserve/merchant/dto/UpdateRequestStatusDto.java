package com.qrserve.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateRequestStatusDto {
    @NotBlank
    private String status; // PENDING, ACKNOWLEDGED, COMPLETED, CANCELLED
}