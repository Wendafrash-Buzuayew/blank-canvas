package com.qrserve.merchant.controller;

import com.qrserve.merchant.service.PublicMenuResolutionService;
import com.qrserve.shared.common.dto.PublicMenuResolutionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public (no-JWT) endpoint for resolving a QR-scanned menu URL
 * {@code GET /api/v1/public/menu/{merchantSlug}/{branchSlug}/{tableNumber}}.
 *
 * <p>Exposed under {@code /api/v1/public/**} so the API gateway routes it
 * without requiring a full user session. All database lookups are scoped by
 * {@code merchantId} and {@code branchId}, and the QR signature is validated
 * before any internal identifiers are returned.</p>
 */
@RestController
@RequestMapping("/api/v1/public/menu")
@RequiredArgsConstructor
@Tag(name = "Public Menu Resolution", description = "Unauthenticated menu resolution for QR-scanned URLs")
public class PublicMenuController {

    private final PublicMenuResolutionService resolutionService;

    @GetMapping("/{merchantSlug}/{branchSlug}/{tableNumber}")
    @Operation(summary = "Resolve a public menu URL to internal merchant/branch/table identifiers")
    public ResponseEntity<PublicMenuResolutionResponse> resolveMenu(
            @PathVariable String merchantSlug,
            @PathVariable String branchSlug,
            @PathVariable String tableNumber,
            @RequestParam(required = false) String signature) {

        PublicMenuResolutionResponse resolution = resolutionService.resolve(
                merchantSlug, branchSlug, tableNumber, signature);

        return ResponseEntity.ok(resolution);
    }

    @GetMapping("/health")
    @Operation(summary = "Public health check for unauthenticated reachability")
    public ResponseEntity<java.util.Map<String, String>> health() {
        return ResponseEntity.ok(java.util.Map.of("status", "UP"));
    }
}