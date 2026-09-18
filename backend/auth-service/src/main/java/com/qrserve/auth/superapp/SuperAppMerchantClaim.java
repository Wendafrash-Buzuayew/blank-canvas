package com.qrserve.auth.superapp;

/**
 * Whatever the M-PESA Super App tells us about the merchant entering the
 * Mini App. Prefill data only - it is never trusted for authorization beyond
 * "this token exchange succeeded," matching the MerchantContextClaim pattern
 * in docs/superpowers/specs/2026-08-20-superapp-miniapp-payments-design.md §7.
 *
 * <p>This is the real Super App handshake, confirmed against the Safaricom
 * contract: the Super App only ever hands over the merchant's till/short code
 * and the MSISDN of the person opening the Mini App. It does NOT carry a
 * business name, phone, city, address or category - those are collected from
 * the merchant in-app, once, on first entry (see
 * {@code UserEntity#onboardingComplete} and {@code AuthController#completeOnboarding}).
 */
public record SuperAppMerchantClaim(
        String merchantShortCode,
        String msisdn) {
}
