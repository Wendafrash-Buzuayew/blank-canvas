package com.qrserve.shared.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * The answer to "which tenant is {@code sunrise.qrserve.safaricom.et}?".
 *
 * <p>Deliberately minimal. This endpoint is public and heavily cached, and it
 * discloses only what the hostname already discloses: that this tenant exists,
 * its id and its display name. Nothing about plans, staff, branches or revenue
 * belongs here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResolutionResponse {
    private UUID merchantId;
    private String slug;
    private String name;
}
