package com.qrserve.analytics.service;

import com.qrserve.analytics.dto.PopularItemDto;
import com.qrserve.analytics.dto.RevenueAnalyticsResponse;
import com.qrserve.analytics.dto.TodayAnalyticsResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import com.qrserve.shared.exceptions.ServiceUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder; // Web context
import org.springframework.web.context.request.ServletRequestAttributes; // Web context attributes

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final RestTemplate restTemplate;

    @Value("${services.merchant-service-url:http://localhost:8085}")
    private String merchantServiceUrl;

    @Value("${services.order-service-url:http://localhost:8083}")
    private String orderServiceUrl;

    public TodayAnalyticsResponse getTodayMetrics(UUID merchantId) {
        // merchantId is now actually used: it is forwarded to both downstream
        // services so each merchant sees only their own revenue and occupancy.
        List<Map<String, Object>> orders = fetchOrders(merchantId);

        BigDecimal todayRev = orders.stream()
                .map(o -> new BigDecimal(o.getOrDefault("totalAmount", "0").toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingCount = orders.stream()
                .filter(o -> "PENDING".equalsIgnoreCase(o.getOrDefault("status", "").toString()))
                .count();

        BigDecimal avgOrderVal = orders.isEmpty() ? BigDecimal.ZERO : 
                todayRev.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);

        // Fetch tables from merchant-service
        List<Map<String, Object>> tables = fetchTables(merchantId);

        long occupied = tables.stream()
                .filter(t -> "OCCUPIED".equalsIgnoreCase(t.getOrDefault("status", "").toString()))
                .count();

        double occupancyRate = tables.isEmpty() ? 0.0 : ((double) occupied / tables.size()) * 100.0;

        return TodayAnalyticsResponse.builder()
                .todayRevenue(todayRev)
                .totalOrders(orders.size())
                .pendingOrders(pendingCount)
                .avgOrderValue(avgOrderVal)
                .occupiedTables(occupied)
                .totalTables(tables.size())
                .occupancyRate(Math.round(occupancyRate * 10.0) / 10.0)
                .build();
    }

    /**
     * PLACEHOLDER — the three historical data points below are hardcoded, not
     * aggregated. Only the final point (today) reflects real data. Do not present
     * this as a sales trend to users without implementing real aggregation from an
     * order read model or Kafka projection; that work is tracked as deferred in
     * docs/superpowers/plans/2026-08-18-codebase-review-remediation.md.
     */
    public RevenueAnalyticsResponse getRevenueAnalytics(UUID merchantId) {
        TodayAnalyticsResponse today = getTodayMetrics(merchantId);
        List<RevenueAnalyticsResponse.DailySalesPoint> points = List.of(
                new RevenueAnalyticsResponse.DailySalesPoint(LocalDate.now().minusDays(3).toString(), new BigDecimal("1250.00"), 12),
                new RevenueAnalyticsResponse.DailySalesPoint(LocalDate.now().minusDays(2).toString(), new BigDecimal("1840.50"), 18),
                new RevenueAnalyticsResponse.DailySalesPoint(LocalDate.now().minusDays(1).toString(), new BigDecimal("2100.00"), 22),
                new RevenueAnalyticsResponse.DailySalesPoint(LocalDate.now().toString(), today.getTodayRevenue(), today.getTotalOrders())
        );

        return RevenueAnalyticsResponse.builder()
                .totalRevenue(today.getTodayRevenue())
                .salesHistory(points)
                .build();
    }

    /**
     * PLACEHOLDER — returns three fixed demo items with stock photo URLs, ignoring
     * merchantId entirely. This is not real bestseller data. Tracked as deferred
     * work alongside {@link #getRevenueAnalytics}.
     */
    public List<PopularItemDto> getPopularItems(UUID merchantId) {
        return List.of(
                PopularItemDto.builder()
                        .productId(1L)
                        .name("Speciality Cappuccino")
                        .image("https://images.unsplash.com/photo-1534778101976-62847782c213?auto=format&fit=crop&w=400&q=80")
                        .count(42)
                        .revenue(new BigDecimal("7560.00"))
                        .build(),
                PopularItemDto.builder()
                        .productId(2L)
                        .name("Avocado Toast Deluxe")
                        .image("https://images.unsplash.com/photo-1525351484163-7529414344d8?auto=format&fit=crop&w=400&q=80")
                        .count(28)
                        .revenue(new BigDecimal("6160.00"))
                        .build(),
                PopularItemDto.builder()
                        .productId(3L)
                        .name("Artisanal Croissant")
                        .image("https://images.unsplash.com/photo-1555507036-ab1f4038808a?auto=format&fit=crop&w=400&q=80")
                        .count(19)
                        .revenue(new BigDecimal("2280.00"))
                        .build()
        );
    }

    /**
     * Fetches orders for a single merchant.
     *
     * <p>The merchantId is now forwarded so filtering happens in order-service.
     * Previously this fetched every order in the system and the caller's
     * merchantId was discarded, so every merchant saw global revenue.
     *
     * @throws ServiceUnavailableException if order-service cannot be reached — the
     *         previous behaviour returned an empty list, which is indistinguishable
     *         from "no sales today" on the dashboard.
     */
    private List<Map<String, Object>> fetchOrders(UUID merchantId) {
        String url = orderServiceUrl + "/api/orders"
                + (merchantId != null ? "?merchantId=" + merchantId : "");
        try {
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (RestClientException e) {
            log.error("Failed to fetch orders from order-service at {}", url, e);
            throw new ServiceUnavailableException("order-service is unavailable", e);
        }
    }

    /**
     * Fetches tables for a single merchant.
     *
     * <p>NOTE the path is {@code /api/tables/all}. The previous {@code /api/tables}
     * had no controller mapping, so every call 404'd; the exception was swallowed
     * and the dashboard silently reported zero tables and 0% occupancy.
     */
    private List<Map<String, Object>> fetchTables(UUID merchantId) {
        String url = merchantServiceUrl + "/api/tables/all"
                + (merchantId != null ? "?merchantId=" + merchantId : "");
        try {
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (RestClientException e) {
            log.error("Failed to fetch tables from merchant-service at {}", url, e);
            throw new ServiceUnavailableException("merchant-service is unavailable", e);
        }
    }

    /**
     * Extracts Authorization Header from the current request thread
     */
    private HttpHeaders getAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authToken = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authToken != null && !authToken.isEmpty()) {
                headers.set(HttpHeaders.AUTHORIZATION, authToken);
            }
        }
        return headers;
    }

}