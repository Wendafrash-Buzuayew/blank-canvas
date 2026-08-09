package com.qrserve.merchant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTableResponse {
    private Long id;
    private String tableNumber;
    private Integer capacity;
    private String qrUrl;
    private String qrToken;
}
