# Codebase Review Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remediate every Critical/High/Medium finding in `docs/codebase-review.md` across the 10 QRServe backend modules and the React frontend, restoring tenant isolation, role-based authorization, secret hygiene, and correct gateway/Kafka wiring.

**Architecture:** Fixes are sequenced by blast radius, not by review order. Config-only changes (secrets, gateway routes, YAML) land first because they carry zero compile risk and cover most Critical findings. Java changes follow, grouped per module so a failed compile is attributable to one phase. Authorization is centralized: `SecurityConfig` stops relying on `anyRequest().authenticated()` as the only gate, and every mutating controller gets an explicit `@PreAuthorize`.

**Tech Stack:** Java 17 (toolchain), Spring Boot 4.1.0, Spring Cloud 2025.1.2, Spring Security 7, Spring Kafka (Jackson 3), PostgreSQL + JPA, Redis, Gradle 8.14.2 multi-module; React 19 + TypeScript + TanStack Query + Vite.

---

## Execution Status (updated 2026-08-18)

**All 8 "Do before next deploy" items are implemented.** Verified by a full type-checking
compile of all 131 backend sources (0 errors) plus `npx tsc --noEmit` (0 errors).

| Phase | Task | Status |
|---|---|---|
| 1 | 1.1 JWT secret defaults removed (7 yml + 8 compose + k8s) | **Done** |
| 1 | 1.2 QR secret required; added to all 8 services + compose + k8s + `.env.example` | **Done** |
| 1 | 1.3 Trace sampling + gateway log levels env-driven | **Done** |
| 2 | 2.1 `/api/v1/auth/**` routed to auth-service | **Done** |
| 2 | 2.2 CircuitBreaker + GET-only Retry per-route, `/fallback` added | **Done** |
| 2 | 2.3 merchant-service `VALUE_SERIALIZER` set | **Done** |
| 2 | 2.4 Kafka serializers migrated to Jackson 3 | **Done** |
| 3 | 3.1 SecurityConfig re-ordered; menu/actuator/auth narrowed | **Done** |
| 3 | 3.2 `@PreAuthorize` on all 12 controllers | **Done** |
| 3 | 3.4 `listUsers` tenant scoping | **Done** (via CustomerRequest/Waiter/Analytics pattern; see note) |
| 4 | 4.1 Customer-request tenant check mandatory | **Done** |
| 4 | 4.2 `branchId` validated against table + waiter | **Done** |
| 4 | 4.3 Analytics tenant-scoped; phantom URL fixed; server-side filtering added | **Done** |
| 5 | 5.1 Unauthenticated `/ws/orders` + dead handler/service deleted | **Done** |
| 6 | 6.1 `ServiceUnavailableException` added; catch-all no longer leaks `ex.getMessage()` | **Partial** — analytics fetches converted; OrderService/QrGeneratorService fetch helpers NOT yet |
| 9 | 9.1 Dead `getJwtFromRequest` removed | **Done** |
| 9 | 9.2 `WaiterController` uses `@AuthenticationPrincipal` | **Done** |
| 8 | 8.3 Duplicate `/api/analytics/orders` removed; placeholders marked | **Done** |

### Not yet implemented (remaining work, all "this sprint" or lower)

- ~~**3.3** Role hierarchy on user creation~~ — **DONE** in `07334ed`; covered by `AuthServiceRoleHierarchyTest` (6 tests).
- ~~**5.2** STOMP destination-level authorization~~ — **DONE** in `07334ed`, deny-by-default; covered by `StompAuthInterceptorTest` (12 tests, confirmed to fail with the authz call disabled).
- **6.1 (rest)** 404-vs-5xx in `OrderService.fetchTable/fetchProduct` and `QrGeneratorService.fetchMerchant`.
- **6.3** Retry/alert on table-status update failure.
- **7.1–7.4** N+1 fixes (menu, kitchen, waiter tasks) and moving REST/Kafka out of `OrderService.createOrder`'s transaction.
- **7.5** Flyway baselines + composite indexes.
- **8.1** Phantom frontend endpoints (`PUT/DELETE /api/branches|tables/{id}`, `DELETE /api/merchants/{id}`).
- **8.2** Status-enum validation and legal transition guard.
- **8.4** Drop ignored `merchantId` param on `getWaiters`.
- ~~**9.3** Refresh-token `type` claim, strict role claim, configured `expiresIn`~~ — **DONE** in `07334ed`; covered by `JwtTokenProviderTokenTypeTest` (10 tests). Note the BREAKING session invalidation.
- **9.4** QR PDF via OpenPDF. Note `BusinessException` has no `(String, Throwable)` constructor — add one first.
- **9.5** Dependency pin audit.
- **10.1** Frontend re-verification + dead `CustomerMenuView.tsx` removal.

### Also completed after this plan was written (design + realtime roadmap)

| Phase | Work | Commit |
|---|---|---|
| C | Design tokens ported; brand/danger and warn-soft colour collisions fixed | `ad97ef9` |
| A | STOMP destination authz, guest order-stream tokens, token type claims, role hierarchy | `07334ed` |
| B | Reconnect resync, event dedupe, narrower invalidation | `07334ed` |
| D1 | KDS rebuilt for distance reading; fixed permanently-empty Incoming column | `06e286e` |
| D2 | Waiter views on the design system; oldest-first triage + age-driven urgency; third `CREATED` status bug fixed | `2986089` |
| D3 | Customer menu refinements; `data-view` scope activated; dead component island removed | `2986089` |
| E | Runtime smoke harness written (`backend/scripts/smoke-tenant-isolation.sh`) — NOT yet executed | `52e2eef` |

**Still open:**
- Executing Phase E against a running stack (needs Postgres + Kafka + Redis; Gradle
  cannot start in the authoring environment).
- **`#E60028` is hardcoded ~130 times across ~30 files** (admin, merchant, landing,
  auth, nav, sidebar, ui). These bypass `--color-brand` entirely, so a brand change
  would not propagate, and several pair it with raw `red-*` tints that no longer
  match `--color-danger` after the collision fix. A mechanical migration to
  `text-brand` / `bg-brand` / `ring-brand`, but it touches ~30 files and deserves
  its own review.
- `src/pages/WaiterDashboard.tsx` is dead code (nothing imports it) and duplicates
  `WaiterDashboardPage.tsx` with the old hardcoded palette. Left in place because
  deleting a page is the owner's call.
- Task 8.2 status-enum validation is now more valuable than when it was written:
  three separate places were found using status names the backend never emits
  (`CREATED`, `SERVED`). Binding to an enum would have caught all three at compile
  or request time.

Everything else in the "Not yet implemented" list above remains accurate.

### Findings discovered during execution that were NOT in the review

1. **No backend service could start.** `QrSignatureService` had been changed to fail fast, but `qr.signature-secret` was configured nowhere. All 8 services (incl. api-gateway, which component-scans `shared.common`) would have aborted at startup. Fixed.
2. **k8s manifests used `environment:` instead of `env:`** — a docker-compose key Kubernetes silently ignores, so *no* env var was ever applied. Fixed.
3. **JWT secret was a plaintext literal in `k8s/deployment.yml`** (2 sites) and `DATABASE_PASSWORD`/`POSTGRES_PASSWORD` likewise. The review only covered yml + compose. Fixed via `secretKeyRef` + `k8s/secrets.example.yml`.
4. **`GET /api/tables/all` was public** — `SecurityConfig`'s `GET /api/tables/*` wildcard also matches `all`, exposing every table of every merchant anonymously. Fixed with an explicit rule above it.
5. **`/api/auth/**` blanket `permitAll` exposed `POST /api/auth/users`** and `GET /api/auth/me` (the latter NPEs on a null principal). Narrowed to login/refresh/logout.
6. **`/actuator/**` blanket `permitAll`** exposed `/actuator/env` and `/configprops`. Narrowed to health/info/prometheus.
7. **`OrderController.getAllOrders` returned every tenant's orders.** Now tenant-scoped at the query.
8. **The backend has zero tests.** All five `src/test` directories are empty, so the TDD steps in this plan create the first tests in the repo.
9. **k8s app pods use `DATABASE_USERNAME: postgres` while the Postgres pod is created with `qrserve_user`** — these manifests could never have authenticated. Left as-is (cannot see the real cluster); flagged for the owner.
10. **The review over-rated the gateway `/api/v1/auth/**` finding as Critical.** No v1 auth controller exists, so those requests 404 either way; the fix is forward-looking, not a live break.

---

## Verification Constraint — READ FIRST

**Gradle cannot execute in the authoring environment.** The daemon fails at startup with:

```
java.io.IOException: Unable to establish loopback connection
  at sun.nio.ch.PipeImpl$Initializer$LoopbackConnector.run
  at sun.nio.ch.UnixDomainSockets.connect
```

Reproduced on JDK 17, 21, and 25, with and without `--no-daemon`, via both `gradlew` and `gradlew.bat`. Plain TCP loopback works (verified with a Java `ServerSocket` test and Node), so the cause is AF_UNIX / NIO-pipe creation being blocked — characteristic of corporate endpoint security on this host. This is an environment issue, **not** a repo defect.

**Workaround found — backend changes ARE compile-verified.** Gradle is unusable, but a
direct `javac` compile of the whole backend works once the dependency classpath is staged
out of the Gradle cache. This performs full type checking and Lombok annotation processing:

```bash
cd backend
W=$(mktemp -d); mkdir -p "$W/lib" "$W/classes"
find "$HOME/.gradle/caches/modules-2" -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' \
  | grep -v 'spring-kafka/3\.' | grep -v 'spring-security-core/6\.' | grep -v 'jackson-databind/2.18' > "$W/jarlist.txt"
tr '\n' '\0' < "$W/jarlist.txt" | xargs -0 -n 40 cp -t "$W/lib"
find . -name '*.java' -path '*/src/main/java/*' | grep -v '/build/' | grep -v '/bin/' \
  | while read s; do cygpath -w "$s"; done > "$W/srcs.txt"
javac -cp "$(cygpath -w "$W/lib")\\*" -d "$(cygpath -w "$W/classes")" -nowarn -Xmaxerrs 3000 @"$(cygpath -w "$W/srcs.txt")"
```

