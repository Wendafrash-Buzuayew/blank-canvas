package com.qrserve.analytics.controller;

import com.qrserve.analytics.dto.PopularItemDto;
import com.qrserve.analytics.dto.RevenueAnalyticsResponse;
import com.qrserve.analytics.dto.TodayAnalyticsResponse;
import com.qrserve.analytics.service.AnalyticsService;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
@Tag(name = "Analytics & Reports", description = "Merchant & Branch Revenue, AOV, Occupancy & Bestsellers Metrics APIs")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Resolves which merchant's data the caller may read.
     *
     * <p>Only SUPER_ADMIN may pass an arbitrary {@code merchantId}. Everyone else
     * is pinned to the tenant in their own token — previously the parameter was
     * passed straight through (and then ignored entirely by the service), so every
     * merchant saw global revenue and occupancy.
     */
    private UUID resolveScope(UUID requested, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            return requested;
        }
        UUID own = principal.getMerchantId();
        if (own == null) {
            throw new UnauthorizedException("Caller has no merchant scope");
        }
        return own;
    }

    @GetMapping("/today")
    @Operation(summary = "Get today's key KPI metrics (Revenue, AOV, Active tables, Occupancy)")
    public ResponseEntity<TodayAnalyticsResponse> getTodayMetrics(
            @RequestParam(required = false) UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.getTodayMetrics(resolveScope(merchantId, principal)));
    }

    @GetMapping("/revenue")
    @Operation(summary = "Get revenue analytics and daily sales trend")
    public ResponseEntity<RevenueAnalyticsResponse> getRevenueAnalytics(
            @RequestParam(required = false) UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.getRevenueAnalytics(resolveScope(merchantId, principal)));
    }

    @GetMapping("/popular-items")
    @Operation(summary = "Get top ordered products and dish ranking")
    public ResponseEntity<List<PopularItemDto>> getPopularItems(
            @RequestParam(required = false) UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.getPopularItems(resolveScope(merchantId, principal)));
    }

    // NOTE: GET /api/analytics/orders was removed. It called getTodayMetrics and
    // returned a payload identical to /today, so it was a duplicate endpoint with
    // a misleading name rather than a distinct contract. Verified unused by the
    // frontend before removal. Real order analytics needs an aggregation source
    // and is tracked as deferred work in the remediation plan.
}
