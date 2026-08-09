package com.qrserve.menu.repository;

import com.qrserve.menu.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findByCategoryId(Long categoryId);
    List<ProductEntity> findByMerchantId(UUID merchantId);
    List<ProductEntity> findByMerchantIdAndAvailableTrue(UUID merchantId);
}
