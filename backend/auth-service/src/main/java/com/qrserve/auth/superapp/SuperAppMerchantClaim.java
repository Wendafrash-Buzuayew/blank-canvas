package com.qrserve.auth.superapp;

/**
 * Whatever the M-PESA Super App tells us about the merchant entering the
 * Mini App. Prefill data only - it is never trusted for authorization beyond
 * "this token exchange succeeded," matching the MerchantContextClaim pattern
 * in docs/superpowers/specs/2026-08-20-superapp-miniapp-payments-design.md §7.
 */
public record SuperAppMerchantClaim(
        String merchantExternalRef,
        String businessName,
        String phone,
        String city,
        String address,
        String category) {
}
