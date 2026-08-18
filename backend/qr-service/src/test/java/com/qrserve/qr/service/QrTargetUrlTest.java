package com.qrserve.qr.service;

import com.qrserve.shared.common.PublicMenuUrl;
import com.qrserve.shared.common.QrSignatureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * qr-service and merchant-service must emit byte-identical URLs for the same
 * table. They previously carried two copies of the format, each commented as
 * consistent with the other, and both wrong in the same way.
 */
class QrTargetUrlTest {

    private static final UUID MERCHANT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final PublicMenuUrl urls = new PublicMenuUrl("qrserve.safaricom.et", "https");
    private final QrSignatureService signatures = new QrSignatureService("master-secret-value", "");

    @Test
    @DisplayName("builds the same signed URL merchant-service would build")
    void matchesMerchantServiceFormat() {
        String url = QrGeneratorService.targetUrlFor(
                new QrGeneratorService.TableInfo(42L, 7L, MERCHANT_ID, "12"),
                new QrGeneratorService.MerchantInfo(MERCHANT_ID, "sunrise"),
                new QrGeneratorService.BranchInfo(7L, "main"),
                urls, signatures);

        String expected = urls.menuUrl("sunrise", "main", "12",
                signatures.generateSignature(MERCHANT_ID, 7L, 42L));
        assertEquals(expected, url);
    }

    @Test
    @DisplayName("the URL is signed and validates for this table")
    void urlIsSigned() {
        String url = QrGeneratorService.targetUrlFor(
                new QrGeneratorService.TableInfo(42L, 7L, MERCHANT_ID, "12"),
                new QrGeneratorService.MerchantInfo(MERCHANT_ID, "sunrise"),
                new QrGeneratorService.BranchInfo(7L, "main"),
                urls, signatures);

        String signature = url.substring(url.indexOf("?signature=") + "?signature=".length());
        assertTrue(signatures.validateSignature(signature, MERCHANT_ID, 7L, 42L));
    }

    @Test
    @DisplayName("no hardcoded qrserve.com survives - the host comes from configuration")
    void hostComesFromConfiguration() {
        String url = QrGeneratorService.targetUrlFor(
                new QrGeneratorService.TableInfo(1L, 1L, MERCHANT_ID, "1"),
                new QrGeneratorService.MerchantInfo(MERCHANT_ID, "tenant"),
                new QrGeneratorService.BranchInfo(1L, "main"),
                new PublicMenuUrl("example.test", "http"), signatures);

        assertTrue(url.startsWith("http://tenant.example.test/menu/main/1"), url);
    }

    @Test
    @DisplayName("the branch SLUG is used, not the branch id")
    void usesBranchSlug() {
        String url = QrGeneratorService.targetUrlFor(
                new QrGeneratorService.TableInfo(42L, 7L, MERCHANT_ID, "12"),
                new QrGeneratorService.MerchantInfo(MERCHANT_ID, "sunrise"),
                new QrGeneratorService.BranchInfo(7L, "terrace"),
                urls, signatures);

        // The old code had the branch id to hand and not the slug, which is very
        // likely how the wrong format came to be written in the first place.
        assertTrue(url.contains("/menu/terrace/12"), url);
        assertTrue(!url.contains("/menu/7/"), "the branch id must not appear in the path");
    }
}
