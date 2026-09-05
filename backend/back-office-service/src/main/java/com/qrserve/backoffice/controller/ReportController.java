package com.qrserve.backoffice.controller;

import com.qrserve.backoffice.dto.PlatformSummaryResponse;
import com.qrserve.backoffice.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Cross-tenant platform reporting")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/platform-summary")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get platform-wide merchant/branch counts and audit activity")
    public ResponseEntity<PlatformSummaryResponse> getPlatformSummary() {
        return ResponseEntity.ok(reportService.getPlatformSummary());
    }
}
