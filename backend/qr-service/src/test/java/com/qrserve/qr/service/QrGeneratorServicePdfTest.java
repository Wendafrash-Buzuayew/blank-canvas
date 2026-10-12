package com.qrserve.qr.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** No PDF library is available, so the structure a reader relies on is checked directly. */
class QrGeneratorServicePdfTest {

    private static final String PAYLOAD = "https://menu.safaricom.et/m/sunrise-coffee/main?signature=abc";

    private static String latin1(byte[] b) {
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("xref offsets and startxref point at the real objects")
    void xrefIsCorrect() {
        byte[] pdf = QrGeneratorService.pdfFromImage(QrGeneratorService.renderImage(PAYLOAD, 300));
        String s = latin1(pdf);

        assertTrue(s.startsWith("%PDF-1.4\n"));
        assertTrue(s.endsWith("%%EOF\n"));

        Matcher startxref = Pattern.compile("startxref\n(\\d+)\n%%EOF\n$").matcher(s);
        assertTrue(startxref.find());
        int xref = Integer.parseInt(startxref.group(1));
        assertTrue(s.startsWith("xref\n0 6\n", xref));

        Matcher entries = Pattern.compile("(\\d{10}) 00000 n \n").matcher(s.substring(xref));
        int obj = 1;
        while (entries.find()) {
            int offset = Integer.parseInt(entries.group(1));
            assertTrue(s.startsWith(obj + " 0 obj\n", offset), "object " + obj + " at offset " + offset);
            obj++;
        }
        assertEquals(6, obj);
    }

    @Test
    @DisplayName("the image stream is Flate grayscale of the declared length and still decodes to the payload")
    void imageStreamDecodes() throws Exception {
        byte[] pdf = QrGeneratorService.pdfFromImage(QrGeneratorService.renderImage(PAYLOAD, 300));
        String s = latin1(pdf);

        Matcher dict = Pattern.compile("/ColorSpace /DeviceGray /BitsPerComponent 8 /Filter /FlateDecode /Length (\\d+) >>\nstream\n")
                .matcher(s);
        assertTrue(dict.find());
        int length = Integer.parseInt(dict.group(1));
        int start = dict.end();
        assertTrue(s.startsWith("\nendstream", start + length), "/Length matches the stream");

        byte[] gray = new InflaterInputStream(new ByteArrayInputStream(pdf, start, length)).readAllBytes();
        assertEquals(300 * 300, gray.length);

        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_BYTE_GRAY);
        image.getRaster().setDataElements(0, 0, 300, 300, gray);
        String decoded = new MultiFormatReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();
        assertEquals(PAYLOAD, decoded);
    }
}
