package com.qrserve.qr.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

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

    @Test
    @DisplayName("renderPng uses error correction H, restored from the M this replaced")
    void rendersWithHighErrorCorrection() throws Exception {
        // This is the print path for a code laminated onto a physical table: it has to
        // survive scuffing and being photographed at an angle. The EC level a decoder
        // reports back is read from the QR's own format bits, so this proves what was
        // actually encoded rather than just what was requested.
        byte[] png = QrGeneratorService.renderPng(VALID_STATIC, 300);
        com.google.zxing.Result result = decode(png);
        assertEquals(VALID_STATIC, result.getText());
        assertEquals("H", String.valueOf(result.getResultMetadata().get(ResultMetadataType.ERROR_CORRECTION_LEVEL)));
    }

    @Test
    @DisplayName("renderPng honors the UTF-8 charset hint for non-ASCII content")
    void rendersUtf8ContentCorrectly() throws Exception {
        // Without the charset hint, ZXing's writer defaults to ISO-8859-1, which
        // cannot represent Ge'ez script at all. A merchant display name or a slug
        // outside ASCII must still round-trip exactly, or the printed sticker encodes
        // garbage no wallet can read.
        String content = "https://sunrise.qrserve.safaricom.et/menu/main/15?note=ሰሉ፡";
        byte[] png = QrGeneratorService.renderPng(content, 300);
        com.google.zxing.Result result = decode(png);
        assertEquals(content, result.getText());
    }

    private static com.google.zxing.Result decode(byte[] png) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        return new MultiFormatReader().decode(bitmap);
    }
}
