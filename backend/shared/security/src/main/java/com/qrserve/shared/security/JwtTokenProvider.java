package com.qrserve.shared.security;

import io.jsonwebtoken.Claims;
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

    private final SecretKey key;
    private final long jwtExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret:dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC10b2tlbi1zaWduaW5nLWl0LXNob3VsZC1iZS1sb25nLWVub3VnaA==}") String secret,
            @Value("${jwt.access-expiration-ms:3600000}") long jwtExpirationMs,
            @Value("${jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
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
                .subject(userPrincipal.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .signWith(key)
                .compact();
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

    public UserRole getRoleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        String roleStr = claims.get("role", String.class);
        return roleStr != null ? UserRole.valueOf(roleStr) : UserRole.CUSTOMER;
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        String userIdStr = claims.get("userId", String.class);
        return userIdStr != null ? UUID.fromString(userIdStr) : null;
    }
}