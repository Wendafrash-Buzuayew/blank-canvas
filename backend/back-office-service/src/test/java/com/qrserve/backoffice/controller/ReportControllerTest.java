package com.qrserve.backoffice.controller;

import com.qrserve.backoffice.dto.PlatformSummaryResponse;
import com.qrserve.backoffice.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportControllerTest {

    private ReportService reportService;
    private ReportController controller;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        controller = new ReportController(reportService);
    }

    @Test
    void getPlatformSummaryReturnsExactlyWhatTheServiceComputes() {
        PlatformSummaryResponse summary = PlatformSummaryResponse.builder()
                .merchantCount(3).branchCount(7).auditEventCount(12).build();
        when(reportService.getPlatformSummary()).thenReturn(summary);

        var response = controller.getPlatformSummary();

        assertEquals(summary, response.getBody());
    }
}
