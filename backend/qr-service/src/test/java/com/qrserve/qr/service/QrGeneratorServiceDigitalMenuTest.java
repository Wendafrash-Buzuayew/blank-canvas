package com.qrserve.qr.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Only the static, HTTP-free decision is unit-tested here — the same reason
 * targetUrlFor and renderPng are static and package-private: this must not
 * diverge between calls without standing up HTTP.
 */
class QrGeneratorServiceDigitalMenuTest {

    @Test
    void rendersAPngForADigitalMenuUrl() {
        byte[] png = QrGeneratorService.renderPng("https://menu.safaricom.et/m/sunrise-coffee/main", 300);

        // PNG magic bytes: 89 50 4E 47
        assertTrue(png.length > 8);
        assertTrue((png[0] & 0xFF) == 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G');
    }
}
