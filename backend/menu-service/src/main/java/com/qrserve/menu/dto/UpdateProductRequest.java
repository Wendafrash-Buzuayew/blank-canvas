package com.qrserve.menu.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateProductRequest {
    private String name;

    private String description;

    private BigDecimal price;

    private String image;

    private Boolean available;

    private Integer preparationTime;
}