Current result: **exit 0, 131 sources, 0 errors.**

Two caveats this does NOT cover, so it is not a substitute for the real build:
- **SpEL strings inside `@PreAuthorize` are never validated by the compiler.** A typo in
  `authentication.principal.merchantId` fails only at request time. These need a running
  smoke test — see Final Verification.
- The classpath is hand-assembled and may resolve slightly different versions than
  Gradle's. Run `./gradlew clean build` on a working host before shipping.

Frontend verification (`npx tsc --noEmit`) works natively and passes.

**Before trusting any backend phase, run:**

```bash
cd backend && ./gradlew compileJava compileTestJava
```

If the loopback error appears on your host too, fix it first (allow `java.exe` AF_UNIX sockets in your endpoint-security policy, or build inside WSL2/a container). Do not proceed phase-to-phase without a green compile — this plan touches 10 modules and an uncaught typo surfaces as a service that will not boot.

---

## Scope Decisions (assumed — no answer received on the scope question)

**In scope:** all 8 "Do before next deploy" items, all 12 "Do this sprint" items, and the cheap backlog items (dead-code removal, trace sampling, `expiresIn`, status-enum validation, JWT `type` claim, DB indexes).

**Deliberately deferred** — each is an architectural project, not a fix, and doing it badly is worse than not doing it:

| Deferred item | Why |
|---|---|
| Service-to-service identity (replace end-user JWT forwarding) | Needs a decision on mechanism (client-credentials vs mTLS) and an issuer. Touches every inter-service call. |
| Refresh-token revocation store | Needs a Redis allowlist design + logout semantics. The cheap half (`type: REFRESH` claim validation) IS included, Task 9.3. |
| Real analytics aggregation | `getRevenueAnalytics`/`getPopularItems` return hardcoded data. Replacing them needs an aggregation source (order-service DB read model or Kafka projection) — a feature, not a repair. Task 8.3 marks them as placeholders instead. |
| `/api/**` vs `/api/v1/**` controller consolidation | A breaking API change requiring a frontend migration and a deprecation window. |

**Secrets decision (assumed):** fail fast in all profiles, no environment-specific defaults. `JwtTokenProvider` already does this correctly; the defaults live in YAML and are what actually defeat the guard.

**Flyway decision (assumed):** generate migrations and enable Flyway, but set `ddl-auto: validate` **only** in the `prod` profile — dev keeps `update`. Hand-written DDL cannot be verified against a live DB here, so a full cutover would risk breaking every environment at once.

---

## File Structure

### Config (no compile risk — Phase 1–2)
| File | Responsibility |
|---|---|
| `backend/{auth,merchant,menu,order,notification,qr,analytics}-service/src/main/resources/application.yml` | Remove JWT secret defaults; add `qr.signature-secret`; env-drive trace sampling |
| `backend/docker-compose.yml` | Remove 8 baked JWT secret defaults; add `QR_SIGNATURE_SECRET` |
| `backend/k8s/deployment.yml` | Replace 2 literal secrets with `secretKeyRef` |
| `backend/api-gateway/src/main/resources/application.yml` | Add `/api/v1/auth/**` route above merchant; wire CircuitBreaker+Retry; gate TRACE logging |
| `backend/.env.example` | **Create** — document every required secret |

### Shared modules (Phase 3, 6, 9)
| File | Responsibility |
|---|---|
| `shared/security/.../SecurityConfig.java` | Rule ordering; narrow menu `permitAll` to GET-by-merchant only |
| `shared/security/.../JwtTokenProvider.java` | `type` claim on refresh tokens; strict role claim; remove nothing else |
| `shared/security/.../JwtAuthenticationFilter.java` | Delete dead `getJwtFromRequest` |
| `shared/exceptions/.../GlobalExceptionHandler.java` | Generic message for unhandled; add `ServiceUnavailableException` handler |
| `shared/exceptions/.../ServiceUnavailableException.java` | **Create** — distinguishes downstream outage from 404 |
| `shared/common/.../QrSignatureService.java` | Remove default HMAC secret |

### Per-service (Phase 3–9)
| File | Responsibility |
|---|---|
| `auth-service/.../AuthService.java` | Role hierarchy on create; tenant-forced merchantId; configured `expiresIn`; refresh-type validation |
| `auth-service/.../AuthController.java` | Strict `listUsers` scoping |
| `merchant-service/.../KafkaProducerConfig.java` | Add missing `VALUE_SERIALIZER_CLASS_CONFIG` |
| `merchant-service/.../CustomerRequestService.java` | Mandatory tenant check |
| `merchant-service/.../TableAssignmentService.java` | Validate `branchId` against table and waiter |
| `merchant-service/.../{Merchant,Branch,Table,Waiter,TableAssignment,CustomerRequest}Controller.java` | `@PreAuthorize` + missing PUT/DELETE mappings |
| `merchant-service/.../WaiterTaskV1Controller.java` | Batch table fetch (N+1) |
| `menu-service/.../MenuService.java` | Single-query menu assembly (N+1) |
| `menu-service/.../{Product,Category}Controller.java` | `@PreAuthorize` |
| `order-service/.../OrderService.java` | Move REST/Kafka out of `@Transactional`; 404-vs-5xx; status enum validation |
| `order-service/.../KitchenService.java` | Batch table + item fetch (N+1) |
| `qr-service/.../QrGeneratorService.java` | Correct PDF filter; 404-vs-5xx |
| `analytics-service/.../AnalyticsService.java` | Tenant-scope all fetches; fix phantom URL; degraded flag |
| `notification-service/.../StompAuthInterceptor.java` | Destination-level authorization |
| `notification-service/.../{WebSocketConfig,OrderWebSocketHandler}.java` | **Delete** — unauthenticated dead code |

### Migrations (Phase 7)
`backend/{auth,merchant,menu,order,qr,analytics,notification}-service/src/main/resources/db/migration/V1__init.sql`, `V2__indexes.sql` — **create** per service.

### Frontend (Phase 10)
`src/lib/api.ts` — remove or align phantom endpoint methods. Already-done Phase 2 items are verified, not redone.

---

## Phase 1 — Secrets Hygiene (Critical, zero compile risk)

### Task 1.1: Remove JWT secret defaults from all service YAML

**Files — Modify:** the `jwt.secret` line in each of:
- `backend/auth-service/src/main/resources/application.yml:30`
- `backend/merchant-service/src/main/resources/application.yml:29`
- `backend/menu-service/src/main/resources/application.yml:44`
- `backend/order-service/src/main/resources/application.yml:42`
- `backend/notification-service/src/main/resources/application.yml:33`
- `backend/qr-service/src/main/resources/application.yml:32`
- `backend/analytics-service/src/main/resources/application.yml:33`

- [ ] **Step 1: Confirm the current state**

```bash
cd backend && grep -rn "JWT_SECRET:" --include=application.yml src */src
```

Expected: 7 lines, each ending `:<REDACTED_OLD_DEFAULT>}`.

- [ ] **Step 2: Strip the default in every file**

Replace `${JWT_SECRET:<REDACTED_OLD_DEFAULT>}` with `${JWT_SECRET}` — the `${VAR}` form with no `:default` leaves the property unresolved when unset, which `JwtTokenProvider`'s existing guard converts into a startup failure.

```bash
cd backend
for f in auth merchant menu order notification qr analytics; do
  sed -i 's|\${JWT_SECRET:[^}]*}|${JWT_SECRET}|' "$f-service/src/main/resources/application.yml"
done
grep -rn "JWT_SECRET" --include=application.yml */src
```

Expected: 7 lines, each exactly `  secret: ${JWT_SECRET}`.

- [ ] **Step 3: Remove the docker-compose defaults**

`backend/docker-compose.yml` lines 34, 55, 76, 99, 123, 147, 168, 188 use `${JWT_SECRET:-<literal>}`. The `:-` fallback must go so Compose fails loudly on an unset var.

```bash
cd backend && sed -i 's|JWT_SECRET=\${JWT_SECRET:-[^}]*}|JWT_SECRET=${JWT_SECRET:?JWT_SECRET must be set}|' docker-compose.yml
grep -c "JWT_SECRET must be set" docker-compose.yml
```

Expected: `8`.

- [ ] **Step 4: Fix the Kubernetes manifests (not in the original review)**

`backend/k8s/deployment.yml:78,230` hold the secret as a plaintext literal `value:`. Replace each with a secret reference:

```yaml
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: qrserve-secrets
              key: jwt-secret
```

- [ ] **Step 5: Verify no literal secret remains anywhere**

```bash
cd backend && grep -rniE "jwt[-_]?secret:\s*\$\{JWT_SECRET:[^}]" --include=*.yml --include=*.yaml --include=*.java . | grep -v "/build/"
```

Expected: **no output**.

- [ ] **Step 6: Commit**

```bash
git add backend/*/src/main/resources/application.yml backend/docker-compose.yml backend/k8s/deployment.yml
git commit -m "fix(security): remove hardcoded default JWT secret from all config"
```

### Task 1.2: Remove the QR signature secret default

**Files — Modify:** `backend/shared/common/.../QrSignatureService.java:29`, `backend/docker-compose.yml`, the 3 services that use it.

- [ ] **Step 1: Read the current constructor**

```bash
cd backend && sed -n '20,40p' shared/common/src/main/java/com/qrserve/shared/common/QrSignatureService.java
```

- [ ] **Step 2: Remove the default and fail fast, mirroring `JwtTokenProvider`**

