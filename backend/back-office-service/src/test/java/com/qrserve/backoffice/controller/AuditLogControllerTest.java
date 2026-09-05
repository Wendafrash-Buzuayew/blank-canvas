package com.qrserve.backoffice.controller;

import com.qrserve.backoffice.dto.CreateAuditLogRequest;
import com.qrserve.backoffice.entity.AuditLogEntity;
import com.qrserve.backoffice.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditLogControllerTest {

    private AuditLogService auditLogService;
    private AuditLogController controller;

    @BeforeEach
    void setUp() {
        auditLogService = mock(AuditLogService.class);
        controller = new AuditLogController(auditLogService);
    }

    @Test
    void recordDelegatesToTheServiceAndReturnsTheSavedEntity() {
        CreateAuditLogRequest request = new CreateAuditLogRequest();
        request.setAction("MERCHANT_CREATED");
        request.setEntityType("MERCHANT");
        AuditLogEntity saved = AuditLogEntity.builder().id(1L).action("MERCHANT_CREATED").build();
        when(auditLogService.record(request)).thenReturn(saved);

        var response = controller.record(request);

        assertEquals(saved, response.getBody());
    }

    @Test
    void getLogsPassesMerchantIdAndActionThroughToTheService() {
        UUID merchantId = UUID.randomUUID();
        List<AuditLogEntity> expected = List.of(AuditLogEntity.builder().id(2L).build());
        when(auditLogService.getLogs(merchantId, "MERCHANT_CREATED")).thenReturn(expected);

        var response = controller.getLogs(merchantId, "MERCHANT_CREATED");

        assertEquals(expected, response.getBody());
    }

    @Test
    void getLogsWithNoFiltersPassesNullsThrough() {
        List<AuditLogEntity> expected = List.of();
        when(auditLogService.getLogs(null, null)).thenReturn(expected);

        var response = controller.getLogs(null, null);

        assertEquals(expected, response.getBody());
    }
}
