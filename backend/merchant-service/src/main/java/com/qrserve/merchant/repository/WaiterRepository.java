package com.qrserve.merchant.repository;

import com.qrserve.merchant.entity.WaiterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WaiterRepository extends JpaRepository<WaiterEntity, Long> {
    List<WaiterEntity> findByMerchantId(UUID merchantId);
    List<WaiterEntity> findByBranchId(Long branchId);
    Optional<WaiterEntity> findByUserId(UUID userId);
    List<WaiterEntity> findByMerchantIdAndStatus(UUID merchantId, String status);
    List<WaiterEntity> findByMerchantIdAndBranchId(
            UUID merchantId,
            Long branchId
    );
}