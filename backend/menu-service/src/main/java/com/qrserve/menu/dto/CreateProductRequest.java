package com.qrserve.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
