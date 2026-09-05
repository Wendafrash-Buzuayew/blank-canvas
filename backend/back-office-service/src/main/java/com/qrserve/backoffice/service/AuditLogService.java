package com.qrserve.backoffice.service;

import com.qrserve.backoffice.dto.CreateAuditLogRequest;
import com.qrserve.backoffice.entity.AuditLogEntity;
import com.qrserve.backoffice.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public AuditLogEntity record(CreateAuditLogRequest request) {
        AuditLogEntity entity = AuditLogEntity.builder()
                .actorUserId(request.getActorUserId())
                .actorRole(request.getActorRole())
                .action(request.getAction())
                .entityType(request.getEntityType())
                .entityId(request.getEntityId())
                .merchantId(request.getMerchantId())
                .details(request.getDetails())
                .build();
        return auditLogRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntity> getLogs(UUID merchantId, String action) {
        if (merchantId != null) {
            return auditLogRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
        }
        if (action != null && !action.isBlank()) {
            return auditLogRepository.findByActionOrderByCreatedAtDesc(action);
        }
        return auditLogRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public long count() {
        return auditLogRepository.count();
    }
}
