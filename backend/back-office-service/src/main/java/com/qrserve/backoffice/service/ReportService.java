package com.qrserve.backoffice.service;

import com.qrserve.backoffice.dto.PlatformSummaryResponse;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
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

import java.util.List;
import java.util.Map;

/**
 * Cross-tenant, SUPER_ADMIN-only reporting. Deliberately reuses merchant-
 * service's EXISTING endpoints (no new endpoints added there) via an
 * internal service token, the same pattern BranchMenuBackfillRunner uses —
 * this service holds no merchant/branch data of its own.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final RestTemplate restTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;

    @Value("${services.merchant-service-url:http://localhost:8085}")
    private String merchantServiceUrl;

    public PlatformSummaryResponse getPlatformSummary() {
        HttpHeaders headers = serviceAuthHeaders();
        List<Map<String, Object>> merchants = fetchAllMerchants(headers);

        long branchCount = 0;
        for (Map<String, Object> merchant : merchants) {
            String merchantId = (String) merchant.get("id");
            branchCount += fetchBranchesForMerchant(merchantId, headers).size();
        }

        return PlatformSummaryResponse.builder()
                .merchantCount(merchants.size())
                .branchCount(branchCount)
                .auditEventCount(auditLogService.count())
                .build();
    }

    private HttpHeaders serviceAuthHeaders() {
        UserPrincipal serviceIdentity = UserPrincipal.builder().role(UserRole.SUPER_ADMIN).build();
        String token = jwtTokenProvider.generateInternalServiceToken(serviceIdentity);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchAllMerchants(HttpHeaders headers) {
        try {
            String url = merchantServiceUrl + "/api/merchants";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> body = response.getBody();
            return body == null ? List.of() : body;
        } catch (Exception e) {
            log.error("Failed to fetch merchants from merchant-service for platform summary", e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchBranchesForMerchant(String merchantId, HttpHeaders headers) {
        try {
            String url = merchantServiceUrl + "/api/branches/merchant/" + merchantId;
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> body = response.getBody();
            return body == null ? List.of() : body;
        } catch (Exception e) {
            log.error("Failed to fetch branches for merchant {} for platform summary", merchantId, e);
            return List.of();
        }
    }
}
