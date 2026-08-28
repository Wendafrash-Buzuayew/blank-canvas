package com.qrserve.auth.superapp;

import com.qrserve.shared.exceptions.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * No real Super App token contract exists yet - this fake treats the raw
 * token as a JSON object carrying the claim fields directly, so local dev
 * and this test can exercise the whole exchange path without a real
 * container. See docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §5.
 */
class DevFakeSuperAppAuthPortTest {

    private final DevFakeSuperAppAuthPort port = new DevFakeSuperAppAuthPort(true);

    @Test
    @DisplayName("a well-formed token JSON becomes a claim with every field")
    void parsesAWellFormedToken() {
        String token = "{"
                + "\"merchantExternalRef\":\"MPESA-BIZ-001\","
                + "\"businessName\":\"Sunrise Cafe\","
                + "\"phone\":\"+254700000000\","
                + "\"city\":\"Nairobi\","
                + "\"address\":\"123 Moi Ave\","
                + "\"category\":\"Restaurant\""
                + "}";

        SuperAppMerchantClaim claim = port.exchangeToken(token);

        assertEquals("MPESA-BIZ-001", claim.merchantExternalRef());
        assertEquals("Sunrise Cafe", claim.businessName());
        assertEquals("+254700000000", claim.phone());
        assertEquals("Nairobi", claim.city());
        assertEquals("123 Moi Ave", claim.address());
        assertEquals("Restaurant", claim.category());
    }

    @Test
    @DisplayName("garbage input is rejected, not silently defaulted")
    void rejectsGarbageInput() {
        assertThrows(UnauthorizedException.class, () -> port.exchangeToken("not json at all"));
    }

    @Test
    @DisplayName("a token missing the merchant reference is rejected")
    void rejectsMissingMerchantRef() {
        String token = "{\"businessName\":\"Sunrise Cafe\",\"phone\":\"+254700000000\","
                + "\"city\":\"Nairobi\",\"address\":\"123 Moi Ave\",\"category\":\"Restaurant\"}";
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
        String token = "{"
                + "\"merchantExternalRef\":\"MPESA-BIZ-001\","
                + "\"businessName\":\"Sunrise Cafe\","
                + "\"phone\":\"+254700000000\","
                + "\"city\":\"Nairobi\","
                + "\"address\":\"123 Moi Ave\","
                + "\"category\":\"Restaurant\""
                + "}";
        assertThrows(UnauthorizedException.class, () -> disabledPort.exchangeToken(token));
    }

    @Test
    @DisplayName("a token missing any required field other than merchantExternalRef is rejected")
    void rejectsMissingOtherFields() {
        String missingCity = "{\"merchantExternalRef\":\"MPESA-BIZ-001\",\"businessName\":\"Sunrise Cafe\","
                + "\"phone\":\"+254700000000\",\"address\":\"123 Moi Ave\",\"category\":\"Restaurant\"}";
        assertThrows(UnauthorizedException.class, () -> port.exchangeToken(missingCity));
    }
}
