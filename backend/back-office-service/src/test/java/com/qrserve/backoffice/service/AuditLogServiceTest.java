package com.qrserve.backoffice.service;

import com.qrserve.backoffice.dto.CreateAuditLogRequest;
import com.qrserve.backoffice.entity.AuditLogEntity;
import com.qrserve.backoffice.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditLogServiceTest {

    private AuditLogRepository auditLogRepository;
    private AuditLogService service;
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        service = new AuditLogService(auditLogRepository);
    }

    @Test
    void recordBuildsAnEntityFromEveryRequestFieldAndSavesIt() {
        UUID actor = UUID.randomUUID();
        CreateAuditLogRequest request = new CreateAuditLogRequest();
        request.setActorUserId(actor);
        request.setActorRole("SUPER_ADMIN");
        request.setAction("MERCHANT_CREATED");
        request.setEntityType("MERCHANT");
        request.setEntityId(MERCHANT.toString());
        request.setMerchantId(MERCHANT);
        request.setDetails("created via onboarding flow");
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditLogEntity saved = service.record(request);

        assertEquals(actor, saved.getActorUserId());
        assertEquals("SUPER_ADMIN", saved.getActorRole());
        assertEquals("MERCHANT_CREATED", saved.getAction());
        assertEquals("MERCHANT", saved.getEntityType());
        assertEquals(MERCHANT.toString(), saved.getEntityId());
        assertEquals(MERCHANT, saved.getMerchantId());
        assertEquals("created via onboarding flow", saved.getDetails());
    }

    @Test
    void getLogsFiltersByMerchantIdWhenProvided() {
        List<AuditLogEntity> expected = List.of(AuditLogEntity.builder().merchantId(MERCHANT).build());
        when(auditLogRepository.findByMerchantIdOrderByCreatedAtDesc(MERCHANT)).thenReturn(expected);

        List<AuditLogEntity> result = service.getLogs(MERCHANT, null);

        assertEquals(expected, result);
        verify(auditLogRepository, never()).findAllByOrderByCreatedAtDesc();
        verify(auditLogRepository, never()).findByActionOrderByCreatedAtDesc(any());
    }

    @Test
    void getLogsFiltersByActionWhenMerchantIdIsAbsent() {
        List<AuditLogEntity> expected = List.of(AuditLogEntity.builder().action("MERCHANT_CREATED").build());
        when(auditLogRepository.findByActionOrderByCreatedAtDesc("MERCHANT_CREATED")).thenReturn(expected);

        List<AuditLogEntity> result = service.getLogs(null, "MERCHANT_CREATED");

        assertEquals(expected, result);
        verify(auditLogRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void getLogsReturnsEverythingWhenNoFilterIsGiven() {
        List<AuditLogEntity> expected = List.of(AuditLogEntity.builder().build());
        when(auditLogRepository.findAllByOrderByCreatedAtDesc()).thenReturn(expected);

        List<AuditLogEntity> result = service.getLogs(null, null);

        assertEquals(expected, result);
    }

    @Test
    void getLogsTreatsABlankActionAsNoFilter() {
        List<AuditLogEntity> expected = List.of(AuditLogEntity.builder().build());
        when(auditLogRepository.findAllByOrderByCreatedAtDesc()).thenReturn(expected);

        List<AuditLogEntity> result = service.getLogs(null, "  ");

        assertEquals(expected, result);
        verify(auditLogRepository, never()).findByActionOrderByCreatedAtDesc(any());
    }

    @Test
    void countDelegatesToTheRepository() {
        when(auditLogRepository.count()).thenReturn(7L);

        assertEquals(7L, service.count());
    }
}
