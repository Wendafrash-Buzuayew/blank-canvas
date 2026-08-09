package com.qrserve.menu.repository;

import com.qrserve.menu.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
    List<CategoryEntity> findByMerchantIdOrderByDisplayOrderAsc(UUID merchantId);
}
