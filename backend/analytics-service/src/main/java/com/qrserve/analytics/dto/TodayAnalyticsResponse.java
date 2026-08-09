package com.qrserve.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodayAnalyticsResponse {
    private BigDecimal todayRevenue;
    private long totalOrders;
    private long pendingOrders;
    private BigDecimal avgOrderValue;
    private long occupiedTables;
    private long totalTables;
    private double occupancyRate;
}
