package com.qrserve.merchant.repository;

import com.qrserve.merchant.entity.MerchantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends JpaRepository<MerchantEntity, UUID> {
    Optional<MerchantEntity> findBySlug(String slug);
}
