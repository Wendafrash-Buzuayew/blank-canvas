package com.qrserve.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateMerchantRequest {
    @NotBlank
    private String name;

    /**
     * The tenant's permanent public hostname label, e.g. "sunrise" for
     * sunrise.qrserve.safaricom.et.
     *
     * <p>Required, and not derived from the name: a display name may be in any
     * script, while this must be a valid DNS label. A UI may suggest a value when
     * the name is already Latin, but the API demands it explicitly.
     */
    @NotBlank
    private String slug;

    @NotBlank
    private String phone;

    @NotBlank
    private String city;

    @NotBlank
    private String address;

    @NotBlank
    private String category;
}
