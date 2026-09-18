package com.qrserve.auth.superapp;

import com.qrserve.shared.exceptions.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The real Super App handshake only ever carries a merchant short code and
 * an MSISDN - this fake treats the raw token as a JSON object carrying those
 * two claim fields directly, so local dev and this test can exercise the
 * whole exchange path without a real container. See
 * docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §5.
 */
class DevFakeSuperAppAuthPortTest {

    private final DevFakeSuperAppAuthPort port = new DevFakeSuperAppAuthPort(true);

    @Test
    @DisplayName("a well-formed token JSON becomes a claim with both fields")
    void parsesAWellFormedToken() {
        String token = "{\"merchantShortCode\":\"174379\",\"msisdn\":\"+254700000000\"}";

        SuperAppMerchantClaim claim = port.exchangeToken(token);

        assertEquals("174379", claim.merchantShortCode());
        assertEquals("+254700000000", claim.msisdn());
    }

    @Test
    @DisplayName("garbage input is rejected, not silently defaulted")
    void rejectsGarbageInput() {
        assertThrows(UnauthorizedException.class, () -> port.exchangeToken("not json at all"));
    }

    @Test
    @DisplayName("a token missing the merchant short code is rejected")
    void rejectsMissingShortCode() {
        String token = "{\"msisdn\":\"+254700000000\"}";
        assertThrows(UnauthorizedException.class, () -> port.exchangeToken(token));
    }

    @Test
    @DisplayName("a token missing the MSISDN is rejected")
    void rejectsMissingMsisdn() {
        String token = "{\"merchantShortCode\":\"174379\"}";
        assertThrows(UnauthorizedException.class, () -> port.exchangeToken(token));
    }

    @Test
    @DisplayName("a null or blank token is rejected")
    void rejectsBlankToken() {
        assertThrows(UnauthorizedException.class, () -> port.exchangeToken(null));
        assertThrows(UnauthorizedException.class, () -> port.exchangeToken("  "));
    }

    @Test
    @DisplayName("the dev fake refuses to run at all when disabled, even for a well-formed token")
    void refusesWhenDisabled() {
        DevFakeSuperAppAuthPort disabledPort = new DevFakeSuperAppAuthPort(false);
        String token = "{\"merchantShortCode\":\"174379\",\"msisdn\":\"+254700000000\"}";
        assertThrows(UnauthorizedException.class, () -> disabledPort.exchangeToken(token));
    }
}
