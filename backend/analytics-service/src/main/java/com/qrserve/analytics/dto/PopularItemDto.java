package com.qrserve.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopularItemDto {
    private Long productId;
    private String name;
    private String image;
    private long count;
    private BigDecimal revenue;
}
