package com.qrserve.backoffice.repository;

import com.qrserve.backoffice.entity.AuditLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<AuditLogEntity> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);
    List<AuditLogEntity> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
    List<AuditLogEntity> findByMerchantIdAndActionOrderByCreatedAtDesc(UUID merchantId, String action, Pageable pageable);
}