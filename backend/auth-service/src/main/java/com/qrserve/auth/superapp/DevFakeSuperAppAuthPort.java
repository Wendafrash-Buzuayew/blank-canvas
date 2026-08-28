package com.qrserve.auth.superapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrserve.shared.exceptions.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the real Super App token exchange, which has no published
 * contract yet. Treats the raw token as a JSON object carrying the claim
 * fields directly - swap this class for a real adapter once the Safaricom
 * contract exists; nothing that depends on {@link SuperAppAuthPort} changes.
 *
 * <p>Fails closed by default (`superapp.auth.dev-fake-enabled`, defaulting to
 * false via SUPERAPP_DEV_FAKE_ENABLED). This fake accepts ANY token carrying a
 * non-blank merchantExternalRef as proof of identity - if it were reachable
 * unconditionally, anyone who knew or guessed an existing merchant's
 * merchantExternalRef could obtain a valid MERCHANT_OWNER session for that
 * merchant with no password, and any unrecognized ref would trigger full
 * unauthenticated provisioning across merchant-service. This must only be
 * enabled in environments where that is the intended behavior (local/dev).
 */
@Component
public class DevFakeSuperAppAuthPort implements SuperAppAuthPort {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean enabled;

    public DevFakeSuperAppAuthPort(
            @Value("${superapp.auth.dev-fake-enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public SuperAppMerchantClaim exchangeToken(String rawToken) {
        if (!enabled) {
            throw new UnauthorizedException(
                    "Super App dev-fake auth is disabled. Set SUPERAPP_DEV_FAKE_ENABLED=true "
                            + "to enable it for local/dev use only.");
        }
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Super App token is required");
        }
        SuperAppMerchantClaim claim;
        try {
            claim = objectMapper.readValue(rawToken, SuperAppMerchantClaim.class);
        } catch (Exception e) {
            throw new UnauthorizedException("Super App token could not be parsed");
        }
        requireField(claim.merchantExternalRef(), "merchantExternalRef");
        requireField(claim.businessName(), "businessName");
        requireField(claim.phone(), "phone");
        requireField(claim.city(), "city");
        requireField(claim.address(), "address");
        requireField(claim.category(), "category");
        return claim;
    }

    private void requireField(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new UnauthorizedException("Super App token is missing the required field: " + fieldName);
        }
    }
}
