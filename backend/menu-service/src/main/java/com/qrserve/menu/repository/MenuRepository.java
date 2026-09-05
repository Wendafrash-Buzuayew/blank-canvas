package com.qrserve.menu.repository;

import com.qrserve.menu.entity.MenuEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuRepository extends JpaRepository<MenuEntity, UUID> {
    Optional<MenuEntity> findByBranchId(Long branchId);

    /** Used by MenuTemplateService.delete to block removing a template still assigned to a branch. */
    long countByTemplateStyle(String templateStyle);
}
