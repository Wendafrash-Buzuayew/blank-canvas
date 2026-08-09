package com.qrserve.analytics.controller;

import com.qrserve.analytics.dto.PopularItemDto;
import com.qrserve.analytics.dto.RevenueAnalyticsResponse;
import com.qrserve.analytics.dto.TodayAnalyticsResponse;
import com.qrserve.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics & Reports", description = "Merchant & Branch Revenue, AOV, Occupancy & Bestsellers Metrics APIs")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/today")
    @Operation(summary = "Get today's key KPI metrics (Revenue, AOV, Active tables, Occupancy)")
    public ResponseEntity<TodayAnalyticsResponse> getTodayMetrics(@RequestParam(required = false) UUID merchantId) {
        return ResponseEntity.ok(analyticsService.getTodayMetrics(merchantId));
    }

    @GetMapping("/revenue")
    @Operation(summary = "Get revenue analytics and daily sales trend")
    public ResponseEntity<RevenueAnalyticsResponse> getRevenueAnalytics(@RequestParam(required = false) UUID merchantId) {
        return ResponseEntity.ok(analyticsService.getRevenueAnalytics(merchantId));
    }

    @GetMapping("/popular-items")
    @Operation(summary = "Get top ordered products and dish ranking")
    public ResponseEntity<List<PopularItemDto>> getPopularItems(@RequestParam(required = false) UUID merchantId) {
        return ResponseEntity.ok(analyticsService.getPopularItems(merchantId));
    }

    @GetMapping("/orders")
    @Operation(summary = "Get order analytics summary")
    public ResponseEntity<TodayAnalyticsResponse> getOrdersAnalytics(@RequestParam(required = false) UUID merchantId) {
        return ResponseEntity.ok(analyticsService.getTodayMetrics(merchantId));
    }
}
