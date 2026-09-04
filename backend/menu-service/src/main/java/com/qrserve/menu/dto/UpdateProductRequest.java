package com.qrserve.menu.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UpdateProductRequest {
    private String name;

    private String description;

    private BigDecimal price;

    private BigDecimal discountPrice;

    private LocalDateTime discountStartAt;

    private LocalDateTime discountEndAt;

    /** True clears any existing discount — distinguishes "not provided" (null) from "clear it" (true). */
    private Boolean clearDiscount;

    private String image;

    private Boolean available;

    private Integer preparationTime;
}