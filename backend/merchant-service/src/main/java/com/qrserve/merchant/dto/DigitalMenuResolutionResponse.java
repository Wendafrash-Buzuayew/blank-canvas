package com.qrserve.merchant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DigitalMenuResolutionResponse {
    private UUID merchantId;
    private String merchantSlug;
    private Long branchId;
    private String branchSlug;
    private String branchName;
}
