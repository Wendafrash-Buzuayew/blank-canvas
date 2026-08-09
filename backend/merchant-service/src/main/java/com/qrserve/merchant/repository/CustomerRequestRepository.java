package com.qrserve.merchant.repository;

import com.qrserve.merchant.entity.CustomerRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerRequestRepository extends JpaRepository<CustomerRequestEntity, Long> {
    List<CustomerRequestEntity> findByMerchantId(UUID merchantId);
    List<CustomerRequestEntity> findByBranchId(Long branchId);
    List<CustomerRequestEntity> findByTableId(Long tableId);
    List<CustomerRequestEntity> findByMerchantIdAndStatus(UUID merchantId, String status);
    List<CustomerRequestEntity> findByBranchIdAndStatus(Long branchId, String status);
}