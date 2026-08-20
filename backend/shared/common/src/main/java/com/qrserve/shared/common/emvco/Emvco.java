package com.qrserve.shared.common.emvco;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EMVCo merchant-presented-mode primitives: TLV encoding, the CRC every payload
 * ends with, and a parser used to verify what we just built.
 *
 * <p>Deliberately domain-free. The one place that knows which tag means what is
 * {@link EmvcoPayload}.
 */
public final class Emvco {

    /** Tag 63, always last, always four hex characters. */
    public static final String CRC_TAG = "63";

    private Emvco() {
    }

    /** Tag, then the value's character count as two digits, then the value. */
    public static String tlv(String tag, String value) {
        if (value == null) {
            throw new IllegalArgumentException("EMVCo value for tag " + tag + " must not be null");
        }
        if (value.length() > 99) {
            throw new IllegalArgumentException(
                    "EMVCo value for tag " + tag + " exceeds 99 characters: " + value.length());
        }
        return tag + String.format("%02d", value.length()) + value;
    }

    /**
     * CRC-16/CCITT-FALSE: polynomial 0x1021, initial value 0xFFFF, no input or
     * output reflection, no final XOR. Computed over every character up to and
     * including the "6304" that introduces the checksum itself.
     */
    public static String crc16(String data) {
        int crc = 0xFFFF;
        for (byte b : data.getBytes(java.nio.charset.StandardCharsets.US_ASCII)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }

    /** Top-level tags only; nested templates are returned as their raw inner string. */
    public static Map<String, String> parseTags(String payload) {
        Map<String, String> tags = new LinkedHashMap<>();
        int i = 0;
        while (i + 4 <= payload.length()) {
            String tag = payload.substring(i, i + 2);
            int length = Integer.parseInt(payload.substring(i + 2, i + 4));
            int from = i + 4;
            int to = from + length;
            if (to > payload.length()) {
                throw new IllegalArgumentException("Truncated EMVCo payload at tag " + tag);
            }
            tags.put(tag, payload.substring(from, to));
            i = to;
        }
        return tags;
    }
}
