package com.qrserve.order.repository;

import com.qrserve.order.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    List<OrderEntity> findByMerchantId(UUID merchantId);
    List<OrderEntity> findByBranchId(Long branchId);
    List<OrderEntity> findByMerchantIdAndStatus(UUID merchantId, String status);
    List<OrderEntity> findByBranchIdAndStatus(Long branchId, String status);
    List<OrderEntity> findByTableId(Long tableId);
}
