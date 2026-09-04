package com.qrserve.merchant.repository;

import com.qrserve.merchant.entity.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {
    List<ReviewEntity> findByBranchIdOrderByCreatedAtDesc(Long branchId);
    List<ReviewEntity> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
