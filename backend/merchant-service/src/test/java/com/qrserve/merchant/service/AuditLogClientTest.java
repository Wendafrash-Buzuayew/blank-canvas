package com.qrserve.merchant.service;

import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * record() is fire-and-forget on purpose (see class javadoc) — a
 * back-office-service outage must never fail merchant onboarding. These
 * tests exist specifically to prove that contract.
 */
class AuditLogClientTest {

    private RestTemplate restTemplate;
    private JwtTokenProvider jwtTokenProvider;
    private AuditLogClient client;

    @BeforeEach
    void setUp() throws Exception {
        restTemplate = mock(RestTemplate.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        client = new AuditLogClient(restTemplate, jwtTokenProvider);
        when(jwtTokenProvider.generateInternalServiceToken(any(UserPrincipal.class))).thenReturn("service-token");

        Field urlField = AuditLogClient.class.getDeclaredField("backOfficeServiceUrl");
        urlField.setAccessible(true);
        urlField.set(client, "http://back-office-service");
    }

    @SuppressWarnings("unchecked")
    @Test
    void postsTheAuditEventWithABearerTokenToTheConfiguredUrl() {
        UUID merchantId = UUID.randomUUID();

        client.record("MERCHANT_CREATED", "MERCHANT", merchantId.toString(), merchantId, "slug=sunrise");

        verify(restTemplate).postForEntity(eq("http://back-office-service/api/audit-logs"),
                argThat((HttpEntity<Map<String, Object>> entity) -> {
                    HttpHeaders headers = entity.getHeaders();
                    Map<String, Object> body = entity.getBody();
                    return "Bearer service-token".equals(headers.getFirst(HttpHeaders.AUTHORIZATION))
                            && "MERCHANT_CREATED".equals(body.get("action"))
                            && "MERCHANT".equals(body.get("entityType"))
                            && merchantId.equals(body.get("merchantId"));
                }), eq(Void.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void aFailureToReachBackOfficeServiceIsSwallowedNotPropagated() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new RestClientException("back-office-service unreachable"));

        assertDoesNotThrow(() -> client.record("MERCHANT_CREATED", "MERCHANT", "id", UUID.randomUUID(), "details"));
    }
}
