package com.qrserve.auth.superapp;

import com.qrserve.auth.dto.LoginResponse;
import com.qrserve.auth.entity.UserEntity;
import com.qrserve.auth.repository.UserRepository;
import com.qrserve.shared.exceptions.ServiceUnavailableException;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Turns a Super App token into a QRServe session, auto-registering the
 * merchant on first entry. See
 * docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §5-6.
 *
 * <p>Provisioning calls merchant-service's EXISTING POST /api/merchants,
 * POST /api/branches and POST /api/tables endpoints, authenticated with a
 * short-lived internal SUPER_ADMIN token minted here and never returned to
 * any client. No merchant-service code changes at all - those endpoints
 * already accept SUPER_ADMIN, and POST /api/tables already provisions the
 * table's QR in the same call.
 *
 * <p>Known limitation, accepted rather than fixed: this is three remote HTTP
 * calls plus one local save, not a distributed transaction. If the local
 * UserEntity save fails after remote provisioning succeeds, a retry will not
 * find a superAppMerchantRef match and will provision a duplicate merchant.
 * Fixing this needs an idempotency key or outbox pattern - out of scope here.
 */
@Service
@RequiredArgsConstructor
public class SuperAppProvisioningService {

    private static final String PLACEHOLDER_EMAIL_DOMAIN = "superapp.qrserve.internal";

    private final SuperAppAuthPort superAppAuthPort;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RestTemplate restTemplate;

    @Value("${services.merchant-service-url:http://localhost:8085}")
    private String merchantServiceUrl;

    public LoginResponse exchangeAndLogin(String rawToken) {
        SuperAppMerchantClaim claim = superAppAuthPort.exchangeToken(rawToken);

        UserEntity user = userRepository.findBySuperAppMerchantRef(claim.merchantExternalRef())
                .orElseGet(() -> provisionMerchantOwner(claim));

        return issueTokens(user);
    }

    private UserEntity provisionMerchantOwner(SuperAppMerchantClaim claim) {
        String systemToken = mintSystemToken();

        MerchantProvisionResponse merchant = createMerchant(claim, systemToken);
        BranchProvisionResponse branch = createDefaultBranch(merchant.id(), claim, systemToken);
        createDefaultTable(branch.id(), systemToken);

        UserEntity user = UserEntity.builder()
                .merchantId(merchant.id())
                .name(claim.businessName())
                .email(claim.merchantExternalRef().toLowerCase() + "@" + PLACEHOLDER_EMAIL_DOMAIN)
                // Never surfaced or logged in - this account only ever authenticates
                // via Super App token exchange, so the password only needs to exist
                // to satisfy the NOT NULL column.
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(UserRole.MERCHANT_OWNER)
                .enabled(true)
                .superAppMerchantRef(claim.merchantExternalRef())
                .build();
        return userRepository.save(user);
    }

    /**
     * A synthetic, never-persisted, never-returned token used only for the
     * three provisioning calls below. It authenticates as SUPER_ADMIN because
     * that is the one role every one of those three endpoints already accepts.
     */
    private String mintSystemToken() {
        UserPrincipal system = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .role(UserRole.SUPER_ADMIN)
                .email("system@" + PLACEHOLDER_EMAIL_DOMAIN)
                .build();
        return tokenProvider.generateAccessToken(system);
    }

    private MerchantProvisionResponse createMerchant(SuperAppMerchantClaim claim, String systemToken) {
        Map<String, String> body = Map.of(
                "name", claim.businessName(),
                "slug", claim.businessName(),
                "phone", claim.phone(),
                "city", claim.city(),
                "address", claim.address(),
                "category", claim.category());
        ResponseEntity<MerchantProvisionResponse> response = restTemplate.exchange(
                merchantServiceUrl + "/api/merchants",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(systemToken)),
                MerchantProvisionResponse.class);
        return requireBody(response, "merchant provisioning");
    }

    private BranchProvisionResponse createDefaultBranch(UUID merchantId, SuperAppMerchantClaim claim, String systemToken) {
        Map<String, Object> body = Map.of(
                "merchantId", merchantId.toString(),
                "name", "Main",
                "slug", "main",
                "phone", claim.phone(),
                "address", claim.address());
        ResponseEntity<BranchProvisionResponse> response = restTemplate.exchange(
                merchantServiceUrl + "/api/branches",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(systemToken)),
                BranchProvisionResponse.class);
        return requireBody(response, "branch provisioning");
    }

    private void createDefaultTable(Long branchId, String systemToken) {
        Map<String, Object> body = Map.of(
                "branchId", branchId,
                "tableNumber", "1",
                "capacity", 1);
        restTemplate.exchange(
                merchantServiceUrl + "/api/tables",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(systemToken)),
                Object.class);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private <T> T requireBody(ResponseEntity<T> response, String what) {
        T body = response.getBody();
        if (body == null) {
            throw new ServiceUnavailableException(what + " returned an empty response");
        }
        return body;
    }

    private LoginResponse issueTokens(UserEntity user) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(user.getId())
                .merchantId(user.getMerchantId())
                .email(user.getEmail())
                .password(user.getPasswordHash())
                .role(user.getRole())
                .build();
        return LoginResponse.builder()
                .accessToken(tokenProvider.generateAccessToken(principal))
                .refreshToken(tokenProvider.generateRefreshToken(principal))
                .expiresIn(tokenProvider.getAccessExpirationSeconds())
                .build();
    }
}
