package com.qrserve.shared.common;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrSignatureServiceDigitalMenuTest {

    private final QrSignatureService service = new QrSignatureService("test-master-secret", "");
    private static final UUID MERCHANT = UUID.randomUUID();

    @Test
    void roundTripsWithoutATable() {
        String signature = service.generateSignature(MERCHANT, 5L);
        assertTrue(service.validateSignature(signature, MERCHANT, 5L));
    }

    @Test
    void aTableScopedSignatureDoesNotValidateTheBranchOnlyScope() {
        // Signing different fewer fields must not be confusable with the
        // existing 3-arg scheme, even for the same merchant/branch.
        String tableScoped = service.generateSignature(MERCHANT, 5L, 42L);
        assertFalse(service.validateSignature(tableScoped, MERCHANT, 5L));
    }
}
