package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.ResolvedMerchantSettings;
import com.qrserve.merchant.entity.MerchantSettingsEntity;
import com.qrserve.merchant.repository.MerchantSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantSettingsService {

    private final MerchantSettingsRepository repository;

    /** @param branchId null to resolve the merchant-wide settings only */
    @Transactional(readOnly = true)
    public ResolvedMerchantSettings resolve(UUID merchantId, Long branchId) {
        MerchantSettingsResolver.SettingsRow merchantRow =
                repository.findByMerchantIdAndBranchIdIsNull(merchantId).map(this::toRow).orElse(null);

        MerchantSettingsResolver.SettingsRow branchRow = branchId == null
                ? null
                : repository.findByMerchantIdAndBranchId(merchantId, branchId).map(this::toRow).orElse(null);

        return MerchantSettingsResolver.resolve(merchantRow, branchRow);
    }

    /** The merchant's own account, or null when unconfigured. Never defaulted. */
    @Transactional(readOnly = true)
    public String destinationRef(UUID merchantId, Long branchId) {
        return (branchId == null
                ? Optional.<MerchantSettingsEntity>empty()
                : repository.findByMerchantIdAndBranchId(merchantId, branchId))
                .or(() -> repository.findByMerchantIdAndBranchIdIsNull(merchantId))
                .map(MerchantSettingsEntity::getDestinationRef)
                .orElse(null);
    }

    private MerchantSettingsResolver.SettingsRow toRow(MerchantSettingsEntity entity) {
        return new MerchantSettingsResolver.SettingsRow(
                Optional.ofNullable(entity.getFulfilmentEnabled()).orElse(java.util.Set.of()),
                Optional.ofNullable(entity.getSettlementMode()).orElse(java.util.Map.of()));
    }
}
