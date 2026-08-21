package com.qrserve.merchant.service;

import com.qrserve.merchant.entity.MerchantSettingsEntity;
import com.qrserve.merchant.repository.MerchantSettingsRepository;
import com.qrserve.shared.common.FulfilmentType;
import com.qrserve.shared.common.SettlementMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchantSettingsServiceTest {

    private static final UUID MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MerchantSettingsRepository repository;
    private MerchantSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(MerchantSettingsRepository.class);
        service = new MerchantSettingsService(repository);
    }

    @Test
    @DisplayName("a merchant with no rows gets the platform defaults, not an exception")
    void noRowsYieldsDefaults() {
        when(repository.findByMerchantIdAndBranchIdIsNull(MERCHANT)).thenReturn(Optional.empty());

        assertTrue(service.resolve(MERCHANT, null).isEnabled(FulfilmentType.DINE_IN));
        assertEquals(SettlementMode.TAB, service.resolve(MERCHANT, null).mode(FulfilmentType.DINE_IN));
    }

    @Test
    @DisplayName("a branch row narrows the merchant row")
    void branchNarrowsMerchant() {
        when(repository.findByMerchantIdAndBranchIdIsNull(MERCHANT)).thenReturn(Optional.of(
                MerchantSettingsEntity.builder()
                        .merchantId(MERCHANT)
                        .fulfilmentEnabled(EnumSet.of(FulfilmentType.DINE_IN, FulfilmentType.TAKEOUT))
                        .settlementMode(Map.of(FulfilmentType.DINE_IN, SettlementMode.TAB))
                        .build()));
        when(repository.findByMerchantIdAndBranchId(MERCHANT, 5L)).thenReturn(Optional.of(
                MerchantSettingsEntity.builder()
                        .merchantId(MERCHANT)
                        .branchId(5L)
                        .fulfilmentEnabled(EnumSet.of(FulfilmentType.DINE_IN))
                        .settlementMode(Map.of())
                        .build()));

        assertTrue(service.resolve(MERCHANT, 5L).isEnabled(FulfilmentType.DINE_IN));
        assertFalse(service.resolve(MERCHANT, 5L).isEnabled(FulfilmentType.TAKEOUT));
        assertEquals(SettlementMode.TAB, service.resolve(MERCHANT, 5L).mode(FulfilmentType.DINE_IN));
    }

    @Test
    @DisplayName("a null branch id reads only the merchant-wide row")
    void nullBranchReadsMerchantRowOnly() {
        when(repository.findByMerchantIdAndBranchIdIsNull(MERCHANT)).thenReturn(Optional.of(
                MerchantSettingsEntity.builder()
                        .merchantId(MERCHANT)
                        .fulfilmentEnabled(EnumSet.of(FulfilmentType.TAKEOUT))
                        .settlementMode(Map.of(FulfilmentType.TAKEOUT, SettlementMode.PREPAID))
                        .build()));

        assertTrue(service.resolve(MERCHANT, null).isEnabled(FulfilmentType.TAKEOUT));
    }

    @Test
    @DisplayName("destinationRef prefers the branch row when present")
    void destinationRefBranchRowWins() {
        when(repository.findByMerchantIdAndBranchId(MERCHANT, 5L)).thenReturn(Optional.of(
                MerchantSettingsEntity.builder()
                        .merchantId(MERCHANT)
                        .branchId(5L)
                        .destinationRef("branch-account")
                        .build()));
        when(repository.findByMerchantIdAndBranchIdIsNull(MERCHANT)).thenReturn(Optional.of(
                MerchantSettingsEntity.builder()
                        .merchantId(MERCHANT)
                        .destinationRef("merchant-account")
                        .build()));

        assertEquals("branch-account", service.destinationRef(MERCHANT, 5L));
    }

    @Test
    @DisplayName("destinationRef falls back to the merchant-wide row when no branch row exists")
    void destinationRefFallsBackToMerchantRow() {
        when(repository.findByMerchantIdAndBranchId(MERCHANT, 5L)).thenReturn(Optional.empty());
        when(repository.findByMerchantIdAndBranchIdIsNull(MERCHANT)).thenReturn(Optional.of(
                MerchantSettingsEntity.builder()
                        .merchantId(MERCHANT)
                        .destinationRef("merchant-account")
                        .build()));

        assertEquals("merchant-account", service.destinationRef(MERCHANT, 5L));
    }

    @Test
    @DisplayName("destinationRef is null, never defaulted, when neither row is configured")
    void destinationRefNullWhenUnconfigured() {
        when(repository.findByMerchantIdAndBranchId(MERCHANT, 5L)).thenReturn(Optional.empty());
        when(repository.findByMerchantIdAndBranchIdIsNull(MERCHANT)).thenReturn(Optional.empty());

        assertNull(service.destinationRef(MERCHANT, 5L));

        assertNull(service.destinationRef(MERCHANT, null));
    }
}
