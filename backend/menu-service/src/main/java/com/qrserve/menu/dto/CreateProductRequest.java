package com.qrserve.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProductRequest {
    @NotNull
    private Long categoryId;

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private BigDecimal price;

    private String image;

    private Integer preparationTime;
}
