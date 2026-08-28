package com.qrserve.auth.superapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The subset of merchant-service's BranchEntity JSON this service actually
 * reads back. See MerchantProvisionResponse for why this isn't the real type.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BranchProvisionResponse(Long id, String slug) {
}
