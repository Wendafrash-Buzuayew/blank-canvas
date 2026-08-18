package com.qrserve.shared.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrSignatureServiceTest {

    private static final UUID MERCHANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MERCHANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final QrSignatureService service = new QrSignatureService("master-secret-value", "");

    @Test
    @DisplayName("a signature it generated validates")
    void roundTrips() {
        String sig = service.generateSignature(MERCHANT_A, 1L, 5L);
        assertTrue(service.validateSignature(sig, MERCHANT_A, 1L, 5L));
    }

    @Test
    @DisplayName("the same table triple under a different merchant produces a different signature")
    void keyIsDerivedPerTenant() {
        assertNotEquals(
                service.generateSignature(MERCHANT_A, 1L, 5L),
                service.generateSignature(MERCHANT_B, 1L, 5L));
    }

    @Test
    @DisplayName("merchant A's signature does not validate for merchant B")
    void signatureIsNotPortableAcrossTenants() {
        String sigA = service.generateSignature(MERCHANT_A, 1L, 5L);
        // The property that matters: compromising one tenant's derived key must not
        // yield a forgery for another tenant.
        assertFalse(service.validateSignature(sigA, MERCHANT_B, 1L, 5L));
    }

    @Test
    @DisplayName("changing the branch or table invalidates the signature")
    void signatureCoversAllThreeIds() {
        String sig = service.generateSignature(MERCHANT_A, 1L, 5L);
        assertFalse(service.validateSignature(sig, MERCHANT_A, 2L, 5L));
        assertFalse(service.validateSignature(sig, MERCHANT_A, 1L, 6L));
    }

    @Test
    @DisplayName("a null or blank signature is rejected, never treated as absent-and-fine")
    void rejectsMissingSignature() {
        assertFalse(service.validateSignature(null, MERCHANT_A, 1L, 5L));
        assertFalse(service.validateSignature("", MERCHANT_A, 1L, 5L));
        assertFalse(service.validateSignature("   ", MERCHANT_A, 1L, 5L));
    }

    @Test
    @DisplayName("a signature from a different master secret is rejected")
    void rejectsForeignSecret() {
        String sig = new QrSignatureService("some-other-master", "")
                .generateSignature(MERCHANT_A, 1L, 5L);
        assertFalse(service.validateSignature(sig, MERCHANT_A, 1L, 5L));
    }

    @Test
    @DisplayName("during rotation, a code signed with the previous secret still validates")
    void rotationOverlapAcceptsPreviousSecret() {
        String printed = new QrSignatureService("old-master", "")
                .generateSignature(MERCHANT_A, 1L, 5L);

        QrSignatureService rotating = new QrSignatureService("new-master", "old-master");
        // A printed table stand keeps working across a secret rotation. Without
        // this, rotating the secret means reprinting every stand in every
        // restaurant on the platform.
        assertTrue(rotating.validateSignature(printed, MERCHANT_A, 1L, 5L));
    }

    @Test
    @DisplayName("rotation only ever signs with the current secret")
    void rotationSignsWithCurrentOnly() {
        String fresh = new QrSignatureService("new-master", "old-master")
                .generateSignature(MERCHANT_A, 1L, 5L);

        assertTrue(new QrSignatureService("new-master", "")
                .validateSignature(fresh, MERCHANT_A, 1L, 5L));
        assertFalse(new QrSignatureService("old-master", "")
                        .validateSignature(fresh, MERCHANT_A, 1L, 5L),
                "a newly issued code must not be valid under the retired secret");
    }

    @Test
    @DisplayName("once the previous secret is dropped, old codes stop validating")
    void droppingPreviousEndsTheOverlap() {
        String printed = new QrSignatureService("old-master", "")
                .generateSignature(MERCHANT_A, 1L, 5L);
        assertFalse(new QrSignatureService("new-master", "")
                .validateSignature(printed, MERCHANT_A, 1L, 5L));
    }

    @Test
    @DisplayName("a blank master secret fails fast")
    void requiresSecret() {
        assertThrows(IllegalStateException.class, () -> new QrSignatureService("  ", ""));
        assertThrows(IllegalStateException.class, () -> new QrSignatureService(null, ""));
    }

    @Test
    @DisplayName("a null merchant id is rejected rather than signing the literal string null")
    void rejectsNullMerchant() {
        assertThrows(IllegalArgumentException.class, () -> service.generateSignature(null, 1L, 5L));
    }
}
