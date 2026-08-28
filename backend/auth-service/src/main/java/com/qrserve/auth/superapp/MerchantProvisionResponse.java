package com.qrserve.auth.superapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * The subset of merchant-service's MerchantEntity JSON this service actually
 * reads back. Deliberately not the real MerchantEntity class - auth-service
 * must not compile-depend on merchant-service's domain types.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MerchantProvisionResponse(UUID id, String slug) {
}
