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

    /**
     * Compact canonical constructor that defensively copies both components into
     * immutable collections. This prevents a single caller from corrupting the shared
     * defaults or any instance by mutating the returned sets and maps. Additionally,
     * {@code Map.copyOf} rejects null values, enforcing the "{@code mode()} never
     * returns null" guarantee at construction time rather than surfacing as a null
     * later while minting a payment QR code.
     */
    public ResolvedMerchantSettings(
            Set<FulfilmentType> fulfilmentEnabled,
            Map<FulfilmentType, SettlementMode> settlementMode) {
        this.fulfilmentEnabled = Set.copyOf(fulfilmentEnabled);
        this.settlementMode = Map.copyOf(settlementMode);
    }

    public boolean isEnabled(FulfilmentType type) {
        return fulfilmentEnabled.contains(type);
    }

    /** Never null: an unconfigured type resolves to its platform default. */
    public SettlementMode mode(FulfilmentType type) {
        return settlementMode.get(type);
    }
}
