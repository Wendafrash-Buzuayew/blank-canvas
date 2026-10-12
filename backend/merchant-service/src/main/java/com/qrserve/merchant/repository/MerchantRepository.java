package com.qrserve.merchant.repository;

import com.qrserve.merchant.entity.MerchantEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends JpaRepository<MerchantEntity, UUID> {
    Optional<MerchantEntity> findBySlug(String slug);

    /**
     * Row-locks the merchant for the rest of the transaction. Serialises branch
     * creation per merchant so two concurrent creates cannot both pass the
     * free-tier cap or both become the auto-primary first branch.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MerchantEntity m where m.id = :id")
    Optional<MerchantEntity> findByIdForUpdate(@Param("id") UUID id);
}
