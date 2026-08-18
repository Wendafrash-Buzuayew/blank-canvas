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
 * HMAC-SHA256 signature over a QR code's {@code {merchantId, branchId, tableId}}
 * triple, proving a scan came from a code this platform issued.
 *
 * <h2>Per-tenant derived key</h2>
 * The signing key is not the master secret. It is
 * {@code HMAC-SHA256(masterSecret, merchantId)}, derived on demand — nothing
 * extra is stored. In a shared multi-tenant deployment a single global key means
 * one leaked or brute-forced value lets an attacker mint valid codes for every
 * restaurant on the platform. Derivation confines the damage to one tenant.
 *
 * <h2>Rotation overlap</h2>
 * Signing always uses the current secret; validation accepts the current secret
 * <em>or</em> an optional {@code qr.signature-secret-previous}. QR codes are
 * printed onto physical table stands, so rotating the secret must not mean
 * reprinting every stand in every restaurant. Rotation becomes: set previous to
 * the old value, current to the new, reprint at leisure, then drop previous.
 *
 * <h2>Signed over ids, not slugs</h2>
 * Ids are permanently stable. Slugs are stable only because renames are currently
 * rejected; signing them would couple a future rename feature to a physical
 * reprint. Choosing ids costs nothing now and removes that coupling later.
 */
@Component
@Slf4j
public class QrSignatureService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;
    /** Empty when no rotation is in progress. Verify-only — never used to sign. */
    private final String previousSecret;

    public QrSignatureService(
            @Value("${qr.signature-secret}") String secret,
            @Value("${qr.signature-secret-previous:}") String previousSecret) {
        // Fail fast: no default secret. Deployment must set QR_SIGNATURE_SECRET;
        // otherwise Spring throws at startup instead of silently signing with a
        // known value.
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("qr.signature-secret must be configured via QR_SIGNATURE_SECRET");
        }
        this.secret = secret;
        this.previousSecret = previousSecret == null ? "" : previousSecret.trim();
        if (!this.previousSecret.isBlank()) {
            log.info("QR signature rotation overlap is active: codes signed with the previous secret "
                    + "will still validate. Drop qr.signature-secret-previous once reprinting is complete.");
        }
    }

    /** Signs with the current secret only. */
    public String generateSignature(UUID merchantId, Long branchId, Long tableId) {
        return sign(secret, merchantId, branchId, tableId);
    }

    /**
     * Validates against the current secret, then the previous one if a rotation is
     * in progress. Both candidates are compared in constant time.
     */
    public boolean validateSignature(String signature, UUID merchantId, Long branchId, Long tableId) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        boolean valid = constantTimeEquals(signature, sign(secret, merchantId, branchId, tableId));
        if (!valid && !previousSecret.isBlank()) {
            valid = constantTimeEquals(signature, sign(previousSecret, merchantId, branchId, tableId));
        }
        return valid;
    }

    private String sign(String masterSecret, UUID merchantId, Long branchId, Long tableId) {
        if (merchantId == null) {
            // Otherwise the canonical payload would contain the literal "null",
            // making every null-merchant signature interchangeable.
            throw new IllegalArgumentException("merchantId is required to sign a QR payload");
        }
        String payload = merchantId + ":" + branchId + ":" + tableId;
        return mac(deriveTenantKey(masterSecret, merchantId), payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * {@code HMAC-SHA256(masterSecret, merchantId)}. The raw MAC bytes become the
     * signing key for this tenant.
     */
    private byte[] deriveTenantKey(String masterSecret, UUID merchantId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(masterSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(merchantId.toString().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("QR tenant key derivation failed", e);
            throw new IllegalStateException("QR tenant key derivation failed", e);
        }
    }

    private String mac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(message));
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
