package com.qrserve.merchant.repository;

import com.qrserve.merchant.entity.MerchantSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantSettingsRepository extends JpaRepository<MerchantSettingsEntity, Long> {

    Optional<MerchantSettingsEntity> findByMerchantIdAndBranchIdIsNull(UUID merchantId);

    Optional<MerchantSettingsEntity> findByMerchantIdAndBranchId(UUID merchantId, Long branchId);
}
