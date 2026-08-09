package com.qrserve.merchant.repository;

import com.qrserve.merchant.entity.TableAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TableAssignmentRepository extends JpaRepository<TableAssignmentEntity, Long> {
    List<TableAssignmentEntity> findByMerchantId(UUID merchantId);
    List<TableAssignmentEntity> findByBranchId(Long branchId);
    List<TableAssignmentEntity> findByTableId(Long tableId);
    List<TableAssignmentEntity> findByWaiterId(Long waiterId);
    Optional<TableAssignmentEntity> findByTableIdAndStatus(Long tableId, String status);
    List<TableAssignmentEntity> findByWaiterIdAndStatus(Long waiterId, String status);
}