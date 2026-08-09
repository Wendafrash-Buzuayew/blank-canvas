package com.qrserve.shared.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

/**
 * HMAC-SHA256 signature utility for QR code payload validation.
 * QR codes embed {@code {"merchantId": Long, "branchId": Long, "tableId": Long}}
 * plus a signature query parameter proving authenticity.
 */
@Component
@Slf4j
public class QrSignatureService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;

    public QrSignatureService(
            @Value("${qr.signature-secret:qrserve-tamper-proof-signature-secret-change-me}") String secret) {
        this.secret = secret;
    }

    public String generateSignature(UUID merchantId, Long branchId, Long tableId) {
        String payload = canonicalPayload(merchantId, branchId, tableId);
        return sign(payload);
    }

    public boolean validateSignature(String signature, UUID merchantId, Long branchId, Long tableId) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = generateSignature(merchantId, branchId, tableId);
        return constantTimeEquals(signature, expected);
    }

    private String canonicalPayload(UUID merchantId, Long branchId, Long tableId) {
        return merchantId + ":" + branchId + ":" + tableId;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("QR signature generation failed", e);
            throw new IllegalStateException("QR signature generation failed", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}