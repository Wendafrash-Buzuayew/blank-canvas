package com.qrserve.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCategoryRequest {
    @NotNull
    private UUID merchantId;

    @NotBlank
    private String name;

    private Integer displayOrder;
}
