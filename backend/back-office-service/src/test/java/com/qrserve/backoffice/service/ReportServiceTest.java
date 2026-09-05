package com.qrserve.backoffice.service;

import com.qrserve.backoffice.dto.PlatformSummaryResponse;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {

    private RestTemplate restTemplate;
    private JwtTokenProvider jwtTokenProvider;
    private AuditLogService auditLogService;
    private ReportService service;

    @BeforeEach
    void setUp() throws Exception {
        restTemplate = mock(RestTemplate.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        auditLogService = mock(AuditLogService.class);
        service = new ReportService(restTemplate, jwtTokenProvider, auditLogService);
        when(jwtTokenProvider.generateInternalServiceToken(any(UserPrincipal.class))).thenReturn("service-token");

        Field urlField = ReportService.class.getDeclaredField("merchantServiceUrl");
        urlField.setAccessible(true);
        urlField.set(service, "http://merchant-service");
    }

    @SuppressWarnings("unchecked")
    @Test
    void platformSummarySumsBranchesAcrossEveryMerchantAndIncludesAuditCount() {
        List<Map<String, Object>> merchants = List.of(Map.of("id", "m1"), Map.of("id", "m2"));
        when(restTemplate.exchange(eq("http://merchant-service/api/merchants"), eq(HttpMethod.GET), any(HttpEntity.class), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(merchants));
        when(restTemplate.exchange(eq("http://merchant-service/api/branches/merchant/m1"), eq(HttpMethod.GET), any(HttpEntity.class), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of(Map.of("id", "b1"), Map.of("id", "b2"))));
        when(restTemplate.exchange(eq("http://merchant-service/api/branches/merchant/m2"), eq(HttpMethod.GET), any(HttpEntity.class), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of(Map.of("id", "b3"))));
        when(auditLogService.count()).thenReturn(42L);

        PlatformSummaryResponse summary = service.getPlatformSummary();

        assertEquals(2, summary.getMerchantCount());
        assertEquals(3, summary.getBranchCount());
        assertEquals(42L, summary.getAuditEventCount());
    }

    @SuppressWarnings("unchecked")
    @Test
    void aBearerTokenFromTheInternalServiceCredentialIsSentOnEveryCall() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of()));
        when(auditLogService.count()).thenReturn(0L);

        service.getPlatformSummary();

        verify(restTemplate).exchange(eq("http://merchant-service/api/merchants"), eq(HttpMethod.GET), argThat((HttpEntity<?> entity) -> {
            HttpHeaders headers = entity.getHeaders();
            return "Bearer service-token".equals(headers.getFirst(HttpHeaders.AUTHORIZATION));
        }), any(org.springframework.core.ParameterizedTypeReference.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void aFailedMerchantServiceCallDegradesToAnEmptySummaryInsteadOfThrowing() {
        when(restTemplate.exchange(eq("http://merchant-service/api/merchants"), eq(HttpMethod.GET), any(HttpEntity.class), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("merchant-service unreachable"));
        when(auditLogService.count()).thenReturn(5L);

        PlatformSummaryResponse summary = service.getPlatformSummary();

        assertEquals(0, summary.getMerchantCount());
        assertEquals(0, summary.getBranchCount());
        assertEquals(5L, summary.getAuditEventCount());
    }

    @SuppressWarnings("unchecked")
    @Test
    void aFailedBranchLookupForOneMerchantDoesNotAbortTheOthers() {
        List<Map<String, Object>> merchants = List.of(Map.of("id", "m1"), Map.of("id", "m2"));
        when(restTemplate.exchange(eq("http://merchant-service/api/merchants"), eq(HttpMethod.GET), any(HttpEntity.class), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(merchants));
        when(restTemplate.exchange(eq("http://merchant-service/api/branches/merchant/m1"), eq(HttpMethod.GET), any(HttpEntity.class), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("timeout"));
        when(restTemplate.exchange(eq("http://merchant-service/api/branches/merchant/m2"), eq(HttpMethod.GET), any(HttpEntity.class), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of(Map.of("id", "b3"))));
        when(auditLogService.count()).thenReturn(0L);

        PlatformSummaryResponse summary = service.getPlatformSummary();

        assertEquals(2, summary.getMerchantCount());
        assertEquals(1, summary.getBranchCount());
    }
}
