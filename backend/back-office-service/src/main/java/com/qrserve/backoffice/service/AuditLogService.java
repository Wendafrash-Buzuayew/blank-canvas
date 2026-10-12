package com.qrserve.backoffice.service;

import com.qrserve.backoffice.dto.CreateAuditLogRequest;
import com.qrserve.backoffice.entity.AuditLogEntity;
import com.qrserve.backoffice.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    static final int MAX_PAGE_SIZE = 500;

    /** Newest first; both filters apply together when both are given. Size is clamped to 1..500. */
    @Transactional(readOnly = true)
    public List<AuditLogEntity> getLogs(UUID merchantId, String action, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        boolean hasAction = action != null && !action.isBlank();
        if (merchantId != null && hasAction) {
            return auditLogRepository.findByMerchantIdAndActionOrderByCreatedAtDesc(merchantId, action, pageable);
        }
        if (merchantId != null) {
            return auditLogRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable);
        }
        if (hasAction) {
            return auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable);
        }
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public long count() {
        return auditLogRepository.count();
    }
}
