package com.qrserve.merchant.repository;

import com.qrserve.merchant.entity.BranchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchRepository extends JpaRepository<BranchEntity, Long> {
    List<BranchEntity> findByMerchantId(UUID merchantId);
    Optional<BranchEntity> findByMerchantIdAndSlug(UUID merchantId, String slug);

    /**
     * Clears the merchant's current primary flag in one statement that runs
     * BEFORE the new primary is written. Saving entities in a loop let Hibernate
     * order the flush so the new primary could hit the one-primary partial
     * unique index while the old one was still set.
     */
    @Modifying(flushAutomatically = true)
    @Query("update BranchEntity b set b.isPrimary = false "
            + "where b.merchantId = :merchantId and b.isPrimary = true and b.id <> :branchId")
    int clearPrimaryExcept(@Param("merchantId") UUID merchantId, @Param("branchId") Long branchId);
}
