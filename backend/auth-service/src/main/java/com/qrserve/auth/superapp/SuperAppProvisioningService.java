package com.qrserve.auth.superapp;

import com.qrserve.auth.dto.LoginResponse;
import com.qrserve.auth.dto.SuperAppLoginRequest;
import com.qrserve.auth.entity.UserEntity;
import com.qrserve.auth.repository.UserRepository;
import com.qrserve.shared.exceptions.ServiceUnavailableException;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
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
@Slf4j
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

    /**
     * Same fail-closed flag DevFakeSuperAppAuthPort guards its own token
     * exchange with — see exchangeAndLoginFromSuperApp below for why this
     * newer entry point needs the identical gate even though it never goes
     * through that port at all.
     */
    @Value("${superapp.auth.dev-fake-enabled:false}")
    private boolean devFakeEnabled;

    /**
     * Placeholder business-profile fields the Super App claim never carries
     * (it only ever hands over a short code and an MSISDN). Merchant-service
     * requires all four non-blank; the onboarding form overwrites them via
     * PUT /api/merchants/{id} before the merchant reaches any other screen -
     * see {@code onboardingComplete} on UserEntity and completeOnboarding()
     * below.
     */
    private static final String PENDING_PROFILE_FIELD = "Pending onboarding";

    public LoginResponse exchangeAndLogin(String rawToken) {
        SuperAppMerchantClaim claim = superAppAuthPort.exchangeToken(rawToken);
        return exchangeAndLogin(claim);
    }

    /**
     * Newer Super App contract (POST /api/auth/superapp-login): msisdn and
     * shortCode arrive as discrete request fields, alongside a
     * signature/superAppToken pair meant to let the caller be
     * cryptographically verified rather than merely trusted.
     *
     * <p><b>signature/superAppToken are NOT verified.</b> No real Super App
     * signing key or algorithm has been supplied yet — the identical
     * situation SuperAppAuthPort's own Javadoc already documents for the
     * token-exchange path. Skipping verification here would be one thing if
     * this endpoint were otherwise locked down, but by itself it is exactly
     * as exploitable as DevFakeSuperAppAuthPort with dev-fake mode on: any
     * caller who supplies a real shortCode gets a MERCHANT_OWNER session for
     * that merchant, no proof required. It is therefore gated behind the
     * SAME devFakeEnabled flag as that port, fully independent of whether
     * that port happens to be wired in — this method never calls it at all,
     * since it already has structured fields with no JSON to parse.
     */
    public LoginResponse exchangeAndLoginFromSuperApp(SuperAppLoginRequest request) {
        if (!devFakeEnabled) {
            throw new UnauthorizedException(
                    "Super App login is not enabled on this server. Set SUPERAPP_DEV_FAKE_ENABLED=true "
                            + "for local/dev/staging use only, until real signature verification is implemented.");
        }
        if (request.getSignature() == null || request.getSignature().isBlank()) {
            log.warn("Super App login for shortCode {} carried no signature - verification is not implemented "
                    + "yet (no real Super App signing contract exists); accepting on trust, same as the "
                    + "existing dev-fake token-exchange path.", request.getShortCode());
        } else {
            log.warn("Super App login for shortCode {} carried a signature, but it is NOT verified - no real "
                    + "signing key/algorithm has been supplied yet. This login is no more trustworthy than the "
                    + "dev-fake exchange until that changes.", request.getShortCode());
        }
        return exchangeAndLogin(new SuperAppMerchantClaim(request.getShortCode(), request.getMsisdn()));
    }

    private LoginResponse exchangeAndLogin(SuperAppMerchantClaim claim) {
        UserEntity user = userRepository.findBySuperAppMerchantRef(claim.merchantShortCode())
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
                .name("Merchant " + claim.merchantShortCode())
                .email(claim.merchantShortCode().toLowerCase() + "@" + PLACEHOLDER_EMAIL_DOMAIN)
                // Never surfaced or logged in - this account only ever authenticates
                // via Super App token exchange, so the password only needs to exist
                // to satisfy the NOT NULL column. Onboarding may let the merchant set
                // a real email/password later as an optional browser-login fallback.
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(UserRole.MERCHANT_OWNER)
                .enabled(true)
                .superAppMerchantRef(claim.merchantShortCode())
                .onboardingComplete(false)
                .build();
        log.info("Provisioned new merchant {} (branch {}) for Super App short code {}", merchant.id(), branch.id(), claim.merchantShortCode());
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
        return tokenProvider.generateInternalServiceToken(system);
    }

    private MerchantProvisionResponse createMerchant(SuperAppMerchantClaim claim, String systemToken) {
        // name/slug come from the short code (merchant-service dedupes the slug
        // itself, see MerchantService.firstAvailableSlug); city/address/category
        // have no Super App source yet and are filled in at onboarding.
        // shortCode IS a real Super App field, unlike those three — it's
        // persisted as-is so menu-service's ETHQR proxy has it later without
        // asking the merchant to type their own till number back in.
        Map<String, String> body = Map.of(
                "name", "Merchant " + claim.merchantShortCode(),
                "slug", claim.merchantShortCode(),
                "phone", claim.msisdn(),
                "city", PENDING_PROFILE_FIELD,
                "address", PENDING_PROFILE_FIELD,
                "category", PENDING_PROFILE_FIELD,
                "shortCode", claim.merchantShortCode());
        try {
            ResponseEntity<MerchantProvisionResponse> response = restTemplate.exchange(
                    merchantServiceUrl + "/api/merchants",
                    HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders(systemToken)),
                    MerchantProvisionResponse.class);
            return requireBody(response, "merchant provisioning");
        } catch (RestClientException e) {
            log.warn("Merchant provisioning failed for Super App short code {}", claim.merchantShortCode(), e);
            throw new ServiceUnavailableException("merchant-service is unavailable during merchant provisioning", e);
        }
    }

    private BranchProvisionResponse createDefaultBranch(UUID merchantId, SuperAppMerchantClaim claim, String systemToken) {
        Map<String, Object> body = Map.of(
                "merchantId", merchantId.toString(),
                "name", "Main",
                "slug", "main",
                "phone", claim.msisdn(),
                "address", PENDING_PROFILE_FIELD);
        try {
            ResponseEntity<BranchProvisionResponse> response = restTemplate.exchange(
                    merchantServiceUrl + "/api/branches",
                    HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders(systemToken)),
                    BranchProvisionResponse.class);
            return requireBody(response, "branch provisioning");
        } catch (RestClientException e) {
            log.warn("Branch provisioning failed for Super App short code {}", claim.merchantShortCode(), e);
            throw new ServiceUnavailableException("merchant-service is unavailable during branch provisioning", e);
        }
    }

    private void createDefaultTable(Long branchId, String systemToken) {
        Map<String, Object> body = Map.of(
                "branchId", branchId,
                "tableNumber", "1",
                "capacity", 1);
        try {
            restTemplate.exchange(
                    merchantServiceUrl + "/api/tables",
                    HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders(systemToken)),
                    Object.class);
        } catch (RestClientException e) {
            log.warn("Table provisioning failed for branch {}", branchId, e);
            throw new ServiceUnavailableException("merchant-service is unavailable during table provisioning", e);
        }
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
