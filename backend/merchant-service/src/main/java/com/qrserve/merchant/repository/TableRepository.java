package com.qrserve.merchant.repository;

import com.qrserve.merchant.entity.TableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TableRepository extends JpaRepository<TableEntity, Long> {
    List<TableEntity> findByBranchId(Long branchId);
    List<TableEntity> findByMerchantId(UUID merchantId);
    Optional<TableEntity> findByQrToken(String qrToken);
    Optional<TableEntity> findByBranchIdAndTableNumber(Long branchId, String tableNumber);
}
