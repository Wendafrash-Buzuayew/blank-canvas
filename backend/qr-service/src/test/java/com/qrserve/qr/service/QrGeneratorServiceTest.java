package com.qrserve.qr.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * qr-service renders; it no longer decides what the code says. Recomputing the target
 * here is how the two services drifted the first time.
 */
class QrGeneratorServiceTest {

    private static final String VALID_STATIC =
            "00020101021126280010ET.QRSERVE011012345678905204581253032305802ET"
                    + "5907SUNRISE6011ADDIS ABABA62160303BR10705T42-163049A4D";

    /** What a MENU_URL-profile row stores: a signed link, never an EMVCo TLV string. */
    private static final String VALID_MENU_URL =
            "https://sunrise.qrserve.safaricom.et/menu/main/12?signature=abc123";

    @Test
    @DisplayName("renders the stored EMVCo payload verbatim")
    void rendersStoredPayload() {
        assertEquals(VALID_STATIC, QrGeneratorService.payloadToRender(VALID_STATIC, "EMVCO"));
    }

    @Test
    @DisplayName("renders the stored menu URL verbatim for an unconfigured merchant")
    void rendersStoredMenuUrl() {
        // A MENU_URL row's payloadCrc is null: a sliced substring of a URL would be
        // meaningless data pretending to be a checksum, so this profile must skip the
        // EMVCo CRC check entirely rather than being rejected by it.
        assertEquals(VALID_MENU_URL, QrGeneratorService.payloadToRender(VALID_MENU_URL, "MENU_URL"));
    }

    @Test
    @DisplayName("refuses to render an EMVCo payload whose CRC does not match")
    void refusesInvalidCrc() {
        // Rendering it would produce a sticker that every bank app rejects with no
        // explanation, and by then it is laminated to a table.
        String corrupted = VALID_STATIC.substring(0, VALID_STATIC.length() - 4) + "0000";
        assertThrows(IllegalStateException.class, () -> QrGeneratorService.payloadToRender(corrupted, "EMVCO"));
    }

    @Test
    @DisplayName("refuses to invent a payload when none is stored, for either profile")
    void refusesMissingPayload() {
        assertThrows(IllegalStateException.class, () -> QrGeneratorService.payloadToRender(null, "EMVCO"));
        assertThrows(IllegalStateException.class, () -> QrGeneratorService.payloadToRender(" ", "EMVCO"));
        assertThrows(IllegalStateException.class, () -> QrGeneratorService.payloadToRender(null, "MENU_URL"));
        assertThrows(IllegalStateException.class, () -> QrGeneratorService.payloadToRender(" ", "MENU_URL"));
    }

    @Test
    @DisplayName("a rendered PNG is a real PNG")
    void rendersPng() {
        byte[] png = QrGeneratorService.renderPng(VALID_STATIC, 300);
        assertTrue(png.length > 100);
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);
    }

    @Test
    @DisplayName("a rendered PNG of a menu URL is also a real PNG")
    void rendersPngOfMenuUrl() {
        byte[] png = QrGeneratorService.renderPng(VALID_MENU_URL, 300);
        assertTrue(png.length > 100);
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);
    }
}
