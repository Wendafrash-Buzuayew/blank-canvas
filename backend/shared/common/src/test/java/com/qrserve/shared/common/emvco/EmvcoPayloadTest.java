package com.qrserve.shared.common.emvco;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden vectors for the one EMVCo builder.
 *
 * <p>A payload with a wrong CRC is rejected silently by every bank app, which is
 * unfixable once a sticker is laminated and on a table. These vectors are the only
 * thing standing between that and a print run.
 */
class EmvcoPayloadTest {

    private static final EmvcoMerchant SUNRISE = new EmvcoMerchant(
            "ET.QRSERVE", "1234567890", "5812", "230", "ET", "SUNRISE", "ADDIS ABABA");

    @Test
    @DisplayName("static payload matches the golden vector, CRC included")
    void staticGoldenVector() {
        assertEquals(
                "00020101021126280010ET.QRSERVE011012345678905204581253032305802ET"
                        + "5907SUNRISE6011ADDIS ABABA62160303BR10705T42-163049A4D",
                EmvcoPayload.staticPayload(SUNRISE, "T42-1", "BR1"));
    }

    @Test
    @DisplayName("dynamic payload matches the golden vector, CRC included")
    void dynamicGoldenVector() {
        assertEquals(
                "00020101021226280010ET.QRSERVE011012345678905204581253032305406420.00"
                        + "5802ET5907SUNRISE6011ADDIS ABABA62350104PB-70303BR10507PR-90010705T42-1"
                        + "630442B8",
                EmvcoPayload.dynamicPayload(
                        SUNRISE, "T42-1", "BR1", new BigDecimal("420.00"), "PR-9001", "PB-7"));
    }

    @Test
    @DisplayName("static carries no amount and dynamic does — tag 01 says which")
    void pointOfInitiationDistinguishesThem() {
        Map<String, String> stat = Emvco.parseTags(EmvcoPayload.staticPayload(SUNRISE, "T42-1", "BR1"));
        assertEquals("11", stat.get("01"));
        assertFalse(stat.containsKey("54"), "a static payload must not fix an amount");

        Map<String, String> dyn = Emvco.parseTags(EmvcoPayload.dynamicPayload(
                SUNRISE, "T42-1", "BR1", new BigDecimal("420.00"), "PR-9001", "PB-7"));
        assertEquals("12", dyn.get("01"));
        assertEquals("420.00", dyn.get("54"));
    }

    @Test
    @DisplayName("a minted payload validates its own CRC")
    void mintedPayloadIsSelfConsistent() {
        assertTrue(EmvcoPayload.crcValid(EmvcoPayload.staticPayload(SUNRISE, "T42-1", "BR1")));
        assertTrue(EmvcoPayload.crcValid(EmvcoPayload.dynamicPayload(
                SUNRISE, "T42-1", "BR1", new BigDecimal("99.50"), "PR-1", "PB-1")));
    }

    @Test
    @DisplayName("a corrupted CRC is rejected")
    void corruptedCrcRejected() {
        String good = EmvcoPayload.staticPayload(SUNRISE, "T42-1", "BR1");
        assertFalse(EmvcoPayload.crcValid(good.substring(0, good.length() - 4) + "0000"));
    }

    @Test
    @DisplayName("amounts keep two decimals, because a bank app renders exactly what it reads")
    void amountScaleIsExplicit() {
        Map<String, String> tags = Emvco.parseTags(EmvcoPayload.dynamicPayload(
                SUNRISE, "T42-1", "BR1", new BigDecimal("420"), "PR-1", "PB-1"));
        assertEquals("420.00", tags.get("54"));
    }

    @Test
    @DisplayName("TLV length is the character count, zero-padded to two digits")
    void tlvEncoding() {
        assertEquals("0002ET", Emvco.tlv("00", "ET"));
        assertEquals("5907SUNRISE", Emvco.tlv("59", "SUNRISE"));
    }

    @Test
    @DisplayName("parseTags rejects incomplete trailing tag header rather than truncating silently")
    void incompleteTagHeaderRejected() {
        String staticPayload = EmvcoPayload.staticPayload(SUNRISE, "T42-1", "BR1");
        String withIncompleteTag = staticPayload + "62";
        assertThrows(IllegalArgumentException.class, () -> Emvco.parseTags(withIncompleteTag),
                "parseTags must throw on incomplete tag header, not return partial map");
    }

    @Test
    @DisplayName("rounding extra precision rejects at mint time, not silently altering a payment that would mismatch")
    void extraPrecisionRejected() {
        assertThrows(ArithmeticException.class, () ->
                EmvcoPayload.dynamicPayload(SUNRISE, "T42-1", "BR1", new BigDecimal("420.375"), "PR-1", "PB-1"),
                "must reject amounts with more than 2 decimals to prevent silent mismatches at settlement");
    }
}
