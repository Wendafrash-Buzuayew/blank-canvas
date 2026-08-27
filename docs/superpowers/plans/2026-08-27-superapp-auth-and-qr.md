# Merchant Auth via Super App + Single Merchant QR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a merchant enter the QRServe Mini App via an M-PESA Super App token, auto-registering on first entry (merchant + default branch + default table-with-QR, all via existing endpoints) and logging straight in on every later entry — with zero new merchant-service code.

**Architecture:** `auth-service` gains a `SuperAppAuthPort` (a small interface with a dev-fake implementation, since no real Super App contract exists yet) that turns a raw token into a `SuperAppMerchantClaim`. A new `SuperAppProvisioningService` looks up an existing `UserEntity` by a new `superAppMerchantRef` column; if none exists, it mints a short-lived internal `SUPER_ADMIN` JWT (using the existing `JwtTokenProvider`, never persisted, never returned to any client) and calls merchant-service's **existing, unmodified** `POST /api/merchants`, `POST /api/branches`, and `POST /api/tables` endpoints over plain HTTP (`RestTemplate`, matching the exact pattern `analytics-service` already uses to call `merchant-service`). `POST /api/tables` already provisions that table's QR in the same call (`TableService.createTable` → `TableQrProvisioningService.provision`), and since no payment settlement exists yet, that QR mints as a `MENU_URL`-profile signed menu link automatically — the existing fallback path, not new code. A new user is created locally in `auth-service` with role `MERCHANT_OWNER`, and a normal JWT pair is issued exactly like today's `/api/auth/login`. The frontend reads a token from the URL, exchanges it automatically on `LoginPage` mount, and falls back to today's email/password form otherwise.

**Tech Stack:** Spring Boot 4.1 (`auth-service`), `RestTemplate` for inter-service calls, JUnit 5 + Mockito for backend unit tests (no `@SpringBootTest` — this repo's convention, and Gradle cannot run in this environment so integration tests aren't verifiable here anyway). React 19 + TypeScript frontend, `tsx`-run `.test.ts` files for pure-function tests (no Jest/Vitest exists in this repo).

**Spec:** `docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md` (§5, §6)

## Global Constraints

- Never delete existing code, controllers, or components. `POST /api/merchants`, `POST /api/branches`, `POST /api/tables` in merchant-service are called as-is — **no merchant-service file changes in this plan at all.**
- No real Super App integration contract exists — design against a port (`SuperAppAuthPort`) with a dev-fake implementation, exactly as `docs/superpowers/specs/2026-08-20-superapp-miniapp-payments-design.md` already does for its own external contracts ("No specs available — design against ports").
- Branch selection is a no-op: every merchant gets exactly one auto-created default branch (slug `main`, name `Main`) — no branch-picker UI, no multi-branch handling.
- The default table is an internal sentinel only: `tableNumber: "1"`, `capacity: 1` — never surfaced to the merchant as a "table" (Plan 3, `MerchantMenuDashboard`, is what will show it as "the QR").
- Backend tests: JUnit 5 + Mockito, matching `TableControllerTest.java`'s style exactly (mock collaborators, instantiate the class under test directly, no Spring context). Verify via the javac+JUnit-launcher harness described in this session's memory (`gradle-cannot-run-here.md`) if a live `./gradlew test` isn't available — `./gradlew build`/`test` remains the user's own outstanding verification step regardless.
- Frontend tests: `node:assert/strict` + the hand-rolled `test(name, fn)` runner in a plain `.test.ts` file run via `tsx`, matching `src/lib/tenant.test.ts` exactly. No new test runner.
- `UserEntity.email` and `passwordHash` are `NOT NULL` — a Super-App-provisioned user gets a synthesized placeholder email (`{merchantExternalRef}@superapp.qrserve.internal`) and a random, never-surfaced password hash. This is documented as a placeholder pending the real Super App contract, not a bug.
- Accepted limitation, documented not fixed: provisioning three remote calls plus one local save is not a distributed transaction. If the local `UserEntity` save fails after remote provisioning succeeds, a retry re-provisions a duplicate merchant. Out of scope for this plan (would need an idempotency key or outbox pattern); call it out with a comment in the code, don't build a fix.

---

### Task 1: `UserEntity` + `UserRepository` — Super App merchant reference

**Files:**
- Modify: `backend/auth-service/src/main/java/com/qrserve/auth/entity/UserEntity.java`
- Modify: `backend/auth-service/src/main/java/com/qrserve/auth/repository/UserRepository.java`

