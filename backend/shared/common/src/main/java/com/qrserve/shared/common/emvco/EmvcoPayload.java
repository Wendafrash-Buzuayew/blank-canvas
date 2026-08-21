package com.qrserve.shared.common.emvco;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single EMVCo payload builder for the platform.
 *
 * <p>One builder, one place, because the alternative has already happened here: the
 * public menu URL was constructed independently in two services, each carrying a
 * comment claiming it matched the other, and they drifted. A drifted <em>payment</em>
 * payload is a QR that every bank app rejects without saying why.
 *
 * <p>Tag assignment follows section 5.2 of the payments design.
 */
public final class EmvcoPayload {

    private static final String FORMAT_INDICATOR = "01";
    private static final String STATIC_INITIATION = "11";
    private static final String DYNAMIC_INITIATION = "12";

    /** Spec cap for tag 59 (merchant name). */
    private static final int MAX_MERCHANT_NAME = 25;

    /** Spec cap for tag 60 (merchant city). */
    private static final int MAX_MERCHANT_CITY = 15;

    /** Spec cap for tag 62-03 (store label). */
    private static final int MAX_STORE_LABEL = 25;

    private EmvcoPayload() {
    }

    /** Amount-less, printable, stable across reprints of the same version. */
    public static String staticPayload(EmvcoMerchant merchant, String terminalLabel, String storeLabel) {
        validate(merchant, storeLabel);
        String additional = Emvco.tlv("03", storeLabel) + Emvco.tlv("07", terminalLabel);
        return withCrc(header(merchant, STATIC_INITIATION) + tail(merchant) + Emvco.tlv("62", additional));
    }

    /** Amount-bearing, single-use, and the only payload carrying a payment reference. */
    public static String dynamicPayload(
            EmvcoMerchant merchant,
            String terminalLabel,
            String storeLabel,
            BigDecimal amount,
            String paymentRef,
            String billNumber) {

        validate(merchant, storeLabel);

        String additional = Emvco.tlv("01", billNumber)
                + Emvco.tlv("03", storeLabel)
                + Emvco.tlv("05", paymentRef)
                + Emvco.tlv("07", terminalLabel);

        return withCrc(header(merchant, DYNAMIC_INITIATION)
                + Emvco.tlv("54", amountOf(amount))
                + tail(merchant)
                + Emvco.tlv("62", additional));
    }

    /**
     * Rejects, before anything is minted, the two ways a payload can be built that
     * every bank app then silently refuses: a value outside the spec's per-tag
     * length cap, and a value outside US-ASCII.
     *
     * <p>{@link Emvco#tlv} counts a value's length in UTF-16 chars and
     * {@link Emvco#crc16} encodes it as US-ASCII (substituting {@code '?'} for
     * anything outside that range), while the image renderer encodes UTF-8. For a
     * merchant name containing, say, Amharic script, those three disagree about
     * both the byte length and the bytes themselves: the TLV length header would
     * undercount the UTF-8 byte length, and the CRC would be computed over
     * corrupted ASCII-substituted bytes rather than what actually gets encoded.
     * {@link #crcValid} cannot catch this after the fact — it repeats the exact
     * same substitution, so a wrong CRC still matches. The only place to catch it
     * is here, before a payload is minted and printed.
     */
    private static void validate(EmvcoMerchant merchant, String storeLabel) {
        requireAsciiWithinCap("59", merchant.name(), MAX_MERCHANT_NAME);
        requireAsciiWithinCap("60", merchant.city(), MAX_MERCHANT_CITY);
        requireAsciiWithinCap("62-03", storeLabel, MAX_STORE_LABEL);
    }

    private static void requireAsciiWithinCap(String tag, String value, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException("EMVCo tag " + tag + " must not be null");
        }
        if (!isUsAscii(value)) {
            throw new IllegalArgumentException(
                    "EMVCo tag " + tag + " must be US-ASCII, was: " + value);
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "EMVCo tag " + tag + " exceeds " + maxLength + " characters: " + value);
        }
    }

    private static boolean isUsAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    /** True when the payload's trailing four characters match its own contents. */
    public static boolean crcValid(String payload) {
        if (payload == null || payload.length() < 8) {
            return false;
        }
        int crcStart = payload.length() - 4;
        String body = payload.substring(0, crcStart);
        return body.endsWith(Emvco.CRC_TAG + "04")
                && Emvco.crc16(body).equalsIgnoreCase(payload.substring(crcStart));
    }

    private static String header(EmvcoMerchant m, String initiationMethod) {
        String mai = Emvco.tlv("00", m.guid()) + Emvco.tlv("01", m.destinationRef());
        return Emvco.tlv("00", FORMAT_INDICATOR)
                + Emvco.tlv("01", initiationMethod)
                + Emvco.tlv("26", mai)
                + Emvco.tlv("52", m.mcc())
                + Emvco.tlv("53", m.currencyNumeric());
    }

    private static String tail(EmvcoMerchant m) {
        return Emvco.tlv("58", m.countryCode())
                + Emvco.tlv("59", m.name())
                + Emvco.tlv("60", m.city());
    }

    /**
     * Amount must already be scaled to exactly two decimals by the caller. Rejects
     * extra precision with ArithmeticException rather than rounding, because the
     * payment matcher compares amounts exactly: rounding a payload down at mint time
     * would mint a good-faith bill that comes back as AMOUNT_MISMATCH and sits waiting
     * for staff. A loud failure before anything is printed is better than a payment
     * that differs from the bill.
     */
    private static String amountOf(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String withCrc(String body) {
        String withCrcTag = body + Emvco.CRC_TAG + "04";
        return withCrcTag + Emvco.crc16(withCrcTag);
    }
}
