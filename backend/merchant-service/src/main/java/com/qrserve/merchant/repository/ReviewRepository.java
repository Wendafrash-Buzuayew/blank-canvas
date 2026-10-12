package com.qrserve.merchant.repository;

import com.qrserve.merchant.entity.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {
    List<ReviewEntity> findByBranchIdOrderByCreatedAtDesc(Long branchId);
    List<ReviewEntity> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    /** Aggregated in the database; the summary endpoint is public and polled. */
    @Query("select avg(r.rating) as average, count(r) as count from ReviewEntity r where r.branchId = :branchId")
    RatingSummary summarizeByBranch(@Param("branchId") Long branchId);

    interface RatingSummary {
        /** Null when the branch has no reviews. */
        Double getAverage();
        Long getCount();
    }
}
