package com.qrserve.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unlike {@link CreateBranchRequest}, carries no merchantId (immutable, set
 * once at creation) and no slug (permanent — see BranchService.updateBranch
 * for why, same reasoning as MerchantService.updateMerchant).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBranchRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String phone;

    private String address;
}
