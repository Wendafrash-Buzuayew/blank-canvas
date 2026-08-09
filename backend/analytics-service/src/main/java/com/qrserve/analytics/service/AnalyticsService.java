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
import org.springframework.stereotype.Service;
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
        // Fetch orders from order-service
        List<Map<String, Object>> orders = fetchOrders();

        BigDecimal todayRev = orders.stream()
                .map(o -> new BigDecimal(o.getOrDefault("totalAmount", "0").toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingCount = orders.stream()
                .filter(o -> "PENDING".equalsIgnoreCase(o.getOrDefault("status", "").toString()))
                .count();

        BigDecimal avgOrderVal = orders.isEmpty() ? BigDecimal.ZERO : 
                todayRev.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);

        // Fetch tables from merchant-service 
        List<Map<String, Object>> tables = fetchTables();

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

    private List<Map<String, Object>> fetchOrders() {
        try {
            String url = orderServiceUrl + "/api/orders";
            // Build headers containing the security token
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.warn("Failed to fetch orders from order-service: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> fetchTables() {
        try {
            String url = merchantServiceUrl + "/api/tables";
                // Build headers containing the security token
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.warn("Failed to fetch tables from merchant-service: {}", e.getMessage());
            return List.of();
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