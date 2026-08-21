package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.ResolvedMerchantSettings;
import com.qrserve.shared.common.FulfilmentType;
import com.qrserve.shared.common.SettlementMode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Merges the merchant-wide row with an optional branch row and fills the gaps.
 *
 * <p>Static and pure so the precedence rules can be asserted without a database. The
 * merge is field by field, not row-replacing: a branch that only narrows its
 * fulfilment list must not reset the merchant's settlement modes and start taking
 * money at a different point in the meal.
 */
public final class MerchantSettingsResolver {

    /**
     * Dine-in on a tab is the product's original behaviour, so it is what a merchant
     * who never opens the settings screen keeps. Takeout and delivery default to
     * PREPAID because with no table there is no signed-QR presence proof, and
     * prepayment is the control that keeps unpaid orders out of the kitchen.
     */
    public static final ResolvedMerchantSettings DEFAULTS = new ResolvedMerchantSettings(
            EnumSet.of(FulfilmentType.DINE_IN),
            defaultModes());

    private MerchantSettingsResolver() {
    }

    /** A stored row. Either field may be null or empty, meaning "not configured here". */
    public record SettingsRow(
            Set<FulfilmentType> fulfilmentEnabled,
            Map<FulfilmentType, SettlementMode> settlementMode) {
    }

    public static ResolvedMerchantSettings resolve(SettingsRow merchantRow, SettingsRow branchRow) {
        Set<FulfilmentType> enabled = firstNonEmpty(
                branchRow == null ? null : branchRow.fulfilmentEnabled(),
                merchantRow == null ? null : merchantRow.fulfilmentEnabled(),
                DEFAULTS.fulfilmentEnabled());

        Map<FulfilmentType, SettlementMode> modes = defaultModes();
        putAll(modes, merchantRow == null ? null : merchantRow.settlementMode());
        putAll(modes, branchRow == null ? null : branchRow.settlementMode());

        return new ResolvedMerchantSettings(EnumSet.copyOf(enabled), modes);
    }

    private static Map<FulfilmentType, SettlementMode> defaultModes() {
        Map<FulfilmentType, SettlementMode> modes = new EnumMap<>(FulfilmentType.class);
        modes.put(FulfilmentType.DINE_IN, SettlementMode.TAB);
        modes.put(FulfilmentType.TAKEOUT, SettlementMode.PREPAID);
        modes.put(FulfilmentType.DELIVERY, SettlementMode.PREPAID);
        return modes;
    }

    private static void putAll(
            Map<FulfilmentType, SettlementMode> target,
            Map<FulfilmentType, SettlementMode> source) {
        if (source != null) {
            target.putAll(source);
        }
    }

    @SafeVarargs
    private static Set<FulfilmentType> firstNonEmpty(Set<FulfilmentType>... candidates) {
        for (Set<FulfilmentType> candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("DEFAULTS must never be empty");
    }
}
