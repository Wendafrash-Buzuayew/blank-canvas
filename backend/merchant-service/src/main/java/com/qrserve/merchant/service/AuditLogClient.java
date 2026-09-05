package com.qrserve.merchant.service;

import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fire-and-forget audit trail writer. A failure here (back-office-service
 * down, network blip) must never fail the caller's own transaction — an
 * unwritten audit entry is a lesser problem than a merchant onboarding that
 * fails because a reporting service hiccuped. Uses the same internal
 * service-token pattern as ReportService in back-office-service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogClient {

    private final RestTemplate restTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${services.back-office-service-url:http://localhost:8090}")
    private String backOfficeServiceUrl;

    public void record(String action, String entityType, String entityId, UUID merchantId, String details) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("actorRole", UserRole.SUPER_ADMIN.name());
            body.put("action", action);
            body.put("entityType", entityType);
            body.put("entityId", entityId);
            body.put("merchantId", merchantId);
            body.put("details", details);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            UserPrincipal serviceIdentity = UserPrincipal.builder().role(UserRole.SUPER_ADMIN).build();
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.generateInternalServiceToken(serviceIdentity));

            restTemplate.postForEntity(backOfficeServiceUrl + "/api/audit-logs", new HttpEntity<>(body, headers), Void.class);
        } catch (Exception e) {
            log.error("Failed to record audit log entry for action {} on {} {}", action, entityType, entityId, e);
        }
    }
}
