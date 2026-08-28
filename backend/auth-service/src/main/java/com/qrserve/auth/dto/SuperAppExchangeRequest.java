package com.qrserve.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SuperAppExchangeRequest {
    @NotBlank(message = "token is required")
    private String token;
}
