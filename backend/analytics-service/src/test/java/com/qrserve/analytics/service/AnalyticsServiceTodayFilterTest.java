package com.qrserve.analytics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalyticsServiceTodayFilterTest {

    // 2026-10-12 10:00 in Addis Ababa (UTC+3) = 07:00 UTC.
    private static final Instant NOW = Instant.parse("2026-10-12T07:00:00Z");

    private static Map<String, Object> order(String id, String createdAtUtc, String status) {
        Map<String, Object> o = new HashMap<>();
        o.put("id", id);
        o.put("createdAt", createdAtUtc);
        o.put("status", status);
        return o;
    }

    private static List<Object> ids(List<Map<String, Object>> orders) {
        return orders.stream().map(o -> o.get("id")).toList();
    }

    @Test
    @DisplayName("keeps only orders inside today's Addis Ababa business day")
    void filtersToAddisBusinessDay() {
        List<Map<String, Object>> orders = List.of(
                order("yesterday-late", "2026-10-11T20:59:59", "DELIVERED"), // 23:59:59 Addis, 11 Oct
                order("midnight", "2026-10-11T21:00:00", "DELIVERED"),       // 00:00 Addis, 12 Oct
                order("morning", "2026-10-12T06:30:00.123456", "PENDING"),
                order("last-second", "2026-10-12T20:59:59", "PAID"),         // 23:59:59 Addis, 12 Oct
                order("tomorrow", "2026-10-12T21:00:00", "PENDING"));

        assertEquals(List.of("midnight", "morning", "last-second"),
                ids(AnalyticsService.todaysOrders(orders, NOW)));
    }

    @Test
    @DisplayName("excludes cancelled orders and orders without a parseable createdAt")
    void excludesCancelledAndUndated() {
        List<Map<String, Object>> orders = List.of(
                order("ok", "2026-10-12T06:00:00", "ACCEPTED"),
                order("cancelled", "2026-10-12T06:00:00", "CANCELLED"),
                order("no-date", null, "PENDING"),
                order("bad-date", "yesterday", "PENDING"));

        assertEquals(List.of("ok"), ids(AnalyticsService.todaysOrders(orders, NOW)));
    }
}
