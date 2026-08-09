package com.qrserve.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueAnalyticsResponse {
    private BigDecimal totalRevenue;
    private List<DailySalesPoint> salesHistory;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DailySalesPoint {
        private String date;
        private BigDecimal revenue;
        private long ordersCount;
    }
}
