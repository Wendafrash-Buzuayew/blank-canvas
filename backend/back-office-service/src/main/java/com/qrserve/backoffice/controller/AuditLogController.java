package com.qrserve.backoffice.controller;

import com.qrserve.backoffice.dto.CreateAuditLogRequest;
import com.qrserve.backoffice.entity.AuditLogEntity;
import com.qrserve.backoffice.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * SUPER_ADMIN-only in both directions: a real admin reading the log, or
 * another service recording an event via its own internal service token
 * (JwtTokenProvider.generateInternalServiceToken — an ordinary access token
 * carrying SUPER_ADMIN, so it authenticates through the normal JWT filter
 * exactly like a real admin session would, just short-lived).
 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Log", description = "Append-only record of significant platform actions")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Record a platform action")
    public ResponseEntity<AuditLogEntity> record(@Valid @RequestBody CreateAuditLogRequest request) {
        return ResponseEntity.ok(auditLogService.record(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List audit log entries, optionally filtered by merchant or action")
    public ResponseEntity<List<AuditLogEntity>> getLogs(
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) String action) {
        return ResponseEntity.ok(auditLogService.getLogs(merchantId, action));
    }
}
