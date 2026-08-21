package com.qrserve.shared.common.emvco;

/**
 * The merchant-side inputs to a payload.
 *
 * @param guid            scheme identifier inside the merchant-account template
 * @param destinationRef  the account money lands in — the merchant's own, never ours
 * @param mcc             merchant category code, tag 52
 * @param currencyNumeric ISO 4217 numeric, tag 53 ("230" for ETB)
 * @param countryCode     ISO 3166-1 alpha-2, tag 58
 * @param name            tag 59
 * @param city            tag 60
 */
public record EmvcoMerchant(
        String guid,
        String destinationRef,
        String mcc,
        String currencyNumeric,
        String countryCode,
        String name,
        String city) {
}
