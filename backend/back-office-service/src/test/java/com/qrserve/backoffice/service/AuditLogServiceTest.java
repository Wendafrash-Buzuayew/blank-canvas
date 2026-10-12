package com.qrserve.backoffice.service;

import com.qrserve.backoffice.dto.CreateAuditLogRequest;
import com.qrserve.backoffice.entity.AuditLogEntity;
import com.qrserve.backoffice.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    private static final Pageable FIRST_100 = PageRequest.of(0, 100);

    @Test
    void getLogsFiltersByMerchantIdWhenProvided() {
        List<AuditLogEntity> expected = List.of(AuditLogEntity.builder().merchantId(MERCHANT).build());
        when(auditLogRepository.findByMerchantIdOrderByCreatedAtDesc(MERCHANT, FIRST_100)).thenReturn(expected);

        assertEquals(expected, service.getLogs(MERCHANT, null, 0, 100));
        verify(auditLogRepository, never()).findAllByOrderByCreatedAtDesc(any());
        verify(auditLogRepository, never()).findByActionOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void getLogsFiltersByActionWhenMerchantIdIsAbsent() {
        List<AuditLogEntity> expected = List.of(AuditLogEntity.builder().action("MERCHANT_CREATED").build());
        when(auditLogRepository.findByActionOrderByCreatedAtDesc("MERCHANT_CREATED", FIRST_100)).thenReturn(expected);

        assertEquals(expected, service.getLogs(null, "MERCHANT_CREATED", 0, 100));
        verify(auditLogRepository, never()).findAllByOrderByCreatedAtDesc(any());
    }

    @Test
    void getLogsAppliesBothFiltersTogether() {
        List<AuditLogEntity> expected = List.of(AuditLogEntity.builder().merchantId(MERCHANT).action("MERCHANT_CREATED").build());
        when(auditLogRepository.findByMerchantIdAndActionOrderByCreatedAtDesc(MERCHANT, "MERCHANT_CREATED", FIRST_100))
                .thenReturn(expected);

        assertEquals(expected, service.getLogs(MERCHANT, "MERCHANT_CREATED", 0, 100));
        verify(auditLogRepository, never()).findByMerchantIdOrderByCreatedAtDesc(any(), any());
        verify(auditLogRepository, never()).findByActionOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void getLogsReturnsEverythingWhenNoFilterIsGiven() {
        List<AuditLogEntity> expected = List.of(AuditLogEntity.builder().build());
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(FIRST_100)).thenReturn(expected);

        assertEquals(expected, service.getLogs(null, null, 0, 100));
    }

    @Test
    void getLogsTreatsABlankActionAsNoFilter() {
        List<AuditLogEntity> expected = List.of(AuditLogEntity.builder().build());
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(FIRST_100)).thenReturn(expected);

        assertEquals(expected, service.getLogs(null, "  ", 0, 100));
        verify(auditLogRepository, never()).findByActionOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void getLogsClampsPageAndSize() {
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(List.of());

        service.getLogs(null, null, -3, 10_000);
        verify(auditLogRepository).findAllByOrderByCreatedAtDesc(PageRequest.of(0, AuditLogService.MAX_PAGE_SIZE));

        service.getLogs(null, null, 2, 0);
        verify(auditLogRepository).findAllByOrderByCreatedAtDesc(PageRequest.of(2, 1));
    }
    @Test
    void countDelegatesToTheRepository() {
        when(auditLogRepository.count()).thenReturn(7L);

        assertEquals(7L, service.count());
    }
}