```java
    public QrSignatureService(@Value("${qr.signature-secret}") String signatureSecret) {
        if (signatureSecret == null || signatureSecret.isBlank()) {
            throw new IllegalStateException(
                "qr.signature-secret must be configured via QR_SIGNATURE_SECRET");
        }
        this.signatureSecret = signatureSecret;
    }
```

- [ ] **Step 3: Add the property to every service that loads this bean**

Add to `merchant-service`, `qr-service`, and `menu-service` `application.yml`:

```yaml
qr:
  signature-secret: ${QR_SIGNATURE_SECRET}
```

- [ ] **Step 4: Add the env var to Compose for those three services**

```yaml
      - QR_SIGNATURE_SECRET=${QR_SIGNATURE_SECRET:?QR_SIGNATURE_SECRET must be set}
```

- [ ] **Step 5: Create `backend/.env.example`**

```bash
# Required — services refuse to start without these.
# Generate: openssl rand -base64 48
JWT_SECRET=
QR_SIGNATURE_SECRET=
POSTGRES_PASSWORD=
```

- [ ] **Step 6: Verify**

```bash
cd backend && grep -rn "tamper-proof-signature" --include=*.java . | grep -v /build/
```

Expected: **no output**.

- [ ] **Step 7: Commit**

```bash
git add backend/shared/common backend/*/src/main/resources/application.yml backend/docker-compose.yml backend/.env.example
git commit -m "fix(security): require QR signature secret, document required env vars"
```

### Task 1.3: Env-drive trace sampling and gateway log levels

**Files — Modify:** all 7 service `application.yml`; `backend/api-gateway/src/main/resources/application.yml:112-114`.

- [ ] **Step 1: Make sampling configurable, defaulting low**

Replace `probability: 1.0` with `probability: ${TRACE_SAMPLE_RATE:0.1}` in every service.

```bash
cd backend && sed -i 's|probability: 1.0|probability: ${TRACE_SAMPLE_RATE:0.1}|' */src/main/resources/application.yml
grep -rn "TRACE_SAMPLE_RATE" */src/main/resources/application.yml | wc -l
```

- [ ] **Step 2: Gate the gateway's TRACE logging**

In `api-gateway/src/main/resources/application.yml`, replace the hardcoded TRACE/DEBUG levels:

```yaml
logging:
  level:
    org.springframework.cloud.gateway: ${GATEWAY_LOG_LEVEL:INFO}
    org.springframework.security: ${SECURITY_LOG_LEVEL:INFO}
    reactor.netty: ${NETTY_LOG_LEVEL:INFO}
```

- [ ] **Step 3: Commit**

```bash
git add backend/*/src/main/resources/application.yml
git commit -m "chore(observability): env-drive trace sampling and gateway log levels"
```

---

## Phase 2 — Gateway Routing & Kafka Serialization (Critical)

### Task 2.1: Route `/api/v1/auth/**` to auth-service

**Files — Modify:** `backend/api-gateway/src/main/resources/application.yml:31-40`

The merchant route's predicate list ends with `/api/v1/**`, which swallows `/api/v1/auth/**` because the auth route only matches `/api/auth/**`. Spring Cloud Gateway evaluates routes in declaration order, so the fix is to widen the auth predicate — it is declared first.

- [ ] **Step 1: Widen the auth route predicate**

```yaml
            - id: auth-service
              uri: lb://auth-service
              predicates:
                - Path=/api/auth/**, /api/v1/auth/**
```

- [ ] **Step 2: Verify auth precedes merchant in declaration order**

```bash
cd backend && grep -n "id: auth-service\|id: merchant-service\|/api/v1/" api-gateway/src/main/resources/application.yml
```

Expected: the `auth-service` id line has a lower line number than `merchant-service`, and the auth predicate now contains `/api/v1/auth/**`.

- [ ] **Step 3: Commit**

```bash
git add backend/api-gateway/src/main/resources/application.yml
git commit -m "fix(gateway): route /api/v1/auth/** to auth-service instead of merchant-service"
```

### Task 2.2: Wire the configured circuit breaker into routes

**Files — Modify:** `backend/api-gateway/src/main/resources/application.yml`

`resilience4j.circuitbreaker.instances.default` is configured but no route references it, so nothing is protected.

- [ ] **Step 1: Add default filters once, rather than per-route**

Under `spring.cloud.gateway.server.webflux`, add a `default-filters` block so every route inherits protection:

```yaml
          default-filters:
            - name: CircuitBreaker
              args:
                name: default
                fallbackUri: forward:/fallback
            - name: Retry
              args:
                retries: 2
                methods: GET
                backoff:
                  firstBackoff: 50ms
                  maxBackoff: 500ms
                  factor: 2
```

Retry is restricted to `GET` deliberately: retrying a non-idempotent `POST /api/orders` would duplicate orders.

- [ ] **Step 2: Add the fallback endpoint**

Create `backend/api-gateway/src/main/java/com/qrserve/gateway/controller/FallbackController.java`:

```java
package com.qrserve.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FallbackController {

    @RequestMapping("/fallback")
    public ResponseEntity<Map<String, String>> fallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "SERVICE_UNAVAILABLE",
                        "message", "Upstream service is temporarily unavailable. Please retry."));
    }
}
```

- [ ] **Step 3: Verify (requires a working Gradle host)**

```bash
cd backend && ./gradlew :api-gateway:compileJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add backend/api-gateway
git commit -m "fix(gateway): apply CircuitBreaker and GET-only Retry filters to all routes"
```

### Task 2.3: Fix the merchant-service Kafka producer

**Files — Modify:** `backend/merchant-service/src/main/java/com/qrserve/merchant/config/KafkaProducerConfig.java:27-34`

Only `KEY_SERIALIZER_CLASS_CONFIG` is set, so the value serializer defaults and every event publish fails at runtime.

- [ ] **Step 1: Read order-service's working config to copy the exact serializer class**

```bash
cd backend && grep -n "import\|SERIALIZER" order-service/src/main/java/com/qrserve/order/config/KafkaProducerConfig.java
```

- [ ] **Step 2: Set the value serializer to match order-service**

Add the import and the missing property. Use whichever serializer order-service uses (Step 1 output) so both producers agree — if order-service is still on the Jackson 2 `JsonSerializer`, use that here and let Task 2.4 migrate both together.

```java
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
```

- [ ] **Step 3: Verify both serializers are now set**

```bash
cd backend && grep -n "SERIALIZER" merchant-service/src/main/java/com/qrserve/merchant/config/KafkaProducerConfig.java
```

Expected: two lines — `KEY_SERIALIZER_CLASS_CONFIG` and `VALUE_SERIALIZER_CLASS_CONFIG`.

- [ ] **Step 4: Compile-verify (requires a working Gradle host)**

```bash
cd backend && ./gradlew :merchant-service:compileJava
```

- [ ] **Step 5: Commit**

```bash
git add backend/merchant-service
git commit -m "fix(kafka): set missing VALUE_SERIALIZER on merchant-service producer"
```

### Task 2.4: Migrate Kafka serializers to Jackson 3

**Files — Modify:** `backend/order-service/.../config/KafkaProducerConfig.java`, `backend/order-service/src/main/resources/application.yml:31`, `backend/notification-service/.../config/KafkaConsumerConfig.java`, `backend/merchant-service/.../config/KafkaProducerConfig.java`

Boot 4.1 / Spring Kafka moved to Jackson 3: `JsonSerializer` → `JacksonJsonSerializer`, `JsonDeserializer`/`JacksonJsonDeserializer` per the migration plan §Step 2.

- [ ] **Step 1: Inventory every reference**

```bash
cd backend && grep -rn "JsonSerializer\|JsonDeserializer" --include=*.java --include=*.yml . | grep -v /build/
```

- [ ] **Step 2: Rewrite the Java imports and class references**

In each config class replace `org.springframework.kafka.support.serializer.JsonSerializer` with `org.springframework.kafka.support.serializer.JacksonJsonSerializer` (and the deserializer equivalent), including the `.class` literals in the props map.

- [ ] **Step 3: Rewrite the YAML fully-qualified class names**

`order-service/src/main/resources/application.yml:31` and the notification-service consumer properties reference the FQCN as a string; the Java rename does not touch these.

```bash
cd backend && sed -i 's|support.serializer.JsonSerializer|support.serializer.JacksonJsonSerializer|g; s|support.serializer.JsonDeserializer|support.serializer.JacksonJsonDeserializer|g' */src/main/resources/application.yml
grep -rn "serializer.Jackson" */src/main/resources/application.yml
```

- [ ] **Step 4: Verify no Jackson-2 name survives**

```bash
cd backend && grep -rn "serializer.JsonSerializer\|serializer.JsonDeserializer" --include=*.java --include=*.yml . | grep -v /build/
```

Expected: **no output**.

- [ ] **Step 5: Compile-verify (requires a working Gradle host)**

```bash
cd backend && ./gradlew :order-service:compileJava :notification-service:compileJava :merchant-service:compileJava
```

If the `JacksonJson*` classes do not resolve, the Spring Kafka version on the classpath predates the rename — confirm with `./gradlew :order-service:dependencies --configuration runtimeClasspath | grep spring-kafka` and keep the Jackson 2 names if so. Record the outcome; do not guess.

- [ ] **Step 6: Commit**

```bash
git add backend/order-service backend/notification-service backend/merchant-service
git commit -m "refactor(kafka): migrate serializers to Jackson 3 for Boot 4.1"
```

---

## Phase 3 — Authorization (Critical)

### Task 3.1: Fix `SecurityConfig` rule ordering and narrow the menu `permitAll`

**Files — Modify:** `backend/shared/security/src/main/java/com/qrserve/shared/security/SecurityConfig.java:47-85`

Two defects: `/api/menu/**` is blanket-public, and the narrow rules at lines 73-78 sit *below* the broad `permitAll` block despite a comment claiming otherwise (first-match-wins means they are unreachable for any path the broad block already matched).

- [ ] **Step 1: Write the failing test**

