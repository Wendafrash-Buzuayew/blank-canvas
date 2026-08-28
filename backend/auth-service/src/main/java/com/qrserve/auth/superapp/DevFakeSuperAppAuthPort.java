package com.qrserve.auth.superapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrserve.shared.exceptions.UnauthorizedException;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the real Super App token exchange, which has no published
 * contract yet. Treats the raw token as a JSON object carrying the claim
 * fields directly - swap this class for a real adapter once the Safaricom
 * contract exists; nothing that depends on {@link SuperAppAuthPort} changes.
 */
@Component
public class DevFakeSuperAppAuthPort implements SuperAppAuthPort {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SuperAppMerchantClaim exchangeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Super App token is required");
        }
        SuperAppMerchantClaim claim;
        try {
            claim = objectMapper.readValue(rawToken, SuperAppMerchantClaim.class);
        } catch (Exception e) {
            throw new UnauthorizedException("Super App token could not be parsed");
        }
        if (claim.merchantExternalRef() == null || claim.merchantExternalRef().isBlank()) {
            throw new UnauthorizedException("Super App token is missing the merchant reference");
        }
        return claim;
    }
}
