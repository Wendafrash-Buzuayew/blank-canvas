package com.qrserve.backoffice.repository;

import com.qrserve.backoffice.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findAllByOrderByCreatedAtDesc();
    List<AuditLogEntity> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
    List<AuditLogEntity> findByActionOrderByCreatedAtDesc(String action);
}