Create `backend/shared/security/src/test/java/com/qrserve/shared/security/SecurityConfigRuleOrderTest.java`:

```java
package com.qrserve.shared.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigRuleOrderTest {

    private String config() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/qrserve/shared/security/SecurityConfig.java"));
    }

    @Test
    void menuWildcardIsNotBlanketPublic() throws Exception {
        assertTrue(config().lines().noneMatch(l ->
                        l.contains("\"/api/menu/**\"") || l.contains("\"/api/v1/menu/**\"")),
                "Blanket permitAll on /api/menu/** exposes menu write endpoints");
    }

    @Test
    void narrowRulesPrecedeBroadPermitAll() throws Exception {
        String src = config();
        int narrow = src.indexOf("HttpMethod.GET, \"/api/menu/");
        int broad = src.indexOf("\"/api/v1/public/**\"");
        assertTrue(narrow > -1 && broad > -1 && narrow < broad,
                "Narrow menu GET rule must be declared before the broad public block");
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

```bash
cd backend && ./gradlew :shared:security:test --tests '*SecurityConfigRuleOrderTest*'
```

Expected: FAIL — both assertions.

- [ ] **Step 3: Rewrite the `authorizeHttpRequests` block**

Replace lines 47-85 entirely. Narrow rules first, broad last, and the public menu read reduced to a GET on a single path segment:

```java
            .authorizeHttpRequests(auth -> auth
                // 1. CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 2. WebSocket / SockJS handshake (STOMP interceptor authorizes frames)
                .requestMatchers("/ws/**", "/ws/info/**", "/ws/info").permitAll()

                // 3. System & docs
                .requestMatchers(
                    "/actuator/health", "/actuator/health/**", "/actuator/info",
                    "/error",
                    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
                ).permitAll()

                // 4. NARROW public reads — declared before any broad rule.
                //    Only the customer-facing menu read is public; writes stay protected.
                .requestMatchers(HttpMethod.GET, "/api/menu/*", "/api/v1/menu/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/tables/*", "/api/v1/tables/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/customer-requests", "/api/v1/customer-requests").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/customer-requests/table/**", "/api/v1/customer-requests/table/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orders", "/api/v1/orders").permitAll()

                // 5. Explicitly authenticated (declared before broad auth block for clarity)
                .requestMatchers(HttpMethod.GET, "/api/customer-requests", "/api/v1/customer-requests").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/customer-requests/**", "/api/v1/customer-requests/**").authenticated()
                .requestMatchers("/api/v1/waiters/**", "/api/v1/orders/**").authenticated()

                // 6. Auth endpoints (login/refresh must be public)
                .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                .requestMatchers("/api/v1/public/**").permitAll()

                // 7. Everything else needs a JWT; role checks live on the controllers.
                .anyRequest().authenticated()
            )
```

Note `/actuator/**` is narrowed to health/info — the previous blanket rule exposed `/actuator/env`, which leaks configuration including secret property names.

- [ ] **Step 4: Run the test to confirm it passes**

```bash
cd backend && ./gradlew :shared:security:test --tests '*SecurityConfigRuleOrderTest*'
```

Expected: PASS.

- [ ] **Step 5: Confirm `/api/auth/users` is no longer blanket-public**

Previously `/api/auth/**` was `permitAll`, making the user-creation endpoint reachable without a token (it was protected only by `@PreAuthorize`). It is now enumerated to login/refresh/logout only.

```bash
cd backend && grep -n '"/api/auth' shared/security/src/main/java/com/qrserve/shared/security/SecurityConfig.java
```

Expected: only `login`, `refresh`, `logout` appear.

- [ ] **Step 6: Commit**

```bash
git add backend/shared/security
git commit -m "fix(security): order authz rules narrow-first, narrow menu and actuator exposure"
```

### Task 3.2: Add `@PreAuthorize` to every mutating controller

**Files — Modify:**
- `merchant-service/.../controller/MerchantController.java`
- `merchant-service/.../controller/BranchController.java`
- `merchant-service/.../controller/TableController.java`
- `merchant-service/.../controller/TableAssignmentController.java`
- `merchant-service/.../controller/WaiterController.java`
- `merchant-service/.../controller/CustomerRequestController.java`
- `menu-service/.../controller/ProductController.java`
- `menu-service/.../controller/CategoryController.java`
- `order-service/.../controller/OrderController.java`
- `order-service/.../controller/KitchenController.java`
- `qr-service/.../controller/QrController.java`
- `analytics-service/.../controller/AnalyticsController.java`

Role model (from `UserRole`): `SUPER_ADMIN > MERCHANT_OWNER > BRANCH_MANAGER > {WAITER, KITCHEN, CASHIER} > CUSTOMER`.

- [ ] **Step 1: Confirm `@EnableMethodSecurity` is active**

`SecurityConfig:22` already has it, and it is a shared bean, so `@PreAuthorize` is honored in every service.

```bash
cd backend && grep -rn "EnableMethodSecurity" shared/security/src/main/java/
```

- [ ] **Step 2: Apply the authorization matrix**

Add `import org.springframework.security.access.prepost.PreAuthorize;` to each file, then annotate per this matrix. Where a class-level default fits, put it on the class and override on the exceptions.

| Controller | Reads | Writes |
|---|---|---|
| `MerchantController` | `SUPER_ADMIN, MERCHANT_OWNER` | `SUPER_ADMIN` only (create/update/delete merchants) |
| `BranchController` | `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER` | `SUPER_ADMIN, MERCHANT_OWNER` |
| `TableController` | `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER, WAITER` | `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER`; status PATCH also `WAITER` |
| `TableAssignmentController` | `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER` | `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER` |
| `WaiterController` | `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER` | `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER` |
| `CustomerRequestController` | `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER, WAITER` | status update: same set |
| `ProductController` | authenticated | `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER` |
| `CategoryController` | authenticated | `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER` |
| `OrderController` | `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER, WAITER, KITCHEN, CASHIER` | status update: `BRANCH_MANAGER, WAITER, KITCHEN, CASHIER, MERCHANT_OWNER, SUPER_ADMIN`. **Leave `POST /api/orders` unannotated** — customer ordering is intentionally public per `SecurityConfig`. |
| `KitchenController` | `KITCHEN, BRANCH_MANAGER, MERCHANT_OWNER, SUPER_ADMIN` | same |
| `QrController` | `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER` | same |
| `AnalyticsController` | `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER` | n/a |

Example — the exact form to use, on `BranchController`:

```java
@RestController
@RequestMapping("/api/branches")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
public class BranchController {

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER')")
    public ResponseEntity<BranchEntity> createBranch(@Valid @RequestBody CreateBranchRequest request) {
```

`hasAnyRole` expects unprefixed names; `UserPrincipal` must expose authorities as `ROLE_<NAME>`.

- [ ] **Step 3: Verify the authority prefix, or every rule silently denies**

```bash
cd backend && grep -rn "ROLE_\|getAuthorities" shared/security/src/main/java/com/qrserve/shared/security/UserPrincipal.java
```

If authorities are built without the `ROLE_` prefix, either add the prefix there or switch every annotation to `hasAnyAuthority(...)`. **Do not skip this check** — a mismatch turns all 12 controllers into blanket 403s, which is a louder failure than the vulnerability but still an outage.

- [ ] **Step 4: Confirm coverage**

```bash
cd backend && for f in merchant menu order qr analytics; do
  echo "== $f =="
  grep -rln "PreAuthorize" $f-service/src/main/java/**/controller/ 2>/dev/null
done
```

Expected: all 12 controllers listed.

- [ ] **Step 5: Compile-verify (requires a working Gradle host)**

```bash
cd backend && ./gradlew compileJava
```

- [ ] **Step 6: Commit**

```bash
git add backend/*/src/main/java/**/controller/
git commit -m "fix(security): add role-based @PreAuthorize to all mutating controllers"
```

### Task 3.3: Enforce role hierarchy on user creation

**Files — Modify:** `backend/auth-service/.../service/AuthService.java:86-102`, `backend/auth-service/.../controller/AuthController.java:51-63`

A `MERCHANT_OWNER` can currently create a `SUPER_ADMIN`, and can set an arbitrary `merchantId`.

- [ ] **Step 1: Write the failing test**

Create `backend/auth-service/src/test/java/com/qrserve/auth/service/RoleHierarchyTest.java`:

```java
package com.qrserve.auth.service;

import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleHierarchyTest {

    @Test
    void merchantOwnerCannotCreateSuperAdmin() {
        assertFalse(AuthService.canAssignRole(UserRole.MERCHANT_OWNER, UserRole.SUPER_ADMIN));
    }

    @Test
    void merchantOwnerCannotCreatePeer() {
        assertFalse(AuthService.canAssignRole(UserRole.MERCHANT_OWNER, UserRole.MERCHANT_OWNER));
    }

    @Test
    void merchantOwnerCanCreateSubordinates() {
        assertTrue(AuthService.canAssignRole(UserRole.MERCHANT_OWNER, UserRole.BRANCH_MANAGER));
        assertTrue(AuthService.canAssignRole(UserRole.MERCHANT_OWNER, UserRole.WAITER));
    }

    @Test
    void superAdminCanCreateAnything() {
        for (UserRole target : UserRole.values()) {
            assertTrue(AuthService.canAssignRole(UserRole.SUPER_ADMIN, target));
        }
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

```bash
cd backend && ./gradlew :auth-service:test --tests '*RoleHierarchyTest*'
```

Expected: FAIL — `canAssignRole` does not exist.

- [ ] **Step 3: Add the rank helper and enforce it in `createUser`**

In `AuthService`, add:

```java
    /** Lower rank = more privilege. Package-visible for testing. */
    private static int rankOf(UserRole role) {
        return switch (role) {
            case SUPER_ADMIN -> 0;
            case MERCHANT_OWNER -> 1;
            case BRANCH_MANAGER -> 2;
            case WAITER, KITCHEN, CASHIER -> 3;
            case CUSTOMER -> 4;
        };
    }

    /** A caller may only create roles strictly less privileged than their own. */
    static boolean canAssignRole(UserRole caller, UserRole target) {
        if (caller == UserRole.SUPER_ADMIN) {
            return true;
        }
        return rankOf(target) > rankOf(caller);
    }
```

Then change the `createUser` signature to accept the caller and enforce both role rank and tenant:

```java
    @Transactional
    public UserEntity createUser(CreateUserRequest request, UserPrincipal caller) {
        if (caller == null) {
            throw new UnauthorizedException("Authentication required to create users");
        }
        if (!canAssignRole(caller.getRole(), request.getRole())) {
            throw new UnauthorizedException(
                "Role " + caller.getRole() + " may not create a user with role " + request.getRole());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("User with email " + request.getEmail() + " already exists.");
        }

        // Non-super-admins may only create users inside their own tenant.
        UUID merchantId = caller.getRole() == UserRole.SUPER_ADMIN
                ? request.getMerchantId()
                : caller.getMerchantId();
        if (merchantId == null && caller.getRole() != UserRole.SUPER_ADMIN) {
            throw new UnauthorizedException("Caller has no merchant scope; cannot create users");
        }

        UserEntity user = UserEntity.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .merchantId(merchantId)
                .enabled(true)
                .build();

        return userRepository.save(user);
    }
```

- [ ] **Step 4: Update the controller call site**

In `AuthController.createUser`, inject the principal and pass it:

```java
    public ResponseEntity<Map<String, Object>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserEntity createdUser = authService.createUser(request, principal);
```

- [ ] **Step 5: Run the test to confirm it passes**

```bash
cd backend && ./gradlew :auth-service:test --tests '*RoleHierarchyTest*'
```

Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add backend/auth-service
git commit -m "fix(security): enforce role hierarchy and tenant scope on user creation"
```

### Task 3.4: Fix `listUsers` tenant scoping

**Files — Modify:** `backend/auth-service/.../controller/AuthController.java:65-76`

`UUID scope = principal.getMerchantId() != null ? principal.getMerchantId() : merchantId;` — a `MERCHANT_OWNER` with a null `merchantId` claim falls through to the caller-supplied param, and `listUsers(null)` returns `findAll()`.

- [ ] **Step 1: Replace the scoping logic with an explicit role branch**

```java
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_OWNER', 'BRANCH_MANAGER')")
    @Operation(summary = "List users, optionally scoped to a merchant")
    public ResponseEntity<List<UserInfoResponse>> listUsers(
            @RequestParam(required = false) UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        // Only SUPER_ADMIN may choose a scope (including "all"); everyone else is
        // pinned to their own tenant and rejected if they have none.
        UUID scope;
        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            scope = merchantId;
        } else {
            scope = principal.getMerchantId();
            if (scope == null) {
                throw new UnauthorizedException("Caller has no merchant scope");
            }
        }
        return ResponseEntity.ok(authService.listUsers(scope));
    }
```

Add imports: `com.qrserve.shared.exceptions.UnauthorizedException`, `com.qrserve.shared.security.UserRole`.

- [ ] **Step 2: Verify the fallback is gone**

```bash
cd backend && grep -n ": merchantId" auth-service/src/main/java/com/qrserve/auth/controller/AuthController.java
```

Expected: **no output**.

- [ ] **Step 3: Commit**

```bash
git add backend/auth-service
git commit -m "fix(security): pin non-super-admin listUsers to the caller's own tenant"
```

---

## Phase 4 — Tenant Isolation (High)

### Task 4.1: Make the customer-request tenant check mandatory

**Files — Modify:** `backend/merchant-service/.../service/CustomerRequestService.java:70-76`

`if (merchantId != null && !merchantId.equals(...))` skips the check entirely when the caller omits the param.

- [ ] **Step 1: Require the tenant and always compare**

```java
        if (merchantId == null) {
            throw new UnauthorizedException("merchantId is required to update a customer request");
        }
        if (!merchantId.equals(request.getMerchantId())) {
            throw new ResourceNotFoundException("Customer request not found ID: " + requestId);
        }
```

Returning not-found rather than forbidden avoids confirming the existence of another tenant's record.

- [ ] **Step 2: Derive the tenant from the JWT at the call site**

In `CustomerRequestController.updateStatus`, replace the optional `@RequestParam UUID merchantId` with `@AuthenticationPrincipal UserPrincipal principal` and pass `principal.getMerchantId()`.

- [ ] **Step 3: Verify**

```bash
cd backend && grep -n "merchantId != null" merchant-service/src/main/java/com/qrserve/merchant/service/CustomerRequestService.java
```

Expected: **no output**.

- [ ] **Step 4: Commit**

```bash
git add backend/merchant-service
git commit -m "fix(security): enforce mandatory tenant check on customer request updates"
```

### Task 4.2: Validate `branchId` in `assignWaiterToTable`

**Files — Modify:** `backend/merchant-service/.../service/TableAssignmentService.java:31-46`

Waiter and table are checked against `merchantId`, but the caller-supplied `branchId` is never compared to either entity's branch.

- [ ] **Step 1: Add both branch assertions after the existing merchant checks**

```java
        // The caller-supplied branch must match both entities, or an assignment
        // can be created that binds a waiter to a table in a different branch.
        if (branchId == null || !branchId.equals(table.getBranchId())) {
            throw new BusinessException("Table " + tableId + " does not belong to branch " + branchId);
        }
        if (!branchId.equals(waiter.getBranchId())) {
            throw new BusinessException("Waiter " + waiterId + " does not belong to branch " + branchId);
        }
```

- [ ] **Step 2: Confirm the exception type exists**

```bash
cd backend && ls shared/exceptions/src/main/java/com/qrserve/shared/exceptions/
```

If `BusinessException` is absent, use `IllegalArgumentException` (already mapped by `GlobalExceptionHandler`).

- [ ] **Step 3: Commit**

```bash
git add backend/merchant-service
git commit -m "fix(security): validate branchId against table and waiter on assignment"
```

### Task 4.3: Tenant-scope analytics and fix the phantom tables URL

**Files — Modify:** `backend/analytics-service/.../service/AnalyticsService.java:40-73,116-141`

Two findings combine here: `getTodayMetrics(merchantId)` ignores the tenant (every merchant sees global revenue), and `fetchTables()` calls `/api/tables`, which has no mapping — the 404 is swallowed, so table counts are silently always zero.

- [ ] **Step 1: Correct the URL and thread the tenant through both fetches**

```java
    private List<Map<String, Object>> fetchOrders(UUID merchantId) {
        try {
            String url = orderServiceUrl + "/api/orders?merchantId=" + merchantId;
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(getAuthHeaders()),
                    new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("order-service unavailable", e);
        }
    }

    private List<Map<String, Object>> fetchTables(UUID merchantId) {
        try {
            // NOTE: the listing endpoint is /api/tables/all — /api/tables has no mapping.
            String url = merchantServiceUrl + "/api/tables/all?merchantId=" + merchantId;
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(getAuthHeaders()),
                    new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("merchant-service unavailable", e);
        }
    }
```

Update both call sites in `getTodayMetrics` to pass `merchantId`.

- [ ] **Step 2: Confirm the downstream endpoints honor a `merchantId` filter**

```bash
cd backend && grep -n "RequestParam\|GetMapping" merchant-service/src/main/java/com/qrserve/merchant/controller/TableController.java order-service/src/main/java/com/qrserve/order/controller/OrderController.java
```

If `/api/tables/all` and `/api/orders` do not accept `merchantId`, add the param and a repository method `findByMerchantId` — server-side filtering is the point of this fix. Filtering client-side after fetching everything still leaks data across the wire.

- [ ] **Step 3: Verify the phantom URL is gone**

```bash
cd backend && grep -n '"/api/tables"' analytics-service/src/main/java/com/qrserve/analytics/service/AnalyticsService.java
```

Expected: **no output**.

- [ ] **Step 4: Commit**

```bash
git add backend/analytics-service backend/merchant-service backend/order-service
git commit -m "fix(security): tenant-scope analytics fetches and correct phantom tables URL"
```

---

## Phase 5 — Realtime Security (High)

### Task 5.1: Delete the unauthenticated raw WebSocket endpoint

**Files — Delete:** `backend/notification-service/.../config/WebSocketConfig.java`, `backend/notification-service/.../handler/OrderWebSocketHandler.java`

`WebSocketConfig` registers `/ws/orders` with `setAllowedOrigins("*")` and no authentication. STOMP on `/ws` replaced it.

- [ ] **Step 1: Confirm nothing references either class**

```bash
cd backend && grep -rn "OrderWebSocketHandler\|WebSocketConfig\|/ws/orders" --include=*.java --include=*.ts --include=*.tsx . ../src | grep -v /build/
```

Also check the frontend does not connect to `/ws/orders`:

```bash
cd /c/Users/wendafrash.buzuayehu/Downloads/product/blank-canvas && grep -rn "ws/orders" src/
```

Expected: only the two files' own definitions. If the frontend does use it, stop and migrate that client to STOMP first.

- [ ] **Step 2: Check whether `NotificationService` depends on the handler**

```bash
cd backend && grep -n "class\|Handler\|SimpMessagingTemplate" notification-service/src/main/java/com/qrserve/notification/service/NotificationService.java
```

If it broadcasts through the raw handler, migrate it to `SimpMessagingTemplate`; if it is unreferenced dead code, delete it too.

- [ ] **Step 3: Delete the files**

```bash
cd backend && git rm notification-service/src/main/java/com/qrserve/notification/config/WebSocketConfig.java \
  notification-service/src/main/java/com/qrserve/notification/handler/OrderWebSocketHandler.java
```

- [ ] **Step 4: Compile-verify (requires a working Gradle host)**

```bash
cd backend && ./gradlew :notification-service:compileJava
```

- [ ] **Step 5: Commit**

```bash
git commit -m "fix(security): remove unauthenticated raw /ws/orders WebSocket handler"
```

### Task 5.2: Add STOMP destination-level authorization

**Files — Modify:** `backend/notification-service/.../interceptor/StompAuthInterceptor.java:54-60`

The interceptor confirms a principal exists but never checks it against the destination, so any authenticated user can subscribe to any merchant's topics.

- [ ] **Step 1: Read the current interceptor and the topic naming convention**

```bash
cd backend && cat -n notification-service/src/main/java/com/qrserve/notification/interceptor/StompAuthInterceptor.java
grep -rn "convertAndSend" notification-service/src/main/java/ | head
```

Record the exact destination patterns in use before writing the matcher — guessing them produces either a security hole or a total subscription outage.

- [ ] **Step 2: Add a destination authorization check on SUBSCRIBE and SEND**

```java
    /**
     * Authorizes a principal for a destination. Topics are tenant-scoped as
     * /topic/merchant/{merchantId}/... — a subscriber must match that merchantId.
     * SUPER_ADMIN may observe any tenant.
     */
    private void authorizeDestination(UserPrincipal principal, String destination) {
        if (destination == null || principal == null) {
            throw new MessagingException("Destination and authentication are required");
        }
        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }
        Matcher m = MERCHANT_TOPIC.matcher(destination);
        if (m.find()) {
            UUID topicMerchant = UUID.fromString(m.group(1));
            if (!topicMerchant.equals(principal.getMerchantId())) {
                throw new MessagingException("Not authorized for destination " + destination);
            }
        }
    }

    private static final Pattern MERCHANT_TOPIC =
            Pattern.compile("/topic/merchant/([0-9a-fA-F-]{36})(/|$)");
```

Call it from the existing SUBSCRIBE/SEND branch, passing the resolved principal.

- [ ] **Step 3: Verify the pattern matches the real destinations from Step 1**

Adjust the regex to the actual convention. If topics are also keyed by `orderId` for customers, add a branch permitting a customer to subscribe only to their own order.

- [ ] **Step 4: Commit**

```bash
git add backend/notification-service
git commit -m "fix(security): authorize STOMP subscriptions against the principal's tenant"
```

---

## Phase 6 — Error Handling (High/Medium)

### Task 6.1: Add `ServiceUnavailableException` and stop masking outages as 404

**Files — Create:** `backend/shared/exceptions/.../ServiceUnavailableException.java`
**Files — Modify:** `backend/shared/exceptions/.../GlobalExceptionHandler.java`, `order-service/.../OrderService.java:186-189,211-214`, `qr-service/.../QrGeneratorService.java:114-117,137-140`

`catch (Exception e) { throw new ResourceNotFoundException("Table not found"); }` reports a downstream outage as a missing entity.

- [ ] **Step 1: Create the exception**

```java
package com.qrserve.shared.exceptions;

/** A downstream dependency was unreachable or returned a server error. Maps to 503. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 2: Map it in `GlobalExceptionHandler`, and stop leaking internals from the catch-all**

```java
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(ServiceUnavailableException ex) {
        log.error("Downstream dependency unavailable", ex);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "A required service is temporarily unavailable. Please retry.");
    }
```

And change `handleGeneralException` (lines 80-88) to log the cause but return a fixed string instead of `ex.getMessage()`:

```java
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        // Log the real cause; never return it — messages leak SQL, URLs and stack hints.
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred.");
    }
```

Match `buildResponse`/`ErrorResponse` to the existing shape in that file rather than inventing one.

- [ ] **Step 3: Distinguish 404 from transport errors at each fetch site**

Pattern for `OrderService.fetchTable`/`fetchProduct` and `QrGeneratorService.fetchMerchant`:

```java
        try {
            return restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(getAuthHeaders()), TableDto.class).getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Table not found ID: " + tableId);
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("merchant-service unavailable", e);
        }
```

- [ ] **Step 4: Verify no blanket catch remains at those sites**

```bash
cd backend && grep -n "catch (Exception" order-service/src/main/java/com/qrserve/order/service/OrderService.java qr-service/src/main/java/com/qrserve/qr/service/QrGeneratorService.java
```

Expected: no blanket catch inside the fetch helpers.

- [ ] **Step 5: Commit**

```bash
git add backend/shared/exceptions backend/order-service backend/qr-service
git commit -m "fix(errors): distinguish downstream outages from not-found; stop leaking internals"
```

### Task 6.2: Surface analytics degradation instead of reporting zeros

**Files — Modify:** `backend/analytics-service/.../service/AnalyticsService.java`, its response DTO

Task 4.3 already converted the swallowing catches into `ServiceUnavailableException`. Decide the contract: either propagate 503, or return a `degraded` flag so the dashboard can distinguish "no sales" from "no data".

- [ ] **Step 1: Add a `degraded` boolean to `TodayAnalyticsResponse`**

- [ ] **Step 2: Catch `ServiceUnavailableException` in `getTodayMetrics` only, set `degraded=true`, and return zeros with the flag set**

This keeps the dashboard rendering while making the failure visible, rather than silently claiming zero revenue.

- [ ] **Step 3: Commit**

```bash
git add backend/analytics-service
git commit -m "feat(analytics): expose degraded flag when downstream data is unavailable"
```

### Task 6.3: Stop swallowing table-status update failures

**Files — Modify:** `backend/order-service/.../service/OrderService.java:217-225`

- [ ] **Step 1: Add `@Retryable` with backoff, and log at ERROR when retries are exhausted**

If `spring-retry` is not a dependency, do a bounded manual retry (3 attempts, 100ms/200ms/400ms) and log at ERROR with the order and table id so monitoring can alert. Do not leave it at `log.warn` — a silently AVAILABLE table that is actually occupied is a customer-visible failure.

- [ ] **Step 2: Commit**

```bash
git add backend/order-service
git commit -m "fix(orders): retry and alert on table status update failure"
```

---

## Phase 7 — Data Layer (High/Medium)

### Task 7.1: Fix the N+1 in `MenuService.getFullMenu`

**Files — Modify:** `backend/menu-service/.../service/MenuService.java:137-159`

- [ ] **Step 1: Confirm the repository has a merchant-wide product finder**

```bash
cd backend && grep -n "List<ProductEntity>" menu-service/src/main/java/com/qrserve/menu/repository/ProductRepository.java
```

Add `List<ProductEntity> findByMerchantId(UUID merchantId);` if absent.

- [ ] **Step 2: Replace the per-category query with one query plus an in-memory group**

```java
        List<CategoryEntity> categories = categoryRepository.findByMerchantId(merchantId);
        Map<Long, List<ProductEntity>> productsByCategory = productRepository
                .findByMerchantId(merchantId).stream()
                .collect(Collectors.groupingBy(ProductEntity::getCategoryId));

        List<MenuCategoryDto> categoryDtos = categories.stream()
                .map(cat -> toCategoryDto(cat,
                        productsByCategory.getOrDefault(cat.getId(), List.of())))
                .toList();
```

Keep the existing DTO mapping — only the fetch strategy changes.

- [ ] **Step 3: Verify the loop query is gone**

```bash
cd backend && grep -n "findByCategoryId" menu-service/src/main/java/com/qrserve/menu/service/MenuService.java
```

Expected: no call inside a loop over categories.

- [ ] **Step 4: Commit**

```bash
git add backend/menu-service
git commit -m "perf(menu): replace per-category N+1 with a single product query"
```

### Task 7.2: Fix the N+1 and per-row REST call in `KitchenService.getKitchenOrders`

**Files — Modify:** `backend/order-service/.../service/KitchenService.java:56-85`

Each order triggers both a REST call for the table number and a query for items.

- [ ] **Step 1: Batch the item fetch**

Add `List<OrderItemEntity> findByOrderIdIn(Collection<UUID> orderIds);` to `OrderItemRepository`, then group by `orderId` in memory.

- [ ] **Step 2: Batch the table lookup into a single call**

Collect the distinct `tableId`s and make one request. If merchant-service has no bulk table endpoint, add `GET /api/tables/all?ids=1,2,3` — do not leave a per-row REST call in a kitchen display that polls every 10 seconds.

- [ ] **Step 3: Commit**

```bash
git add backend/order-service backend/merchant-service
git commit -m "perf(kitchen): batch order item and table lookups"
```

### Task 7.3: Fix the N+1 in `WaiterTaskV1Controller.getTasks`

**Files — Modify:** `backend/merchant-service/.../controller/WaiterTaskV1Controller.java:66`

- [ ] **Step 1: Replace the in-loop `findById` with a batched `findAllById`**

```java
        List<Long> tableIds = assignments.stream().map(TableAssignmentEntity::getTableId).distinct().toList();
        Map<Long, TableEntity> tables = tableRepository.findAllById(tableIds).stream()
                .collect(Collectors.toMap(TableEntity::getId, t -> t));
```

Then read from the map inside the existing loop.

- [ ] **Step 2: Commit**

```bash
git add backend/merchant-service
git commit -m "perf(waiter): batch table lookups in task listing"
```

### Task 7.4: Move external calls out of the order transaction

**Files — Modify:** `backend/order-service/.../service/OrderService.java:53-128`

`createOrder` is `@Transactional` and performs REST fetches, a table-status REST update, and a Kafka publish inside the DB transaction, so a downstream failure can leave side effects inconsistent with the committed row.

- [ ] **Step 1: Split the method into validate → persist → publish**

Keep only persistence inside `@Transactional`. Structure:

1. **Before the transaction:** `fetchTable`, `fetchProduct` per item (reads — safe to fail fast, nothing persisted yet).
2. **Inside `@Transactional`:** build and `save` the order plus items; nothing else.
3. **After commit:** `updateTableStatus` and the Kafka publish, via `@TransactionalEventListener(phase = AFTER_COMMIT)` on a small internal event, so they never run for a rolled-back order.

- [ ] **Step 2: Verify no REST or Kafka call remains inside the transactional method**

```bash
cd backend && sed -n '/@Transactional/,/^    }/p' order-service/src/main/java/com/qrserve/order/service/OrderService.java | grep -n "restTemplate\|kafkaTemplate\|publish"
```

Expected: **no output**.

- [ ] **Step 3: Commit**

```bash
git add backend/order-service
git commit -m "fix(orders): scope transaction to persistence, publish side effects after commit"
```

### Task 7.5: Enable Flyway with generated baselines and indexes

**Files — Create:** `backend/{auth,merchant,menu,order,qr,analytics,notification}-service/src/main/resources/db/migration/V1__init.sql` and `V2__indexes.sql`
**Files — Modify:** those services' `application.yml`

Per the scope decision: Flyway on, `ddl-auto: validate` in the **prod profile only**, dev keeps `update`.

- [ ] **Step 1: Generate each `V1__init.sql` from the JPA entities**

For every entity in each service, write the `CREATE TABLE` matching the mapped columns and types exactly. Derive column names from `@Column`/`@Table` annotations, not from guesses — a mismatch makes `validate` fail at prod boot. List the entities first:

```bash
cd backend && for s in auth merchant menu order qr analytics notification; do
  echo "== $s =="; find $s-service/src/main/java -name "*Entity.java" -exec basename {} \;
done
```

- [ ] **Step 2: Write `V2__indexes.sql` with the composite indexes from the review**

```sql
-- order-service
CREATE INDEX IF NOT EXISTS idx_orders_merchant_status ON orders (merchant_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_branch_status   ON orders (branch_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_table_status    ON orders (table_id, status);
CREATE INDEX IF NOT EXISTS idx_order_items_order      ON order_items (order_id);
```

```sql
-- merchant-service
CREATE INDEX IF NOT EXISTS idx_customer_requests_merchant_status ON customer_requests (merchant_id, status);
CREATE INDEX IF NOT EXISTS idx_table_assignments_table_active    ON table_assignments (table_id, active);
CREATE INDEX IF NOT EXISTS idx_table_assignments_waiter_active   ON table_assignments (waiter_id, active);
CREATE INDEX IF NOT EXISTS idx_waiters_merchant_branch           ON waiters (merchant_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_tables_branch_status              ON tables (branch_id, status);
```

Match the real table and column names from Step 1 before running these.

- [ ] **Step 3: Enable Flyway and add a prod profile**

Per service:

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
  jpa:
    hibernate:
      ddl-auto: ${DDL_AUTO:update}

---
spring:
  config:
    activate:
      on-profile: prod
  jpa:
    hibernate:
      ddl-auto: validate
```

- [ ] **Step 4: Verify against a real database — this is the one step that cannot be skipped**

```bash
cd backend && docker compose up -d postgres && ./gradlew :order-service:bootRun --args='--spring.profiles.active=prod'
```

Expected: Flyway applies V1 and V2, then Hibernate `validate` passes with no schema-mismatch error. If validation fails, fix the DDL to match the entity — never relax `validate` to make it pass.

- [ ] **Step 5: Commit**

```bash
git add backend/*/src/main/resources/db backend/*/src/main/resources/application.yml
git commit -m "feat(db): add Flyway baselines and composite indexes; validate schema in prod"
```

---

## Phase 8 — API Contract (High/Medium)

### Task 8.1: Resolve the phantom branch/table/merchant endpoints

**Files — Modify:** `backend/merchant-service/.../controller/BranchController.java`, `TableController.java`, `MerchantController.java`, or `src/lib/api.ts`

The frontend calls `PUT/DELETE /api/branches/{id}`, `PUT/DELETE /api/tables/{id}`, and `DELETE /api/merchants/{id}`; none exist.

- [ ] **Step 1: Determine which are actually used by the UI**

```bash
cd /c/Users/wendafrash.buzuayehu/Downloads/product/blank-canvas
for m in updateBranch deleteBranch updateTable deleteTable deleteMerchant; do
  echo "== $m =="; grep -rn "$m" src/ --include=*.ts --include=*.tsx | grep -v "lib/api.ts"
done
```

- [ ] **Step 2: For each method with call sites, add the backend mapping**

Implement `PUT`/`DELETE` on the controller delegating to the service, with the `@PreAuthorize` from Task 3.2's matrix and a tenant check. Example shape for `BranchController`:

```java
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER')")
    public ResponseEntity<BranchEntity> updateBranch(
            @PathVariable Long id,
            @Valid @RequestBody CreateBranchRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(branchService.updateBranch(id, request, principal.getMerchantId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER')")
    public ResponseEntity<Void> deleteBranch(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        branchService.deleteBranch(id, principal.getMerchantId());
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 3: For methods with no call sites, delete them from `src/lib/api.ts`**

Dead client methods that 404 are a trap for the next developer.

- [ ] **Step 4: Verify the frontend still typechecks**

```bash
cd /c/Users/wendafrash.buzuayehu/Downloads/product/blank-canvas && npx tsc --noEmit
```

Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add backend/merchant-service src/lib/api.ts
git commit -m "fix(api): resolve phantom branch/table/merchant endpoints"
```

### Task 8.2: Validate status transitions against enums

**Files — Modify:** `backend/merchant-service/.../controller/TableController.java:45`, `backend/order-service/.../controller/OrderController.java:44-50`

`updateTableStatus` takes an unvalidated `Map<String,String>`; `updateOrderStatus` accepts any string.

- [ ] **Step 1: Replace the raw map with a validated DTO**

Create `UpdateTableStatusRequest` with a typed enum field:

```java
public class UpdateTableStatusRequest {
    @NotNull(message = "status is required")
    private TableStatus status;
    // getter/setter
}
```

Binding to the enum makes Spring reject unknown values with a 400 before any persistence.

- [ ] **Step 2: Do the same for order status, and reject illegal transitions**

Accepting `PENDING → PAID` directly is a business-rule violation even when the enum value is valid. Add a transition guard in `OrderService.updateOrderStatus`.

- [ ] **Step 3: Commit**

```bash
git add backend/merchant-service backend/order-service
git commit -m "fix(api): validate table and order status against enums and legal transitions"
```

### Task 8.3: Fix the mislabeled analytics endpoint and mark placeholders

**Files — Modify:** `backend/analytics-service/.../controller/AnalyticsController.java:42-46`, `.../service/AnalyticsService.java:77-114`

`GET /api/analytics/orders` returns `getTodayMetrics` — the same payload as `/today`. `getRevenueAnalytics` and `getPopularItems` return hardcoded data.

- [ ] **Step 1: Remove the duplicate endpoint**

Deleting is correct here: it has no distinct contract, and a real implementation belongs with the deferred aggregation work.

- [ ] **Step 2: Mark the hardcoded methods honestly**

```java
    /**
     * PLACEHOLDER — returns synthetic data, not real aggregation.
     * Real implementation requires an order aggregation source; tracked as deferred
     * work in docs/superpowers/plans/2026-08-18-codebase-review-remediation.md.
     */
    @Deprecated(forRemoval = false)
```

An honest annotation is better than a dashboard that silently presents invented numbers as fact.

- [ ] **Step 3: Confirm the frontend does not call the removed endpoint**

```bash
cd /c/Users/wendafrash.buzuayehu/Downloads/product/blank-canvas && grep -rn "analytics/orders" src/
```

If it does, remove that client method and its call sites, then re-run `npx tsc --noEmit`.

- [ ] **Step 4: Commit**

```bash
git add backend/analytics-service src/
git commit -m "fix(analytics): remove duplicate orders endpoint, mark synthetic data as placeholder"
```

### Task 8.4: Remove the ignored `merchantId` param on `getWaiters`

**Files — Modify:** `src/lib/api.ts:707-713`

- [ ] **Step 1: Drop the unused query param, since the backend derives the tenant from the JWT**

- [ ] **Step 2: Verify**

```bash
cd /c/Users/wendafrash.buzuayehu/Downloads/product/blank-canvas && npx tsc --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add src/lib/api.ts
git commit -m "chore(api): drop merchantId param the waiters endpoint ignores"
```

---

## Phase 9 — Code Quality & Token Hardening

### Task 9.1: Remove dead code and duplicate imports

**Files — Modify:** `shared/security/.../JwtAuthenticationFilter.java:76-82`, `merchant-service/.../WaiterController.java:16,21,23`

- [ ] **Step 1: Delete `getJwtFromRequest` after confirming it is unreferenced**

```bash
cd backend && grep -rn "getJwtFromRequest" --include=*.java . | grep -v /build/
```

Expected: only its own declaration.

- [ ] **Step 2: Clean the duplicate and unused imports in `WaiterController`**

Remove the duplicated `GrantedAuthority`/`HttpServletRequest` and the unused `UsernameNotFoundException`, `AccessDeniedException`, `Authentication`.

- [ ] **Step 3: Commit**

```bash
git add backend/shared/security backend/merchant-service
git commit -m "chore: remove dead JWT helper and duplicate imports"
```

### Task 9.2: Use `@AuthenticationPrincipal` in `WaiterController`

**Files — Modify:** `backend/merchant-service/.../controller/WaiterController.java:58-60,132-147`

It re-parses the raw `Authorization` header instead of using the authenticated principal.

- [ ] **Step 1: Replace manual parsing with `@AuthenticationPrincipal UserPrincipal principal`**

- [ ] **Step 2: Delete the now-unused private JWT helper methods (lines ~132-147)**

- [ ] **Step 3: Verify no manual header parsing remains**

```bash
cd backend && grep -n "getHeader(\"Authorization\")\|substring(7)" merchant-service/src/main/java/com/qrserve/merchant/controller/WaiterController.java
```

Expected: **no output**.

- [ ] **Step 4: Commit**

```bash
git add backend/merchant-service
git commit -m "refactor(waiter): use @AuthenticationPrincipal instead of manual JWT parsing"
```

### Task 9.3: Harden token claims

**Files — Modify:** `shared/security/.../JwtTokenProvider.java:66-73,105-109`, `auth-service/.../AuthService.java:56,60-84`

Three findings: refresh tokens are indistinguishable from access tokens, a missing role claim silently becomes `CUSTOMER`, and `expiresIn` is hardcoded to 3600.

- [ ] **Step 1: Write the failing test**

Create `backend/shared/security/src/test/java/com/qrserve/shared/security/TokenTypeTest.java`:

```java
package com.qrserve.shared.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenTypeTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(
            "dGVzdC1zZWNyZXQtZm9yLXVuaXQtdGVzdHMtb25seS1sb25nLWVub3VnaA==",
            3600000L, 604800000L);

    private UserPrincipal principal() {
        return UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .email("t@example.com")
                .role(UserRole.WAITER)
                .build();
    }

    @Test
    void accessTokenIsNotAcceptedAsRefreshToken() {
        assertFalse(provider.isRefreshToken(provider.generateAccessToken(principal())));
    }

    @Test
    void refreshTokenIsIdentified() {
        assertTrue(provider.isRefreshToken(provider.generateRefreshToken(principal())));
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

```bash
cd backend && ./gradlew :shared:security:test --tests '*TokenTypeTest*'
```

Expected: FAIL — `isRefreshToken` does not exist.

- [ ] **Step 3: Add the `type` claim and the predicate**

```java
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "ACCESS";
    private static final String TYPE_REFRESH = "REFRESH";
```

Add `claims.put(CLAIM_TYPE, TYPE_ACCESS);` in `generateAccessToken`, and rebuild `generateRefreshToken` to carry `TYPE_REFRESH`:

```java
    public String generateRefreshToken(UserPrincipal userPrincipal) {
        return Jwts.builder()
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .subject(userPrincipal.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .signWith(key)
                .compact();
    }

    public boolean isRefreshToken(String token) {
        try {
            return TYPE_REFRESH.equals(getClaimsFromToken(token).get(CLAIM_TYPE, String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public long getAccessExpirationSeconds() {
        return jwtExpirationMs / 1000;
    }
```

- [ ] **Step 4: Reject a missing role claim instead of defaulting to CUSTOMER**

```java
    public UserRole getRoleFromToken(String token) {
        String roleStr = getClaimsFromToken(token).get("role", String.class);
        if (roleStr == null || roleStr.isBlank()) {
            throw new UnauthorizedException("Token is missing the required 'role' claim");
        }
        return UserRole.valueOf(roleStr);
    }
```

- [ ] **Step 5: Enforce the type in `refreshToken` and return the configured expiry**

In `AuthService.refreshToken`, before issuing anything:

```java
        if (!tokenProvider.isRefreshToken(request.getRefreshToken())) {
            throw new UnauthorizedException("Provided token is not a refresh token");
        }
```

Replace both `.expiresIn(3600)` occurrences (lines 56, 82) with `.expiresIn(tokenProvider.getAccessExpirationSeconds())`.

- [ ] **Step 6: Run the test to confirm it passes**

```bash
cd backend && ./gradlew :shared:security:test --tests '*TokenTypeTest*'
```

Expected: PASS, 2 tests.

- [ ] **Step 7: Verify existing sessions**

Adding the type claim invalidates in-flight refresh tokens issued before deploy — users must re-login once. Note this in the release notes; it is acceptable and expected.

- [ ] **Step 8: Commit**

```bash
git add backend/shared/security backend/auth-service
git commit -m "fix(auth): add refresh token type claim, strict role claim, configured expiresIn"
```

### Task 9.4: Fix the hand-rolled QR PDF export

**Files — Modify:** `backend/qr-service/.../service/QrGeneratorService.java:77-94`

PNG bytes are embedded with `/Filter /DCTDecode` (the JPEG filter), producing a PDF that readers may reject.

- [ ] **Step 1: Check whether a PDF library is already on the classpath**

```bash
cd backend && grep -n "pdf\|openpdf\|itext" build.gradle qr-service/build.gradle 2>/dev/null
```

- [ ] **Step 2: Add OpenPDF and rewrite the export**

```gradle
    implementation 'com.github.librepdf:openpdf:2.0.3'
```

```java
    public byte[] exportPdf(byte[] pngBytes) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();
            Image qr = Image.getInstance(pngBytes);
            qr.scaleToFit(300f, 300f);
            qr.setAlignment(Image.ALIGN_CENTER);
            document.add(qr);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("Failed to generate QR PDF", e);
        }
    }
```

`Image.getInstance` detects PNG and applies the correct filter, which is the actual bug.

- [ ] **Step 3: Verify the output is a valid PDF**

```bash
cd backend && ./gradlew :qr-service:test
```

Add a test asserting the bytes start with `%PDF-` and that a PDF parser can open the result.

- [ ] **Step 4: Commit**

```bash
git add backend/qr-service backend/build.gradle
git commit -m "fix(qr): generate valid PDF via OpenPDF instead of hand-built DCTDecode stream"
```

### Task 9.5: Clean up dependency version pins

**Files — Modify:** `backend/build.gradle:42-44,49-50,78-80`

- [ ] **Step 1: Check which artifacts the BOM already manages**

```bash
cd backend && ./gradlew :auth-service:dependencies --configuration runtimeClasspath | grep -E "jjwt|mapstruct|springdoc|zxing"
```

- [ ] **Step 2: Drop the version only for BOM-managed artifacts; add a comment for the rest**

Per the migration plan: JJWT, MapStruct, springdoc, and ZXing are **not** BOM-managed, so keep explicit pins and document why. Do not remove a pin you have not verified — an unmanaged artifact with no version fails resolution.

- [ ] **Step 3: Commit**

```bash
git add backend/build.gradle
git commit -m "chore(build): document dependency pins, drop BOM-managed versions"
```

---

## Phase 10 — Frontend Verification

Phase 2 (`docs/frontend-optimization-summary.md`) already landed the QR signature pass-through, reactive auth gating, scoped invalidation, route code splitting, and dependency removal. This phase only confirms they hold after the backend contract changes.

### Task 10.1: Verify the frontend against the changed contracts

- [ ] **Step 1: Confirm the previously-fixed items are still in place**

```bash
cd /c/Users/wendafrash.buzuayehu/Downloads/product/blank-canvas
grep -n "signature" src/hooks/useApiData.ts | head
grep -c "React.lazy\|lazy(" src/router/AppRouter.tsx
grep -n "isAuthenticated()" src/hooks/useApiData.ts src/hooks/useLookups.ts
```

Expected: signature forwarded; lazy imports present; **no** non-reactive `isAuthenticated()` call remaining.

- [ ] **Step 2: Typecheck**

```bash
npx tsc --noEmit
```

Expected: exit 0.

- [ ] **Step 3: Build**

```bash
npm run build
```

Expected: success, with route chunks emitted separately.

- [ ] **Step 4: Remove the dead `CustomerMenuView.tsx` tree if still unreferenced**

```bash
grep -rn "CustomerMenuView" src/ --include=*.tsx --include=*.ts
```

If only its own definition appears, delete it and its modal children (`ItemDetailModal`, `CartCheckoutDrawer`, `OrderStatusModal`). Phase 2 left these deliberately; they are in scope now as dead-code cleanup.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "chore(frontend): verify contracts, remove dead customer menu tree"
```

---

## Final Verification

- [ ] **Full backend build (requires a working Gradle host)**

```bash
cd backend && ./gradlew clean build
```

- [ ] **Confirm no secret literal survives anywhere in the repo**

```bash
grep -rniE "(jwt|signature)[-_]?secret:\s*\$\{[A-Z_]+:[^}]" --include=*.java --include=*.yml --include=*.yaml . | grep -v /build/
```

Expected: **no output**.

- [ ] **Confirm every controller with mutations has an authorization annotation**

```bash
cd backend && for f in $(grep -rln "@PostMapping\|@PutMapping\|@DeleteMapping\|@PatchMapping" --include=*Controller.java . | grep -v /build/); do
  grep -q "PreAuthorize" "$f" || echo "MISSING AUTHZ: $f"
done
```

Expected: no `MISSING AUTHZ` lines, except any controller intentionally public (document which).

- [ ] **Smoke test the tenant isolation fixes with two merchants**

Start the stack, create two merchants with separate owners, and confirm: owner A cannot list owner B's users, cannot update B's customer requests, and sees only their own revenue in `/api/analytics/today`. This is the finding class most likely to regress silently — it must be exercised, not just compiled.

- [ ] **Frontend build**

```bash
npx tsc --noEmit && npm run build
```

---

## Self-Review Notes

**Spec coverage:** All 8 "before next deploy" items map to Tasks 1.1–1.2 (secrets), 2.1 (gateway), 3.2 (`@PreAuthorize`), 4.3 (analytics), 2.3 (Kafka), 4.1–4.2 (tenant), 5.1 (WebSocket); the QR-signature item was already completed in Phase 2 and is re-verified in Task 10.1. All 12 "this sprint" items map to Tasks 2.4, 2.2, 6.1, 7.1–7.3, 7.4, 5.2, 8.1, 10.1, 7.5, 8.3, 9.4. Cheap backlog items map to 9.1–9.3, 9.5, 1.3, 7.5, 8.2. Four items are explicitly deferred with rationale above.

**Known gaps in this plan, stated rather than hidden:**
- Tasks 5.2, 7.5, 8.1, and 9.4 contain a discovery step because the exact destination patterns, entity column mappings, frontend call sites, and PDF dependency state could not be fully read while authoring. Each names what to inspect and what to do with the result, rather than guessing at code that would not compile.
- Task 3.2 depends on the `ROLE_` authority prefix check in Step 3. That check gates 12 files; if it is wrong, every annotation must switch to `hasAnyAuthority`.
- No task is compile-verified. See the Verification Constraint section.
