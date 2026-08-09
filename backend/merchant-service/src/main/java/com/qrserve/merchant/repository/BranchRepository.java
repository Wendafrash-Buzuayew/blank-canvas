package com.qrserve.merchant.repository;

import com.qrserve.merchant.entity.BranchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchRepository extends JpaRepository<BranchEntity, Long> {
    List<BranchEntity> findByMerchantId(UUID merchantId);
    Optional<BranchEntity> findByMerchantIdAndSlug(UUID merchantId, String slug);
}
