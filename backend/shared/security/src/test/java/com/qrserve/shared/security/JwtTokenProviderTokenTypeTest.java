package com.qrserve.shared.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the token-purpose separation: access, refresh and anonymous
 * order-stream tokens are signed with the same key, so only the {@code type}
 * claim keeps them from being interchangeable.
 */
class JwtTokenProviderTokenTypeTest {

    /** Test-only secret. Never a real one — deployments must set JWT_SECRET. */
    private static final String SECRET =
            "dGVzdC1vbmx5LXNlY3JldC1mb3ItdW5pdC10ZXN0cy1sb25nLWVub3VnaC0xMjM0NQ==";

    private final JwtTokenProvider provider =
            new JwtTokenProvider(SECRET, 3_600_000L, 604_800_000L);

    private UserPrincipal staff() {
        return UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .email("waiter@example.com")
                .role(UserRole.WAITER)
                .build();
    }

    @Test
    @DisplayName("an access token is not accepted as a refresh token")
    void accessIsNotRefresh() {
        String access = provider.generateAccessToken(staff());
        assertTrue(provider.isAccessToken(access));
        assertFalse(provider.isRefreshToken(access));
        assertFalse(provider.isOrderStreamToken(access));
    }

    @Test
    @DisplayName("a refresh token is not accepted as an access token")
    void refreshIsNotAccess() {
        String refresh = provider.generateRefreshToken(staff());
        assertTrue(provider.isRefreshToken(refresh));
        assertFalse(provider.isAccessToken(refresh));
    }

    @Test
    @DisplayName("an order-stream token is scoped to one order and grants no API access")
    void orderStreamIsScopedAndNotAnAccessToken() {
        UUID orderId = UUID.randomUUID();
        String token = provider.generateOrderStreamToken(orderId);

        assertTrue(provider.validateToken(token), "should be a validly signed token");
        assertTrue(provider.isOrderStreamToken(token));
        assertFalse(provider.isAccessToken(token), "must not authenticate REST calls");
        assertFalse(provider.isRefreshToken(token));
        assertEquals(orderId, provider.getOrderIdFromToken(token));
    }

    @Test
    @DisplayName("an order-stream token carries no role, so reading one fails loudly")
    void orderStreamHasNoRole() {
        String token = provider.generateOrderStreamToken(UUID.randomUUID());
        assertThrows(JwtException.class, () -> provider.getRoleFromToken(token));
    }

    @Test
    @DisplayName("a missing role claim is rejected instead of defaulting to CUSTOMER")
    void missingRoleIsRejected() {
        // A refresh token has no role claim; it stands in for any roleless token.
        String roleless = provider.generateRefreshToken(staff());
        assertThrows(JwtException.class, () -> provider.getRoleFromToken(roleless));
    }

    @Test
    @DisplayName("an access token still exposes role and merchant")
    void accessTokenClaimsSurvive() {
        UserPrincipal p = staff();
        String access = provider.generateAccessToken(p);
        assertEquals(UserRole.WAITER, provider.getRoleFromToken(access));
        assertEquals(p.getMerchantId(), provider.getMerchantIdFromToken(access));
        assertEquals(p.getEmail(), provider.getUsernameFromToken(access));
    }

    @Test
    @DisplayName("expiresIn reflects configuration rather than a hardcoded hour")
    void expirySecondsComeFromConfig() {
        assertEquals(3_600L, provider.getAccessExpirationSeconds());
        assertEquals(120L, new JwtTokenProvider(SECRET, 120_000L, 1L).getAccessExpirationSeconds());
    }

    @Test
    @DisplayName("a blank secret fails fast instead of signing with a default")
    void blankSecretIsRejected() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("  ", 1L, 1L));
    }

    @Test
    @DisplayName("minting an order-stream token without an order is a programming error")
    void orderStreamRequiresOrderId() {
        assertThrows(IllegalArgumentException.class, () -> provider.generateOrderStreamToken(null));
    }

    @Test
    @DisplayName("garbage is not silently typed")
    void garbageHasNoType() {
        assertNull(provider.getTokenType("not-a-jwt"));
        assertFalse(provider.isAccessToken("not-a-jwt"));
        assertNotNull(provider.generateAccessToken(staff()));
    }
}
