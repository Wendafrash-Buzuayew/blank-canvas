package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.DigitalMenuResolutionResponse;
import com.qrserve.merchant.service.PublicDigitalMenuResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public (no-JWT) resolution for the phase-1 Digital Menu URL family
 * ({@code /m/{merchant-slug}[/{branch-slug}]}). Exposed under
 * {@code /api/v1/public/**} so the gateway routes it without a session,
 * exactly like {@link PublicMenuController} — a sibling of that controller,
 * which stays untouched.
 */
@RestController
@RequestMapping("/api/v1/public/digital-menu")
@RequiredArgsConstructor
@Tag(name = "Public Digital Menu Resolution", description = "Unauthenticated resolution for the phase-1 path-based menu URLs")
public class PublicDigitalMenuController {

    private final PublicDigitalMenuResolutionService resolutionService;

    @GetMapping("/{merchantSlug}")
    @Operation(summary = "Resolve a merchant's primary branch, for the /m/{slug} short link")
    public ResponseEntity<DigitalMenuResolutionResponse> resolvePrimary(@PathVariable String merchantSlug) {
        return ResponseEntity.ok(resolutionService.resolvePrimary(merchantSlug));
    }

    @GetMapping("/{merchantSlug}/{branchSlug}")
    @Operation(summary = "Resolve a specific branch's canonical digital-menu URL")
    public ResponseEntity<DigitalMenuResolutionResponse> resolveBranch(
            @PathVariable String merchantSlug, @PathVariable String branchSlug) {
        return ResponseEntity.ok(resolutionService.resolveBranch(merchantSlug, branchSlug));
    }
}
