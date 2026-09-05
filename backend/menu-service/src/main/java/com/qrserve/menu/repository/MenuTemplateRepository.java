package com.qrserve.menu.repository;

import com.qrserve.menu.entity.MenuTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuTemplateRepository extends JpaRepository<MenuTemplateEntity, String> {
}
