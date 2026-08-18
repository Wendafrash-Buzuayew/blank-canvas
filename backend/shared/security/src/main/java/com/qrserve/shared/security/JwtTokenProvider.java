package com.qrserve.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    /** Distinguishes token purposes. A token is only valid for its own purpose. */
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "ACCESS";
    public static final String TYPE_REFRESH = "REFRESH";
    /** Anonymous, single-order token used only to subscribe to /topic/orders/{id}. */
    public static final String TYPE_ORDER_STREAM = "ORDER_STREAM";
    public static final String CLAIM_ORDER_ID = "orderId";

    /**
     * Order-stream tokens outlive a meal but not a day. Long enough for a guest to
     * watch an order through to PAID, short enough that a leaked QR-derived token
     * is not a durable credential.
     */
    private static final long ORDER_STREAM_EXPIRATION_MS = 4 * 60 * 60 * 1000L;

    private final SecretKey key;
    private final long jwtExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-ms:3600000}") long jwtExpirationMs,
            @Value("${jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        // Fail fast: no default secret. Deployment must set JWT_SECRET; otherwise
        // Spring throws at startup instead of silently signing with a known value.
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret must be configured via JWT_SECRET");
        }
        // Decode Base64 secret or use raw bytes if not valid Base64
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        // Ensure key is at least 256 bits (32 bytes) for HS256
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.jwtExpirationMs = jwtExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(UserPrincipal userPrincipal) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userPrincipal.getUserId() != null ? userPrincipal.getUserId().toString() : null);
        claims.put("merchantId", userPrincipal.getMerchantId() != null ? userPrincipal.getMerchantId().toString() : null);
        claims.put("role", userPrincipal.getRole().name());
        claims.put(CLAIM_TYPE, TYPE_ACCESS);

        return Jwts.builder()
                .claims(claims)
                .subject(userPrincipal.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(UserPrincipal userPrincipal) {
        return Jwts.builder()
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .subject(userPrincipal.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * Mints an anonymous token scoped to exactly one order.
     *
     * <p>A guest who scans a QR code has no account, so they cannot complete the
     * authenticated WebSocket handshake — which meant {@code /topic/orders/{id}}
     * was published to by notification-service but reachable by nobody. This token
     * carries no role and no merchant: its only capability is subscribing to the
     * one order named in it, enforced by StompAuthInterceptor.
     */
    public String generateOrderStreamToken(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is required for an order-stream token");
        }
        return Jwts.builder()
                .claim(CLAIM_TYPE, TYPE_ORDER_STREAM)
                .claim(CLAIM_ORDER_ID, orderId.toString())
                .subject("order:" + orderId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ORDER_STREAM_EXPIRATION_MS))
                .signWith(key)
                .compact();
    }

    /** Token purpose, or null if the token predates the type claim or is unparseable. */
    public String getTokenType(String token) {
        try {
            return getClaimsFromToken(token).get(CLAIM_TYPE, String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(getTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(getTokenType(token));
    }

    public boolean isOrderStreamToken(String token) {
        return TYPE_ORDER_STREAM.equals(getTokenType(token));
    }

    /** The order this token is scoped to, or null if it is not an order-stream token. */
    public UUID getOrderIdFromToken(String token) {
        try {
            String raw = getClaimsFromToken(token).get(CLAIM_ORDER_ID, String.class);
            return raw != null ? UUID.fromString(raw) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Configured access-token lifetime in seconds, for the login/refresh response. */
    public long getAccessExpirationSeconds() {
        return jwtExpirationMs / 1000L;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    public UUID getMerchantIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        String merchantIdStr = claims.get("merchantId", String.class);
        return merchantIdStr != null ? UUID.fromString(merchantIdStr) : null;
    }

    /**
     * Reads the role claim, rejecting tokens that lack one.
     *
     * <p>Previously a missing role silently became {@code CUSTOMER}, which masked
     * token-issuance bugs. Only call this for access tokens — order-stream tokens
     * carry no role by design.
     *
     * @throws JwtException if the role claim is absent or unrecognised
     */
    public UserRole getRoleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        String roleStr = claims.get("role", String.class);
        if (roleStr == null || roleStr.isBlank()) {
            throw new JwtException("Token is missing the required 'role' claim");
        }
        try {
            return UserRole.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new JwtException("Token carries an unrecognised role: " + roleStr);
        }
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        String userIdStr = claims.get("userId", String.class);
        return userIdStr != null ? UUID.fromString(userIdStr) : null;
    }
}