**Interfaces:**
- Produces: `UserEntity.getSuperAppMerchantRef()/.setSuperAppMerchantRef(String)` (via Lombok `@Data`, plus the existing `@Builder`'s `.superAppMerchantRef(String)`); `UserRepository.findBySuperAppMerchantRef(String): Optional<UserEntity>` — both consumed by Task 4.

This is a plain JPA field addition with a Spring Data derived-query method — no custom logic to unit test. Verification is compilation plus Task 4's tests exercising the repository call via a mock.

- [ ] **Step 1: Add the column**

Modify `backend/auth-service/src/main/java/com/qrserve/auth/entity/UserEntity.java`. Add this field after the existing `role` field (before `enabled`):

```java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /**
     * The M-PESA Super App's own merchant/business reference, so a repeat entry
     * from the Super App finds the existing account instead of re-provisioning.
     * Null for every user created through the ordinary email/password path.
     */
    @Column(name = "super_app_merchant_ref", unique = true)
    private String superAppMerchantRef;

    @Column(nullable = false)
    private boolean enabled;
```

- [ ] **Step 2: Add the finder**

Modify `backend/auth-service/src/main/java/com/qrserve/auth/repository/UserRepository.java` — add one line inside the interface:

```java
@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    List<UserEntity> findByMerchantId(UUID merchantId);
    Optional<UserEntity> findBySuperAppMerchantRef(String superAppMerchantRef);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/auth-service/src/main/java/com/qrserve/auth/entity/UserEntity.java backend/auth-service/src/main/java/com/qrserve/auth/repository/UserRepository.java
git commit -m "feat(auth): add superAppMerchantRef to UserEntity"
```

---

### Task 2: `SuperAppAuthPort` — the token-exchange port and dev fake

**Files:**
- Create: `backend/auth-service/src/main/java/com/qrserve/auth/superapp/SuperAppMerchantClaim.java`
- Create: `backend/auth-service/src/main/java/com/qrserve/auth/superapp/SuperAppAuthPort.java`
- Create: `backend/auth-service/src/main/java/com/qrserve/auth/superapp/DevFakeSuperAppAuthPort.java`
- Test: `backend/auth-service/src/test/java/com/qrserve/auth/superapp/DevFakeSuperAppAuthPortTest.java`

**Interfaces:**
- Produces: `SuperAppMerchantClaim` (record: `merchantExternalRef, businessName, phone, city, address, category` — all `String`); `SuperAppAuthPort.exchangeToken(String rawToken): SuperAppMerchantClaim` — consumed by Task 4's `SuperAppProvisioningService`.

No real Super App contract exists. The dev fake treats the raw token as a JSON string carrying the claim fields directly — this is intentionally the whole "adapter," so swapping in the real Safaricom contract later is a new class implementing `SuperAppAuthPort`, not a rewrite of anything that depends on the port.

- [ ] **Step 1: Write the failing test**

Create `backend/auth-service/src/test/java/com/qrserve/auth/superapp/DevFakeSuperAppAuthPortTest.java`:

```java
package com.qrserve.auth.superapp;

import com.qrserve.shared.exceptions.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * No real Super App token contract exists yet - this fake treats the raw
 * token as a JSON object carrying the claim fields directly, so local dev
 * and this test can exercise the whole exchange path without a real
 * container. See docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §5.
 */
class DevFakeSuperAppAuthPortTest {

    private final DevFakeSuperAppAuthPort port = new DevFakeSuperAppAuthPort();

    @Test
    @DisplayName("a well-formed token JSON becomes a claim with every field")
    void parsesAWellFormedToken() {
        String token = "{"
                + "\"merchantExternalRef\":\"MPESA-BIZ-001\","
                + "\"businessName\":\"Sunrise Cafe\","
                + "\"phone\":\"+254700000000\","
                + "\"city\":\"Nairobi\","
                + "\"address\":\"123 Moi Ave\","
                + "\"category\":\"Restaurant\""
                + "}";

        SuperAppMerchantClaim claim = port.exchangeToken(token);

        assertEquals("MPESA-BIZ-001", claim.merchantExternalRef());
        assertEquals("Sunrise Cafe", claim.businessName());
        assertEquals("+254700000000", claim.phone());
        assertEquals("Nairobi", claim.city());
        assertEquals("123 Moi Ave", claim.address());
        assertEquals("Restaurant", claim.category());
    }

    @Test
    @DisplayName("garbage input is rejected, not silently defaulted")
    void rejectsGarbageInput() {
        assertThrows(UnauthorizedException.class, () -> port.exchangeToken("not json at all"));
    }

    @Test
    @DisplayName("a token missing the merchant reference is rejected")
    void rejectsMissingMerchantRef() {
        String token = "{\"businessName\":\"Sunrise Cafe\",\"phone\":\"+254700000000\","
                + "\"city\":\"Nairobi\",\"address\":\"123 Moi Ave\",\"category\":\"Restaurant\"}";
        assertThrows(UnauthorizedException.class, () -> port.exchangeToken(token));
    }

    @Test
    @DisplayName("a null or blank token is rejected")
    void rejectsBlankToken() {
        assertThrows(UnauthorizedException.class, () -> port.exchangeToken(null));
        assertThrows(UnauthorizedException.class, () -> port.exchangeToken("  "));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (via the javac+JUnit harness, or `./gradlew :auth-service:test --tests "*.DevFakeSuperAppAuthPortTest"` if Gradle is available): expect a compile failure — `SuperAppMerchantClaim`/`SuperAppAuthPort`/`DevFakeSuperAppAuthPort` don't exist yet.

- [ ] **Step 3: Write the implementation**

Create `backend/auth-service/src/main/java/com/qrserve/auth/superapp/SuperAppMerchantClaim.java`:

```java
package com.qrserve.auth.superapp;

/**
 * Whatever the M-PESA Super App tells us about the merchant entering the
 * Mini App. Prefill data only - it is never trusted for authorization beyond
 * "this token exchange succeeded," matching the MerchantContextClaim pattern
 * in docs/superpowers/specs/2026-08-20-superapp-miniapp-payments-design.md §7.
 */
public record SuperAppMerchantClaim(
        String merchantExternalRef,
        String businessName,
        String phone,
        String city,
        String address,
        String category) {
}
```

Create `backend/auth-service/src/main/java/com/qrserve/auth/superapp/SuperAppAuthPort.java`:

```java
package com.qrserve.auth.superapp;

/**
 * Turns a raw Super App token into the merchant claim it carries. One
 * interface, adapters behind it - see
 * docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §5.
 * Today's only implementation is {@link DevFakeSuperAppAuthPort}; a real
 * Safaricom adapter drops in later as a second implementation of this same
 * interface.
 */
public interface SuperAppAuthPort {

    /**
     * @throws com.qrserve.shared.exceptions.UnauthorizedException if the token
     *         cannot be parsed or is missing required fields
     */
    SuperAppMerchantClaim exchangeToken(String rawToken);
}
```

Create `backend/auth-service/src/main/java/com/qrserve/auth/superapp/DevFakeSuperAppAuthPort.java`:

```java
package com.qrserve.auth.superapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrserve.shared.exceptions.UnauthorizedException;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the real Super App token exchange, which has no published
 * contract yet. Treats the raw token as a JSON object carrying the claim
 * fields directly - swap this class for a real adapter once the Safaricom
 * contract exists; nothing that depends on {@link SuperAppAuthPort} changes.
 */
@Component
public class DevFakeSuperAppAuthPort implements SuperAppAuthPort {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SuperAppMerchantClaim exchangeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Super App token is required");
        }
        SuperAppMerchantClaim claim;
        try {
            claim = objectMapper.readValue(rawToken, SuperAppMerchantClaim.class);
        } catch (Exception e) {
            throw new UnauthorizedException("Super App token could not be parsed");
        }
        if (claim.merchantExternalRef() == null || claim.merchantExternalRef().isBlank()) {
            throw new UnauthorizedException("Super App token is missing the merchant reference");
        }
        return claim;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run the same test target as Step 2. Expect: PASS, all 4 test cases green.

- [ ] **Step 5: Commit**

```bash
git add backend/auth-service/src/main/java/com/qrserve/auth/superapp/SuperAppMerchantClaim.java backend/auth-service/src/main/java/com/qrserve/auth/superapp/SuperAppAuthPort.java backend/auth-service/src/main/java/com/qrserve/auth/superapp/DevFakeSuperAppAuthPort.java backend/auth-service/src/test/java/com/qrserve/auth/superapp/DevFakeSuperAppAuthPortTest.java
git commit -m "feat(auth): add SuperAppAuthPort with a dev-fake implementation"
```

---

### Task 3: `RestTemplate` bean + merchant-service URL config

**Files:**
- Create: `backend/auth-service/src/main/java/com/qrserve/auth/config/AppConfig.java`
- Modify: `backend/auth-service/src/main/resources/application.yml`

**Interfaces:**
- Produces: a `RestTemplate` bean, and the `services.merchant-service-url` property — both consumed by Task 4's `SuperAppProvisioningService`.

Purely mechanical, no test — mirrors `analytics-service`'s existing identical `AppConfig`.

- [ ] **Step 1: Add the RestTemplate bean**

Create `backend/auth-service/src/main/java/com/qrserve/auth/config/AppConfig.java`:

```java
package com.qrserve.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

- [ ] **Step 2: Add the merchant-service URL config**

Modify `backend/auth-service/src/main/resources/application.yml`. Add this block after the existing `qr:` section (before `management:`):

```yaml
# Plain HTTP, not lb:// - matches analytics-service's existing pattern for
# calling merchant-service directly, so tenant/merchant provisioning does not
# depend on Eureka load-balancer state during a Super App token exchange.
services:
  merchant-service-url: ${MERCHANT_SERVICE_URL:http://localhost:8085}
```

- [ ] **Step 3: Commit**

```bash
git add backend/auth-service/src/main/java/com/qrserve/auth/config/AppConfig.java backend/auth-service/src/main/resources/application.yml
git commit -m "feat(auth): add RestTemplate bean and merchant-service URL config"
```

---

### Task 4: `SuperAppProvisioningService` — the orchestration

**Files:**
- Create: `backend/auth-service/src/main/java/com/qrserve/auth/superapp/MerchantProvisionResponse.java`
- Create: `backend/auth-service/src/main/java/com/qrserve/auth/superapp/BranchProvisionResponse.java`
- Create: `backend/auth-service/src/main/java/com/qrserve/auth/superapp/SuperAppProvisioningService.java`
- Test: `backend/auth-service/src/test/java/com/qrserve/auth/superapp/SuperAppProvisioningServiceTest.java`

**Interfaces:**
- Consumes: `SuperAppAuthPort.exchangeToken` (Task 2), `UserRepository.findBySuperAppMerchantRef` (Task 1), `RestTemplate` bean + `services.merchant-service-url` (Task 3), `JwtTokenProvider.generateAccessToken/generateRefreshToken/getAccessExpirationSeconds` (existing, `shared:security`), `PasswordEncoder` (existing Spring Security bean), `LoginResponse` (existing, `com.qrserve.auth.dto.LoginResponse`).
- Produces: `SuperAppProvisioningService.exchangeAndLogin(String rawToken): LoginResponse` — consumed by Task 5's `AuthController`.

- [ ] **Step 1: Write the failing test**

Create `backend/auth-service/src/test/java/com/qrserve/auth/superapp/SuperAppProvisioningServiceTest.java`:

```java
package com.qrserve.auth.superapp;

import com.qrserve.auth.dto.LoginResponse;
import com.qrserve.auth.entity.UserEntity;
import com.qrserve.auth.repository.UserRepository;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SuperAppProvisioningServiceTest {

    private static final String TOKEN = "raw-token";
    private static final String MERCHANT_REF = "MPESA-BIZ-001";
    private static final UUID MERCHANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long BRANCH_ID = 42L;

    private SuperAppAuthPort authPort;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider tokenProvider;
    private RestTemplate restTemplate;
    private SuperAppProvisioningService service;

    private static SuperAppMerchantClaim claim() {
        return new SuperAppMerchantClaim(MERCHANT_REF, "Sunrise Cafe", "+254700000000", "Nairobi", "123 Moi Ave", "Restaurant");
    }

    @BeforeEach
    void setUp() {
        authPort = mock(SuperAppAuthPort.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenProvider = mock(JwtTokenProvider.class);
        restTemplate = mock(RestTemplate.class);
        service = new SuperAppProvisioningService(authPort, userRepository, passwordEncoder, tokenProvider, restTemplate);
        ReflectionTestUtils.setField(service, "merchantServiceUrl", "http://merchant-service-test");

        when(authPort.exchangeToken(TOKEN)).thenReturn(claim());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(any(UserPrincipal.class))).thenReturn("refresh-token");
        when(tokenProvider.getAccessExpirationSeconds()).thenReturn(3600L);
    }

    @Test
    @DisplayName("an existing Super App user logs straight in - no provisioning calls at all")
    void existingUserLogsInWithoutProvisioning() {
        UserEntity existing = UserEntity.builder()
                .id(UUID.randomUUID())
                .merchantId(MERCHANT_ID)
                .name("Sunrise Cafe")
                .email("mpesa-biz-001@superapp.qrserve.internal")
                .passwordHash("hashed")
                .role(UserRole.MERCHANT_OWNER)
                .enabled(true)
                .superAppMerchantRef(MERCHANT_REF)
                .build();
        when(userRepository.findBySuperAppMerchantRef(MERCHANT_REF)).thenReturn(Optional.of(existing));

        LoginResponse response = service.exchangeAndLogin(TOKEN);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(3600L, response.getExpiresIn());
        verifyNoInteractions(restTemplate);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a new Super App merchant is provisioned: merchant, then branch, then table, then a local user")
    void newMerchantIsProvisionedInOrder() {
        when(userRepository.findBySuperAppMerchantRef(MERCHANT_REF)).thenReturn(Optional.empty());

        MerchantProvisionResponse merchantResponse = new MerchantProvisionResponse(MERCHANT_ID, "sunrise-cafe");
        BranchProvisionResponse branchResponse = new BranchProvisionResponse(BRANCH_ID, "main");

        when(restTemplate.exchange(
                eq("http://merchant-service-test/api/merchants"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(MerchantProvisionResponse.class)))
                .thenReturn(ResponseEntity.ok(merchantResponse));
        when(restTemplate.exchange(
                eq("http://merchant-service-test/api/branches"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(BranchProvisionResponse.class)))
                .thenReturn(ResponseEntity.ok(branchResponse));
        when(restTemplate.exchange(
                eq("http://merchant-service-test/api/tables"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Object.class)))
                .thenReturn(ResponseEntity.ok().build());

        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity toSave = inv.getArgument(0);
            toSave.setId(UUID.randomUUID());
            return toSave;
        });

        LoginResponse response = service.exchangeAndLogin(TOKEN);

        assertEquals("access-token", response.getAccessToken());

        InOrder order = inOrder(restTemplate);
        order.verify(restTemplate).exchange(eq("http://merchant-service-test/api/merchants"), eq(HttpMethod.POST), any(HttpEntity.class), eq(MerchantProvisionResponse.class));
        order.verify(restTemplate).exchange(eq("http://merchant-service-test/api/branches"), eq(HttpMethod.POST), any(HttpEntity.class), eq(BranchProvisionResponse.class));
        order.verify(restTemplate).exchange(eq("http://merchant-service-test/api/tables"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertEquals(MERCHANT_ID, saved.getMerchantId());
        assertEquals(UserRole.MERCHANT_OWNER, saved.getRole());
        assertEquals(MERCHANT_REF, saved.getSuperAppMerchantRef());
        assertEquals("mpesa-biz-001@superapp.qrserve.internal", saved.getEmail());
        assertNotNull(saved.getPasswordHash());
        assertEquals(true, saved.isEnabled());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Expect a compile failure — `MerchantProvisionResponse`, `BranchProvisionResponse`, and `SuperAppProvisioningService` don't exist yet.

- [ ] **Step 3: Write the implementation**

Create `backend/auth-service/src/main/java/com/qrserve/auth/superapp/MerchantProvisionResponse.java`:

```java
package com.qrserve.auth.superapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * The subset of merchant-service's MerchantEntity JSON this service actually
 * reads back. Deliberately not the real MerchantEntity class - auth-service
 * must not compile-depend on merchant-service's domain types.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MerchantProvisionResponse(UUID id, String slug) {
}
```

Create `backend/auth-service/src/main/java/com/qrserve/auth/superapp/BranchProvisionResponse.java`:

```java
package com.qrserve.auth.superapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The subset of merchant-service's BranchEntity JSON this service actually
 * reads back. See MerchantProvisionResponse for why this isn't the real type.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BranchProvisionResponse(Long id, String slug) {
}
```

Create `backend/auth-service/src/main/java/com/qrserve/auth/superapp/SuperAppProvisioningService.java`:

```java
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
import org.springframework.transaction.annotation.Transactional;
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

    @Transactional
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
```

- [ ] **Step 4: Run test to verify it passes**

Expect: PASS, both test cases green — the "existing user" case makes zero `RestTemplate` calls, the "new merchant" case calls merchant/branch/table in that exact order and saves a `UserEntity` with the right fields.

- [ ] **Step 5: Commit**

```bash
git add backend/auth-service/src/main/java/com/qrserve/auth/superapp/MerchantProvisionResponse.java backend/auth-service/src/main/java/com/qrserve/auth/superapp/BranchProvisionResponse.java backend/auth-service/src/main/java/com/qrserve/auth/superapp/SuperAppProvisioningService.java backend/auth-service/src/test/java/com/qrserve/auth/superapp/SuperAppProvisioningServiceTest.java
git commit -m "feat(auth): add SuperAppProvisioningService orchestrating merchant/branch/table provisioning"
```

---

### Task 5: `AuthController` endpoint + `SecurityConfig` wiring

**Files:**
- Create: `backend/auth-service/src/main/java/com/qrserve/auth/dto/SuperAppExchangeRequest.java`
- Modify: `backend/auth-service/src/main/java/com/qrserve/auth/controller/AuthController.java`
- Modify: `backend/shared/security/src/main/java/com/qrserve/shared/security/SecurityConfig.java`

**Interfaces:**
- Consumes: `SuperAppProvisioningService.exchangeAndLogin` (Task 4).
- Produces: `POST /api/auth/superapp/exchange` — consumed by the frontend (Task 6/7).

- [ ] **Step 1: Add the request DTO**

Create `backend/auth-service/src/main/java/com/qrserve/auth/dto/SuperAppExchangeRequest.java`:

```java
package com.qrserve.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SuperAppExchangeRequest {
    @NotBlank(message = "token is required")
    private String token;
}
```

- [ ] **Step 2: Add the endpoint**

Modify `backend/auth-service/src/main/java/com/qrserve/auth/controller/AuthController.java`. Add the import:

```java
import com.qrserve.auth.dto.SuperAppExchangeRequest;
import com.qrserve.auth.superapp.SuperAppProvisioningService;
```

Add the field (`RequiredArgsConstructor` picks it up automatically):

```java
    private final AuthService authService;
    private final SuperAppProvisioningService superAppProvisioningService;
```

Add the endpoint, directly after the existing `/login` method:

```java
    @PostMapping("/superapp/exchange")
    @Operation(summary = "Exchange an M-PESA Super App token for a QRServe session, auto-registering the merchant on first entry")
    public ResponseEntity<LoginResponse> exchangeSuperAppToken(@Valid @RequestBody SuperAppExchangeRequest request) {
        return ResponseEntity.ok(superAppProvisioningService.exchangeAndLogin(request.getToken()));
    }
```

- [ ] **Step 3: Permit the new path**

Modify `backend/shared/security/src/main/java/com/qrserve/shared/security/SecurityConfig.java`. Find this block (item 4, "Public authentication endpoints, enumerated"):

```java
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh", "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/logout", "/api/v1/auth/logout").permitAll()
```

Add one line after it:

```java
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh", "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/logout", "/api/v1/auth/logout").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/superapp/exchange").permitAll()
```

This file is shared by every microservice - this is the only line to touch here. Do not restructure anything else in this file.

- [ ] **Step 4: Verify with the javac harness**

Since `./gradlew` cannot run in this environment (per `gradle-cannot-run-here.md`), compile `auth-service` + its dependencies (`shared:common`, `shared:exceptions`, `shared:security`) with `javac` against a classpath built from `~/.gradle/caches/modules-2/files-2.1` (newest version per artifact, `-parameters` flag, `spring-cloud-gateway` excluded) — the same approach already used earlier in this project's history. Confirm no compile errors across all 4 modules' `src/main/java`, then compile+run `auth-service`'s `src/test/java` (all of it, including this task's new test and Task 2/4's) against that classpath plus `classes-main`, using a small `MiniRunner`/`MiniRunnerClasses` harness over `junit-platform-launcher` (same pattern as before). Confirm all tests pass. Report `./gradlew build` (or at minimum `./gradlew :auth-service:test`) as the user's own outstanding verification step regardless — the harness cannot catch dependency-resolution issues Gradle would.

- [ ] **Step 5: Commit**

```bash
git add backend/auth-service/src/main/java/com/qrserve/auth/dto/SuperAppExchangeRequest.java backend/auth-service/src/main/java/com/qrserve/auth/controller/AuthController.java backend/shared/security/src/main/java/com/qrserve/shared/security/SecurityConfig.java
git commit -m "feat(auth): expose POST /api/auth/superapp/exchange"
```

---

### Task 6: Frontend — `superApp.ts` token reader

**Files:**
- Create: `src/lib/superApp.ts`
- Create: `src/lib/superApp.test.ts`
- Modify: `package.json:13`

**Interfaces:**
- Produces: `getSuperAppToken(search?: string): string | null` — consumed by Task 7's `LoginPage.tsx`.

- [ ] **Step 1: Write the failing test**

Create `src/lib/superApp.test.ts`:

```ts
/**
 * Pure-function tests for reading the M-PESA Super App handoff token from
 * the URL. Run with `npm run test:unit`.
 *
 * No real Super App integration contract exists yet - this is a placeholder
 * handoff mechanism (a query param), matching the backend's dev-fake port.
 * See docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §5.
 */
import assert from 'node:assert/strict';
import { getSuperAppToken } from './superApp';

let failures = 0;
function test(name: string, fn: () => void) {
  try {
    fn();
    console.log(`  ok  ${name}`);
  } catch (error) {
    failures += 1;
    console.error(`FAIL  ${name}`);
    console.error(`      ${(error as Error).message}`);
  }
}

test('extracts the token from the query string', () => {
  assert.equal(getSuperAppToken('?superapp_token=abc123'), 'abc123');
});

test('returns null when the param is absent', () => {
  assert.equal(getSuperAppToken(''), null);
  assert.equal(getSuperAppToken('?other=1'), null);
});

test('returns null for a blank or whitespace-only token value', () => {
  assert.equal(getSuperAppToken('?superapp_token='), null);
  assert.equal(getSuperAppToken('?superapp_token=%20'), null);
});

test('works alongside other query params', () => {
  assert.equal(getSuperAppToken('?utm_source=x&superapp_token=abc123&ref=y'), 'abc123');
});

if (failures > 0) {
  console.error(`\n${failures} test(s) failed`);
  process.exit(1);
}
console.log('\nall superApp tests passed');
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx tsx src/lib/superApp.test.ts`
Expected: FAIL — `Cannot find module './superApp'`.

- [ ] **Step 3: Write the implementation**

Create `src/lib/superApp.ts`:

```ts
/**
 * Reads the M-PESA Super App handoff token from the URL.
 *
 * No real Super App integration contract exists yet - this query param is a
 * placeholder for whatever the real container hands over on launch, matching
 * the backend's dev-fake SuperAppAuthPort. See
 * docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §5.
 */
const SUPER_APP_TOKEN_PARAM = 'superapp_token';

/**
 * @param search defaults to the real page's query string; overridable so this
 *   is testable under plain node (this module has no other browser dependency).
 */
export function getSuperAppToken(
  search: string = typeof window !== 'undefined' ? window.location.search : '',
): string | null {
  const params = new URLSearchParams(search);
  const token = params.get(SUPER_APP_TOKEN_PARAM);
  return token && token.trim() ? token : null;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx tsx src/lib/superApp.test.ts`
Expected: PASS — `all superApp tests passed`, exit code 0.

- [ ] **Step 5: Wire into the test:unit script**

Modify `package.json:13`. Find the current `test:unit` script (it ends with `tsx src/lib/navigation.test.ts` after Plan 1 merged) and append this task's test:

```json
    "test:unit": "tsx src/lib/tenant.test.ts && tsx src/lib/orderSession.test.ts && tsx src/lib/qrDisplay.test.ts && tsx src/lib/phase.test.ts && tsx src/lib/navigation.test.ts && tsx src/lib/superApp.test.ts"
```

- [ ] **Step 6: Run the full unit suite**

Run: `npm run test:unit`
Expected: every test file passes, exit code 0.

- [ ] **Step 7: Commit**

```bash
git add src/lib/superApp.ts src/lib/superApp.test.ts package.json
git commit -m "feat(frontend): add getSuperAppToken URL reader"
```

---

### Task 7: Frontend — `api.ts`, `AuthContext.tsx`, `LoginPage.tsx` wiring

**Files:**
- Modify: `src/lib/api.ts`
- Modify: `src/context/AuthContext.tsx`
- Modify: `src/pages/LoginPage.tsx`

**Interfaces:**
- Consumes: `getSuperAppToken` (Task 6); backend `POST /api/auth/superapp/exchange` (Task 5, same `LoginResponse` shape as `/auth/login`).
- Produces: `authApi.exchangeSuperAppToken(token: string): Promise<LoginResponse>`; `AuthContext`'s `loginWithSuperAppToken(token: string): Promise<AuthUser>` — both consumed only within this task's own `LoginPage.tsx` change.

No new pure functions here (JSX/context wiring); this repo has no component-testing harness, so verification is `npm run lint` plus the manual steps in Step 5.

- [ ] **Step 1: Add `authApi.exchangeSuperAppToken`**

Modify `src/lib/api.ts`. Find the existing `authApi.login` entry:

```ts
export const authApi = {
  login: (email: string, password: string) =>
    request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
      skipAuth: true,
    }),
```

Add a sibling method directly after it:

```ts
export const authApi = {
  login: (email: string, password: string) =>
    request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
      skipAuth: true,
    }),

  /** POST /api/auth/superapp/exchange - M-PESA Super App token exchange. */
  exchangeSuperAppToken: (token: string) =>
    request<LoginResponse>('/auth/superapp/exchange', {
      method: 'POST',
      body: JSON.stringify({ token }),
      skipAuth: true,
    }),
```

- [ ] **Step 2: Add `loginWithSuperAppToken` to `AuthContext.tsx`**

Modify `src/context/AuthContext.tsx`. Add to the `AuthContextType` interface, alongside the existing `login`:

```ts
interface AuthContextType {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<AuthUser>;
  loginWithSuperAppToken: (token: string) => Promise<AuthUser>;
  logout: () => Promise<void>;
  setUserProfile: (user: AuthUser) => void;
  refreshUser: () => Promise<void>;
}
```

Add the implementation directly after the existing `login` callback (same file, inside `AuthProvider`):

```ts
  const loginWithSuperAppToken = useCallback(async (token: string): Promise<AuthUser> => {
    setIsLoading(true);
    try {
      const response: LoginResponse = await authApi.exchangeSuperAppToken(token);
      setTokens(response.accessToken, response.refreshToken);

      let authUser: AuthUser;
      try {
        const info = await authApi.getMe();
        authUser = mapUserInfoToAuthUser(info);
      } catch (err) {
        // Fallback: decode JWT payload if /me fails - mirrors login()'s own fallback.
        const payload = JSON.parse(atob(response.accessToken.split('.')[1]));
        authUser = {
          id: payload.sub || payload.userId || '',
          email: payload.email || '',
          name: payload.name || payload.sub || 'Merchant',
          role: payload.role || 'MERCHANT_OWNER',
          merchantId: payload.merchantId,
        };
      }

      setUser(authUser);
      setUserState(authUser);
      setAuthState(true);
      window.dispatchEvent(new CustomEvent('qrserve_auth_update'));
      return authUser;
    } finally {
      setIsLoading(false);
    }
  }, []);
```

Add it to the context value, alongside `login`:

```ts
  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: authState,
        isLoading,
        login,
        loginWithSuperAppToken,
        logout,
        setUserProfile,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
```

- [ ] **Step 3: Wire `LoginPage.tsx`**

Modify `src/pages/LoginPage.tsx`. Change the import block, from:

```tsx
import React, { useEffect, useState } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import { QrCode, Loader2, AlertCircle, Lock, Mail, Eye, EyeOff } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../lib/api';
import { getRoleHome } from '../router/ProtectedRoute';
import { isPhase2Enabled, isRoleAllowedInPhase } from '../lib/phase';
```

to:

```tsx
import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import { QrCode, Loader2, AlertCircle, Lock, Mail, Eye, EyeOff } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../lib/api';
import { getRoleHome } from '../router/ProtectedRoute';
import { isPhase2Enabled, isRoleAllowedInPhase } from '../lib/phase';
import { getSuperAppToken } from '../lib/superApp';
```

Change the component's opening (destructuring `useAuth()` and adding the Super App exchange effect), from:

```tsx
export const LoginPage: React.FC = () => {
  const { login, isLoading, isAuthenticated, user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState(isPhase2Enabled() ? 'admin@hotel.com' : '');
  const [password, setPassword] = useState(isPhase2Enabled() ? 'password' : '');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [wasPhaseBlocked, setWasPhaseBlocked] = useState(false);
```

to:

```tsx
export const LoginPage: React.FC = () => {
  const { login, loginWithSuperAppToken, isLoading, isAuthenticated, user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState(isPhase2Enabled() ? 'admin@hotel.com' : '');
  const [password, setPassword] = useState(isPhase2Enabled() ? 'password' : '');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [wasPhaseBlocked, setWasPhaseBlocked] = useState(false);

  // Read once per mount: a Super App launch hands this off via the URL, and
  // it must not be re-read after the exchange consumes it (e.g. on an
  // in-page state change), or a failed exchange would retry forever.
  const superAppToken = useMemo(() => getSuperAppToken(), []);
  const [superAppExchanging, setSuperAppExchanging] = useState(Boolean(superAppToken));
  const [superAppError, setSuperAppError] = useState<string | null>(null);

  useEffect(() => {
    if (!superAppToken) return;
    loginWithSuperAppToken(superAppToken)
      .catch((err) => {
        setSuperAppError(
          err instanceof ApiError ? err.message : 'Could not sign in from the Super App. Please try again.',
        );
      })
      .finally(() => setSuperAppExchanging(false));
    // Runs once per mount against the token captured above - loginWithSuperAppToken
    // is stable (useCallback with an empty dependency array in AuthContext).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [superAppToken]);
```

Add a Super-App-loading render branch. Insert it directly after the existing `phaseBlocked` block's closing `}` and before the `isAuthenticated && user` redirect:

```tsx
  if (superAppExchanging) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
        <div className="flex flex-col items-center gap-3 text-slate-500">
          <Loader2 className="w-6 h-6 animate-spin" />
          <p className="text-sm font-bold">Signing you in…</p>
        </div>
      </div>
    );
  }

  // If already authenticated with an allowed role, redirect to role home
  if (isAuthenticated && user) {
    return <Navigate to={getRoleHome(user.role)} replace />;
  }
```

Show the Super App error alongside the manual-login error, so a failed Super App exchange still leaves the ordinary form usable as a fallback. Change the error banner, from:

```tsx
            {error && (
              <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700 flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-red-500 shrink-0" />
                {error}
              </div>
            )}
```

to:

```tsx
            {(error || superAppError) && (
              <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700 flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-red-500 shrink-0" />
                {error || superAppError}
              </div>
            )}
```

Everything else in the file (the form, `handleSubmit`, `demoAccounts`, the rest of the JSX) is unchanged.

- [ ] **Step 4: Type-check**

Run: `npm run lint`
Expected: no errors.

- [ ] **Step 5: Manual verification**

This sandbox cannot run the Gradle-based backend, so a real end-to-end exchange isn't possible here — perform these checks once a real backend is available:
1. Visit `/login` with no `?superapp_token=` param. Expect: the ordinary email/password form, unchanged from before this task.
2. Visit `/login?superapp_token=<valid dev-fake JSON>` (URL-encoded, e.g. `?superapp_token=%7B%22merchantExternalRef%22%3A%22MPESA-BIZ-001%22%2C...%7D`) against a running backend with a fresh `merchantExternalRef`. Expect: a brief "Signing you in…" screen, then landing on `/merchant/dashboard` as a newly-provisioned `MERCHANT_OWNER`.
3. Repeat with the same `merchantExternalRef`. Expect: no new merchant/branch/table created (check merchant-service logs or the database), straight login.
4. Visit `/login?superapp_token=garbage`. Expect: the "Signing you in…" screen briefly, then the ordinary form reappears with a red error banner, and the form is still usable for a manual login.

- [ ] **Step 6: Commit**

```bash
git add src/lib/api.ts src/context/AuthContext.tsx src/pages/LoginPage.tsx
git commit -m "feat(frontend): wire Super App token exchange into LoginPage"
```

---

## Plan complete

At this point: `npm run test:unit` passes (6 test files, including this plan's new `superApp.test.ts`), `npm run lint` passes, and the backend compiles clean under the javac harness with all new unit tests passing (`DevFakeSuperAppAuthPortTest`, `SuperAppProvisioningServiceTest`). A merchant entering via a Super App token is auto-registered with one default branch and one default table-with-QR on first entry, and logs straight in on every later entry — using zero new merchant-service code.

**Follow-on plans** (per the spec's decomposition, not part of this plan):
- `MerchantMenuDashboard` (shows the provisioned table's QR as "the merchant's QR," no table framing)
- API Gateway `phase1` profile + deployment scaling — **carries the blocker recorded during Plan 1's final review:** `/merchant/dashboard`'s content (`DashboardPage.tsx`) still renders full Phase 2 data and must be phase-gated before that plan removes `order-service`/`analytics-service` from the gateway.
