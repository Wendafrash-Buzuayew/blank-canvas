package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.ResolvedMerchantSettings;
import com.qrserve.shared.common.FulfilmentType;
import com.qrserve.shared.common.SettlementMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every order and every payment reads these settings, and most merchants will never
 * open the settings screen. The defaults are therefore the real production behaviour.
 */
class MerchantSettingsResolverTest {

    private static Map<FulfilmentType, SettlementMode> modes(Object... pairs) {
        Map<FulfilmentType, SettlementMode> map = new EnumMap<>(FulfilmentType.class);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((FulfilmentType) pairs[i], (SettlementMode) pairs[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("no rows at all: dine-in only, on a tab")
    void defaultsWhenNothingConfigured() {
        ResolvedMerchantSettings resolved = MerchantSettingsResolver.resolve(null, null);

        assertTrue(resolved.isEnabled(FulfilmentType.DINE_IN));
        assertFalse(resolved.isEnabled(FulfilmentType.TAKEOUT), "takeout is opt-in");
        assertFalse(resolved.isEnabled(FulfilmentType.DELIVERY), "delivery is not buildable yet");
        assertEquals(SettlementMode.TAB, resolved.mode(FulfilmentType.DINE_IN));
    }

    @Test
    @DisplayName("takeout defaults to prepaid, because prepayment is what protects the kitchen")
    void takeoutDefaultsToPrepaid() {
        assertEquals(SettlementMode.PREPAID,
                MerchantSettingsResolver.resolve(null, null).mode(FulfilmentType.TAKEOUT));
        assertEquals(SettlementMode.PREPAID,
                MerchantSettingsResolver.resolve(null, null).mode(FulfilmentType.DELIVERY));
    }

    @Test
    @DisplayName("a merchant row applies to every branch")
    void merchantRowApplies() {
        MerchantSettingsResolver.SettingsRow merchant = new MerchantSettingsResolver.SettingsRow(
                EnumSet.of(FulfilmentType.DINE_IN, FulfilmentType.TAKEOUT),
                modes(FulfilmentType.DINE_IN, SettlementMode.PREPAID));

        ResolvedMerchantSettings resolved = MerchantSettingsResolver.resolve(merchant, null);

        assertTrue(resolved.isEnabled(FulfilmentType.TAKEOUT));
        assertEquals(SettlementMode.PREPAID, resolved.mode(FulfilmentType.DINE_IN));
    }

    @Test
    @DisplayName("a branch row wins over the merchant row, field by field")
    void branchOverridesMerchant() {
        MerchantSettingsResolver.SettingsRow merchant = new MerchantSettingsResolver.SettingsRow(
                EnumSet.of(FulfilmentType.DINE_IN, FulfilmentType.TAKEOUT),
                modes(FulfilmentType.DINE_IN, SettlementMode.TAB));
        MerchantSettingsResolver.SettingsRow branch = new MerchantSettingsResolver.SettingsRow(
                EnumSet.of(FulfilmentType.DINE_IN),
                modes(FulfilmentType.DINE_IN, SettlementMode.PREPAID));

        ResolvedMerchantSettings resolved = MerchantSettingsResolver.resolve(merchant, branch);

        assertFalse(resolved.isEnabled(FulfilmentType.TAKEOUT), "this branch does not do takeout");
        assertEquals(SettlementMode.PREPAID, resolved.mode(FulfilmentType.DINE_IN));
    }

    @Test
    @DisplayName("a branch row with an empty mode map still inherits the merchant's modes")
    void emptyBranchMapDoesNotErasePolicy() {
        // A branch that only narrows the fulfilment list must not silently reset every
        // settlement mode to the default and start taking money at a different time.
        MerchantSettingsResolver.SettingsRow merchant = new MerchantSettingsResolver.SettingsRow(
                EnumSet.of(FulfilmentType.DINE_IN, FulfilmentType.TAKEOUT),
                modes(FulfilmentType.DINE_IN, SettlementMode.PREPAID));
        MerchantSettingsResolver.SettingsRow branch = new MerchantSettingsResolver.SettingsRow(
                EnumSet.of(FulfilmentType.DINE_IN), Map.of());

        assertEquals(SettlementMode.PREPAID,
                MerchantSettingsResolver.resolve(merchant, branch).mode(FulfilmentType.DINE_IN));
    }

    @Test
    @DisplayName("an unconfigured fulfilment type falls back to its default mode, not to null")
    void unconfiguredTypeFallsBack() {
        MerchantSettingsResolver.SettingsRow merchant = new MerchantSettingsResolver.SettingsRow(
                EnumSet.of(FulfilmentType.DINE_IN, FulfilmentType.TAKEOUT),
                modes(FulfilmentType.DINE_IN, SettlementMode.TAB));

        assertEquals(SettlementMode.PREPAID,
                MerchantSettingsResolver.resolve(merchant, null).mode(FulfilmentType.TAKEOUT));
    }
}
