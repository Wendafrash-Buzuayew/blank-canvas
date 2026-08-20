package com.qrserve.merchant.dto;

import com.qrserve.shared.common.FulfilmentType;
import com.qrserve.shared.common.SettlementMode;

import java.util.Map;
import java.util.Set;

/**
 * Settings as every caller sees them: already merged, already defaulted, no nulls.
 *
 * <p>Callers must never have to ask "and what if this merchant has no row" — that
 * question is answered once, in {@code MerchantSettingsResolver}.
 */
public record ResolvedMerchantSettings(
        Set<FulfilmentType> fulfilmentEnabled,
        Map<FulfilmentType, SettlementMode> settlementMode) {

    public boolean isEnabled(FulfilmentType type) {
        return fulfilmentEnabled.contains(type);
    }

    /** Never null: an unconfigured type resolves to its platform default. */
    public SettlementMode mode(FulfilmentType type) {
        return settlementMode.get(type);
    }
}
