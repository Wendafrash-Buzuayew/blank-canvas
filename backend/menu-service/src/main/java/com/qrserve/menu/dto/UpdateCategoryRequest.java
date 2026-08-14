package com.qrserve.menu.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateCategoryRequest {
    @NotBlank
    private String name;

    private Integer displayOrder;
}