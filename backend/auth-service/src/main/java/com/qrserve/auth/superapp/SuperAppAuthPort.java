package com.qrserve.auth.superapp;

/**
 * Turns a raw Super App token into the merchant claim it carries. One
 * interface, adapters behind it - see
 * docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §5.
 * Today's only implementation is {@link DevFakeSuperAppAuthPort}; a real
 * Safaricom adapter drops in later as a second implementation of this same
 * interface.
 */
public interface SuperAppAuthPort {

    /**
     * @throws com.qrserve.shared.exceptions.UnauthorizedException if the token
     *         cannot be parsed or is missing required fields
     */
    SuperAppMerchantClaim exchangeToken(String rawToken);
}
