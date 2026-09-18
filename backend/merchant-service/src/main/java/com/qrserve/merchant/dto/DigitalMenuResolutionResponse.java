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

    /**
     * Brand presentation, all nullable and all merchant-level.
     *
     * <p>This is a PUBLIC, unauthenticated response, so only fields a guest is
     * meant to see belong here - name, slug and artwork. Nothing about
     * ownership, contact details or settings.
     *
     * <p>The customer menu treats every one of these as optional: a null cover
     * falls back to the branch's first dish photo, and a null logo or tagline
     * simply is not rendered. That matters because none of them is populated
     * today.
     */
    private String logoUrl;
    private String coverImageUrl;
    private String tagline;
}
