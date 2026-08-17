# Spring Boot 3.x → 4.1.x Migration Plan (Reusable)

> **For:** A developer (or an OpenRewrite-driven automated pass) migrating a different Spring Boot 3.x microservice repo to **Spring Boot 4.1.0 / Spring Cloud 2025.1.2**.
> **Based on:** The real-world breaking changes this monorepo (QRServe) hit during its hand migration.
> **Status of recipe claims:** Each OpenRewrite recipe below was **verified against the actual OpenRewrite `rewrite-spring` recipe source** (GitHub `openrewrite/rewrite-spring`, `main` branch) on 2026-08-16. Anything not verifiable from that source is explicitly marked **"manual step — verify before relying on it"**.

---

## Executive Summary

This document is a step-by-step, dependency-ordered migration playbook for taking a Spring Boot 3.x microservice repo to **Spring Boot 4.1.0** + **Spring Cloud 2025.1.2** (the "Oakwood"/Northfields train). It is built from the exact failure classes encountered in this repo's migration history:

1. **BOM / artifact renames** — `spring-cloud-starter-gateway` → `spring-cloud-gateway-server-webflux`, `spring-boot-starter-web` → `spring-boot-starter-webmvc`, modular starters.
2. **Gateway property re-namespacing** — `spring.cloud.gateway.*` → `spring.cloud.gateway.server.webflux.*` (silently breaks routing with **no startup error** if missed).
3. **Jackson 2 → 3 transition** — Spring Kafka serializers, Spring Data Redis serializers, and any custom `com.fasterxml.jackson.databind.*` imports.
4. **Property prefix changes** — `spring.redis.*` → `spring.data.redis.*`, `server.error.*` → `spring.web.error.*`, `spring.session.redis.*` → `spring.session.data.redis.*`, etc.
5. **STOMP TaskScheduler** — `SimpleBrokerMessageHandler` now *requires* an explicit `TaskScheduler` bean when heartbeat values are configured.
6. **Spring Security** — `authorizeHttpRequests` ordering re-audit; `SecurityConfigurer.init/configure` no longer `throws Exception`.
7. **Framework 7 API moves** — package relocations, `HealthIndicator.getHealth` → `health`, `AuthorizationManager.check` → `authorize`, etc.

**Recommended order:** shared modules → services → gateway LAST (gateway config is the most fragile).

**Target BOM alignment:**
```gradle
mavenBom "org.springframework.boot:spring-boot-dependencies:4.1.0"
mavenBom "org.springframework.cloud:spring-cloud-dependencies:2025.1.2"
```

---

## Pre-migration Checklist

- [ ] **Create a branch/tag of the pre-migration state** (needed for the `./gradlew build` diff step in §7).
- [ ] **Baseline build** — run `./gradlew clean build` on the un-migrated repo and record the warning/error count.
- [ ] **Stand up infrastructure** (PostgreSQL, Redis, Kafka, Eureka) using `docker-compose.yml` so the post-migration smoke test (§9) is runnable.
- [ ] **Inventory direct Jackson 2 imports**: `grep -rn "com.fasterxml.jackson" --include=*.java .` — every hit needs an individual audit (Jackson 3 moved to `tools.jackson.databind.*`).
- [ ] **Inventory `@Value("${...}")` keys** — see §5 for the fail-fast audit strategy recommended via `@ConfigurationProperties` + `@Validated`.
- [ ] **Generate a Code Genome Project token** — as of 2026 OpenRewrite artifacts are distributed through the **Code Genome Project repository** (`https://artifacts.codegenomeproject.org/maven`), which requires authentication (see §8).
- [ ] **Confirm Java 17+ toolchain** — Boot 4.x requires Java 17 minimum; this repo uses Java 17.

---

## Step-by-Step Migration Procedure (ordered by dependency)

### Step 1 — Shared modules first (`shared:common`, `shared:exceptions`, `shared:events`, `shared:security`)

Nothing downstream compiles against these until they're migrated, and they carry the cross-cutting concerns (JWT, exceptions, DTOs) with Jackson/Security surface area.

1. **BOM alignment** in the root `build.gradle`:
   ```gradle
   dependencyManagement {
       imports {
           mavenBom "org.springframework.boot:spring-boot-dependencies:4.1.0"
           mavenBom "org.springframework.cloud:spring-cloud-dependencies:2025.1.2"
       }
   }
   ```
2. **Remove hardcoded versions the BOM now manages.** Specifically:
   - `io.jsonwebtoken:jjwt-api:0.12.6` / `jjwt-impl:0.12.6` / `jjwt-jackson:0.12.6` — drop the version (or confirm the BOM manages a compatible one; JJWT is **not** BOM-managed, so this repo kept a pinned explicit version — **verify**).
   - `org.projectlombok:lombok` / `org.mapstruct:mapstruct:1.6.0` — Lombok is BOM-managed (drop version); MapStruct is **not** BOM-managed (verify against the BOM; if absent, pin explicitly).
   - `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0` — likely **not** BOM-managed; keep explicit, verify 3.x is Boot 4 compatible (this repo uses 3.1.0).
   - `com.google.zxing:core:3.5.3` / `javase:3.5.3` — not BOM-managed; keep explicit.
3. **Jackson 2 → 3 audit in `shared:exceptions` / `shared:security` / `shared:events`:**
   - Any `import com.fasterxml.jackson.databind.*` → migrate to `tools.jackson.databind.*` (Jackson 3). This repo's shared modules do **not** import Jackson directly, so no change was needed — **verify per-repo**.
   - Serializer class renames (Kafka/Redis) are covered by recipes in Step 2/3, but direct Java references to `JsonSerializer`/`JsonDeserializer` in shared code must be re-pointed at `JacksonJsonSerializer`/`JacksonJsonDeserializer`.
4. **Spring Security 7.0 changes in `shared:security`:**
   - `SecurityFilterChain` config methods (`authorizeHttpRequests`, `requestMatchers`, etc.) are unchanged, but **re-audit rule ordering** (first-match-wins hasn't changed — see §6). This repo's `SecurityConfig` had a broad `permitAll()` shadowing narrower rules; a major-version migration is the right time to fix that.
   - If any `SecurityConfigurer.init/configure` implementations exist, they no longer declare `throws Exception` — the `UpgradeSpringSecurity_7_0` recipe handles this automatically.
   - `AuthorizationManager.check` → `authorize` (recipe-covered).
5. **`@Value` fail-fast audit”** — see §5. Since `shared:security` `JwtTokenProvider` reads `jwt.secret` with a **default value**, bootstrap may post-migration use a hardcoded default secret — replace with required config.

### Step 2 — Service modules (auth, merchant, menu, order, qr, notification, analytics)

Apply per-service, in dependency order (they all depend on `shared:*`):

1. **Add the modular-starter dependencies** the Boot 4 design requires:
   - `spring-boot-starter-web` → `spring-boot-starter-webmvc` (rename — recipe `RenameDeprecatedStartersManagedVersions` covers this).
   - `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-websocket`, `spring-boot-starter-actuator`, `spring-boot-starter-data-redis`, `org.springframework.kafka:spring-kafka` → **use `spring-boot-starter-kafka`** (recipe `MigrateToModularStarters` + explicit `ChangeDependency` rename covers `spring-kafka` → `spring-boot-starter-kafka`).
   - `hamcrest`/test slivers: `spring-security-test` → `spring-boot-starter-security-test` (recipe-covered).
2. **Kafka serializers (Jackson 3)** — recipe `org.openrewrite.java.spring.kafka.UpgradeSpringKafka_4_0` automatically rewrites:
   - `org.springframework.kafka.support.serializer.JsonSerializer` → `JacksonJsonSerializer`
   - `org.springframework.kafka.support.serializer.JsonDeserializer` → `JacksonJsonDeserializer`
   - `JsonSerde` → `JacksonJsonSerde`, `DefaultKafkaHeaderMapper` → `JsonKafkaHeaderMapper`, converter renames.
   - **Manual check**: `application.yml` `spring.kafka.producer.value-serializer: org.springframework.kafka.support.serializer.JsonSerializer` and `spring.kafka.consumer.value-deserializer` **must also** be updated to the `JacksonJson*` FQNs (the recipe rewrites Java code and property names, but verify it rewrites the YAML FQCN strings — **verify recipe behavior**).
   - **This repo's bug to avoid**: `merchant-service` `KafkaProducerConfig` had a missing `VALUE_SERIALIZER_CLASS_CONFIG` — ensure both key and value serializers are set after the rename.
3. **Redis serializer (Jackson 3)** — `GenericJackson2JsonRedisSerializer` → `GenericJacksonJsonRedisSerializer`. **Note: no no-arg constructor** — must use `.builder().build()`:
   ```java
   GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder().build();
   ```
   This repo's `notification-service/RedisConfig` does exactly this — **copy that pattern**.
4. **Property prefix changes** — run `org.openrewrite.java.spring.boot4.SpringBootProperties_4_0`, then `SpringBootProperties_4_1` for the 4.1 delta. Confirmed automated property renames include:
   - `spring.redis.*` → `spring.data.redis.*` (already removed in Boot 3 — easy to miss when copy-pasting old configs)
   - `server.error.*` → `spring.web.error.*`
   - `server.servlet.encoding.*` → `spring.servlet.encoding.*`
   - `spring.session.redis.*` → `spring.session.data.redis.*`
   - `spring.dao.exceptiontranslation.enabled` → `spring.persistence.exceptiontranslation.enabled`
   - Jackson datetime/enum feature properties (`spring.jackson.serialization.write-dates-as-timestamps` → `spring.jackson.datatype.datetime.write-dates-as-timestamps`, etc.)
   - Deprecated properties are **commented out** (e.g. `spring.jackson2.*`, SignalFX, Wavefront) rather than silently dropped — important because **Boot 4 no longer fails fast on unknown properties by default**; the comment keeps the value visible for manual review.
   - Logging rolling-policy renames for 4.1 (`logging.file.*` → `logging.logback.rollingpolicy.*`).
5. **JPA / datasource**: `spring.jpa.hibernate.ddl-auto: update` is unchanged in behavior but **strongly consider** moving to Flyway + `validate` before Boot 4 (this repo has it disabled — `flyway.enabled: false`). No breaking change here, but it's a migration-hygiene item.
6. **`@ConfigurationProperties` + `@Validated` audit** — see §5. At minimum, ensure `jwt.secret` and `qr.signature-secret` no longer have hardcoded defaults.

### Step 3 — Gateway (api-gateway) — do LAST

The most fragile portion. Three distinct sub-migrations:

1. **Artifact rename** — recipe `org.openrewrite.java.spring.cloud2025.SpringCloudGatewayDeprecatedModulesAndStarters` handles `spring-cloud-starter-gateway` → `spring-cloud-starter-gateway-server-webflux`. **This repo already uses `spring-cloud-gateway-server-webflux`** (the direct module, not the starter), which is the correct 2025.1 artifact for a reactive gateway — so no rename was needed here. **Verify per-repo**: if using `spring-cloud-starter-gateway` or `spring-cloud-starter-gateway-mvc`, apply the rename.
2. **Property re-namespacing** — recipe `org.openrewrite.java.spring.cloud2025.SpringCloudGatewayProperties` automatically migrates `spring.cloud.gateway.*` → `spring.cloud.gateway.server.webflux.*` (excluding `proxy`, `mvc`, `server` sub-keys, which have their own recipes). **This is the single most dangerous silent-breakage point**: if you miss it, the gateway starts fine but **no route matches** and everything 404s.
   - Routes: `spring.cloud.gateway.routes[*]` → `spring.cloud.gateway.server.webflux.routes[*]`
   - Discovery locator: `spring.cloud.gateway.discovery.locator.*` → `spring.cloud.gateway.server.webflux.discovery.locator.*`
   - Global CORS: `spring.cloud.gateway.globalcors.*` → `spring.cloud.gateway.server.webflux.globalcors.*`
   - **Verified recipe coverage**: YES, the recipe exists and rewrites these keys.
3. **CircuitBreaker wiring** — the circuitbreaker config (`resilience4j.*`) is unchanged, but in this repo it was **configured-but-unused** (no route filters). Post-migration, wire `- name: CircuitBreaker` filters into routes if you want the breaker actually applied — this is a manual step, no recipe.

### Step 4 — Discovery service (discovery-service)

Minimal change. `spring-cloud-starter-netflix-eureka-server` is managed by the 2025.1 BOM. Run the `DependencyUpgrades` recipe to bump Spring Cloud artifacts; no config changes observed in this repo.

### Step 5 — Post-migration build & compile-diff (required)

> **Task requirement:** Actually run `./gradlew build` on the pre-migration branch vs. the migrated state and diff compiler warnings/errors to catch anything not already listed.

1. `git checkout <pre-migration-tag>` and run `./gradlew clean build 2>&1 | tee /tmp/build-before.log`.
2. `git checkout <migrated-branch>` and run `./gradlew clean build 2>&1 | tee /tmp/build-after.log`.
3. `diff /tmp/build-before.log /tmp/build-after.log` — **look for NEW warnings/errors** beyond the expected set:
   - `deprecation` / `forRemoval` warnings on `org.springframework.*` APIs
   - `package ... does not exist` errors (artifact/module renames)
   - `cannot find symbol` (method renames like `getHealth` → `health`, `check` → `authorize`)
   - `NoClassDefFoundError`-style issues surface at **runtime**, not compile time — the container smoke test (§9) is the catch.
4. Keep the recipe output (it logs every transformation) as the authoritative list of changes applied.

---

## OpenRewrite Recipe Reference Table

Verified against the actual `rewrite-spring` recipe source (`github.com/openrewrite/rewrite-spring`, `main`; recipe artifact `org.openrewrite.recipe:rewrite-spring:6.9.0` — current on Maven Central as of 2026-08-16).

| Recipe FQN | What it fixes | Automated vs Manual | Verified? |
|---|---|---|---|
| `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` | Top-level Boot 4.0 migration: chains Boot 3.5 → Boot 4.0 property/API changes, Spring Cloud 2025.1, Framework 7.0, Security 7.0, Batch 5→6, Hibernate 7.1, springdoc 3.0, Kotlin 2.2, Gradle wrapper 8.14+, dependency version upgrades, BOM property renames (`jackson-bom.version` → `jackson-2-bom.version`) | Automated | ✅ (recipe source read) |
| `org.openrewrite.java.spring.boot4.SpringBootProperties_4_0` | Property renames for Boot 4.0 (`server.error.*`→`spring.web.error.*`, Jackson 3 datetime/enum features, `spring.session.redis.*`→`spring.session.data.redis.*`, etc.); comments out deprecated props | Automated | ✅ |
| `org.openrewrite.java.spring.boot4.SpringBootProperties_4_1` | Property renames for Boot 4.1 (`logging.file.*` → `logging.logback.rollingpolicy.*`, etc.) | Automated | ✅ |
| `org.openrewrite.java.spring.boot4.MigrateToModularStarters` | Adds Boot 4 modular starters (`spring-boot-starter-kafka`, `spring-boot-starter-flyway`, `spring-boot-starter-webmvc-test`, etc.); migrates autoconfigure packages (`org.springframework.boot.autoconfigure.orm.jpa` → `org.springframework.boot.hibernate.autoconfigure`, etc.) | Automated | ✅ |
| `org.openrewrite.java.spring.boot4.RenameDeprecatedStartersManagedVersions` | Renames `spring-boot-starter-web` → `spring-boot-starter-webmvc`, oauth2 starters → `*-security-*`, `spring-boot-starter-aop` → `spring-boot-starter-aspectj` (for `io.spring.dependency-management` projects) | Automated | ✅ |
| `org.openrewrite.java.spring.cloud2025.UpgradeSpringCloud_2025_1` | Bumps Spring Cloud artifacts to 5.0.x / `2025.1.x`; `spring-cloud-starter-parent` → `spring-boot-starter-parent` + BOM import | Automated | ✅ |
| `org.openrewrite.java.spring.cloud2025.SpringCloudGatewayDeprecatedModulesAndStarters` | Renames `spring-cloud-starter-gateway` → `spring-cloud-starter-gateway-server-webflux`, `spring-cloud-gateway-server` → `spring-cloud-gateway-server-webflux`, mvc variants | Automated | ✅ |
| `org.openrewrite.java.spring.cloud2025.SpringCloudGatewayProperties` | `spring.cloud.gateway.*` → `spring.cloud.gateway.server.webflux.*` (routes, discovery locator, globalcors) | Automated | ✅ |
| `org.openrewrite.java.spring.cloud2025.SpringCloudGatewayWebfluxProperties` (and WebMvc/Proxy variants) | `spring.cloud.gateway.proxy.*` → `spring.cloud.gateway.proxy-exchange.webflux|webmvc.*` | Automated | ✅ |
| `org.openrewrite.java.spring.framework.UpgradeSpringFramework_7_0` | Framework 7.0: chains 6.2 migration, `UpgradeJackson_2_3`, JUnit 5→6, Kafka 4.0; JSpecify annotation migration | Automated | ✅ |
| `org.openrewrite.java.spring.kafka.UpgradeSpringKafka_4_0` | `JsonSerializer`→`JacksonJsonSerializer`, `JsonDeserializer`→`JacksonJsonDeserializer`, `JsonSerde`→`JacksonJsonSerde`, `DefaultKafkaHeaderMapper`→`JsonKafkaHeaderMapper`, message-converter renames | Automated | ✅ |
| `org.openrewrite.java.spring.security7.UpgradeSpringSecurity_7_0` | Bumps Security to 7.0.x; removes `throws Exception` from `SecurityConfigurer.init/configure`; `AuthorizationManager.check`→`authorize` | Automated | ✅ |
| `org.openrewrite.java.jackson.UpgradeJackson_2_3` | Migrates Jackson 2 → Jackson 3 (package renames) — pulled in via Framework 7.0 | Automated | ✅ (referenced by Framework 7.0 recipe) |
| **Starter `spring-boot-starter-web` → `spring-boot-starter-webmvc` in non-BOM projects** | Fallback rename with explicit version | Automated (safe to re-run) | ✅ |
| **Redis serializer `GenericJackson2JsonRedisSerializer` → `GenericJacksonJsonRedisSerializer` + `.builder().build()`** | No no-arg constructor; must build explicitly | **Manual** | ✅ (issue verified in source; no dedicated recipe found) |
| **`TaskScheduler` bean for STOMP heartbeat** | `SimpleBrokerMessageHandler` requires explicit `TaskScheduler` when heartbeats configured | **Manual** | ✅ (verified in this repo's `StompWebSocketConfig`; no recipe found) |
| **`authorizeHttpRequests` ordering re-audit** | First-match-wins unchanged, but rule ordering needs human review during any major migration | **Manual** | ✅ (semantics verified; no recipe) |
| **`@Value` → `@ConfigurationProperties` + `@Validated`** | Make missing config fail at startup instead of silently defaulting | **Manual** | ✅ |
| **Resilience4j filter wiring into gateway routes** | Circuit breaker config alone doesn't protect routes | **Manual** | ✅ |
| **Direct `com.fasterxml.jackson.databind.*` imports** | UpgradeJackson_2_3 handles many; coexistence cases need manual audit | **Partial (manual review required)** | ✅ |

---

## Post-Migration Verification Checklist

Mapped to the specific failure modes this repo hit:

1. **Build** — `./gradlew clean build` passes on all modules; `build-after.log` diff shows no new flags.
2. **Container startup logs** — boot every service via `docker-compose up`; grep startup logs for:
   - `NoClassDefFoundError` / `NoSuchMethodError` on `org.springframework.*` or `tools.jackson.*` → indicates a missed artifact rename or Jackson 3 reference.
   - `Failed to bind properties under 'spring.cloud.gateway'` → legacy property prefix (shouldn't happen if `SpringCloudGatewayProperties` ran).
   - **Silent routing failure**: gateway starts but every route 404s → the `spring.cloud.gateway.*` → `spring.cloud.gateway.server.webflux.*` re-namespace was missed (recipe should have caught it; re-run and diff).
   - `ClassNotFound: org.springframework.kafka.support.serializer.JsonSerializer` → Kafka serializer rename missed.
   - `NoSuchBeanDefinitionException: TaskScheduler` → STOMP heartbeat TaskScheduler bean missing (manual step).
3. **Security rule smoke test** (map to this repo's known rules):
   - `OPTIONS /api/**` → 200 (CORS preflight permitAll).
   - `GET /api/menu/{merchantId}` → 200 unauthenticated (public menu).
   - `GET /api/auth/me` without token → 401.
   - `POST /api/merchants` without token → 401; with CUSTOMER token → **403** (if you fixed the Phase-1 privilege escalation).
   - `POST /api/v1/tables/{tableId}/requests` with a valid QR signature → 200; with missing/forged signature → 401 (if you made signatures mandatory).
4. **WebSocket connectivity test (stomp)**: connect to `/ws` over SockJS with `?token=<validJWT>`; subscribe to `/topic/merchant/{merchantId}/branch/{branchId}/kitchen`; verify a Kafka `order-created` event arrives within ~10s (heartbeat interval). Confirm **unauthenticated** connect is rejected (missing/invalid JWT → handshake refused).
5. **Kafka round-trip**: place an order; verify `order-service` publishes to `order-created` and `notification-service` consumes + forwards over Redis → STOMP. If the consumer shows `ClassCastException`/`SerializationException`, the JacksonJson serializer/deserializer FQCNs in `application.yml` were not updated.
6. **Redis round-trip**: publish a notification envelope; verify all pods receive it (cross-pod broadcast). If `GenericJacksonJsonRedisSerializer.builder().build()` is not used, expect `NoSuchMethodError` / instantiation failure.
7. **Eureka registry** — all 9 services + api-gateway register with `discovery-service` (`http://localhost:8761`).
8. **Actuator health** — `GET /actuator/health` on every service returns UP; `show-details: when_authorized` behaves (401 without JWT).

---

## Appendix: Real-World Failure Log Signatures (from this repo's migration history)

Use these to pattern-match your own errors during migration:

| # | Category | Failure signature | Root cause | Fix |
|---|---|---|---|---|
| A1 | Gateway routing | `HTTP 404` on every `/api/**` request; gateway starts clean, no errors | `spring.cloud.gateway.*` properties still under old prefix; Boot 4/Cloud 2025 renamed to `spring.cloud.gateway.server.webflux.*` | Run `SpringCloudGatewayProperties`; verify routes in `application.yml` under `server.webflux` |
| A2 | Kafka serialization | `java.lang.ClassNotFoundException: org.springframework.kafka.support.serializer.JsonSerializer` OR `SerializationException: Can't serialize data` | `JsonSerializer`/`JsonDeserializer` removed/renamed in Spring Kafka 4.0; `value-serializer`/`value-deserializer` FQCN still Jackson 2 | Use `JacksonJsonSerializer`/`JacksonJsonDeserializer` (Java + `application.yml`) |
| A3 | Kafka missing config | `Serializer class for value ... not found` / producer sends `BytesSerializer` payload | `VALUE_SERIALIZER_CLASS_CONFIG` never set (this repo's merchant-service bug) | Set both key and value serializer classes |
| A4 | Redis serializer | `java.lang.NoSuchMethodError: GenericJackson2JsonRedisSerializer.<init>()` or `... no-arg constructor` | Boot 4 / Spring Data Redis switched to `GenericJacksonJsonRedisSerializer` (Jackson 3) with no no-arg constructor | Use `GenericJacksonJsonRedisSerializer.builder().build()`; update import |
| A5 | STOMP heartbeat | `java.lang.IllegalArgumentException: The TaskScheduler is required when heartbeats are configured` OR `NoSuchBeanDefinitionException` for `TaskScheduler` | `SimpleBrokerMessageHandler` requires an explicit `TaskScheduler` bean once `setHeartbeatValue` is used | Add `ThreadPoolTaskScheduler` bean and `.setTaskScheduler(...)` in `StompWebSocketConfig` |
| A6 | Starter rename | `java.lang.NoClassDefFoundError: org.springframework.boot.webmvc.servlet.DispatcherServletRegistrationBean` (or similar package move) | Boot 4 modularized autoconfigure packages; `spring-boot-starter-web` not auto-renamed in a hand migration | Ensure `MigrateToModularStarters`/`RenameDeprecatedStarters` ran; `spring-boot-starter-webmvc` |
| A7 | Jackson 3 | `java.lang.NoClassDefFoundError: com/fasterxml/jackson/databind/ObjectMapper` at runtime | Direct code still imports Jackson 2 FQCNs; Boot 4 defaults to Jackson 3 (`tools.jackson`) | Run `UpgradeJackson_2_3`; audit `com.fasterxml.jackson` imports → `tools.jackson` |
| A8 | Security config | `java.lang.Exception` no longer tolerated in `SecurityConfigurer.configure/init` | Spring Security 7 removed `throws Exception` | Run `UpgradeSpringSecurity_7_0`; remove `throws Exception` |
| A9 | Health indicator | `getHealth(..)` doesn't override the interface method | `HealthIndicator.getHealth(boolean)` renamed to `health(boolean)` in Boot 4 | Rename override (recipe `MigrateAutoconfigurePackages` handles the class moves) |
| A10 | Property no-op | Config value silently ignored (e.g. `spring.redis.host` does nothing) | Old prefix `spring.redis.*` no longer bound in Boot 4; unknown props don't fail fast | Run `SpringBootProperties_4_0`; switch to `spring.data.redis.*` |
| A11 | Missing property default | App boots with a well-known default secret (`dGhpcy1pcy1hLXNlY3JldC1rZXkt...` in this repo's JWT config) | `@Value("${jwt.secret:<default>}")` silently defaulted | Replace with `@ConfigurationProperties` + `@Validated`; fail fast if `JWT_SECRET` unset |

---

### Notes on "Verified vs Needs-verification"

- **Verified** = the recipe name and its recipe list were read directly from the `rewrite-spring` YAML source on GitHub `main` (2026-08-16), and/or the artifact version was confirmed on Maven Central (`rewrite-spring:6.9.0`).
- **Manual** = no recipe exists in the catalog for that item; a human must do it (Redis builder pattern, STOMP TaskScheduler, route filter wiring, security-rule ordering re-audit, `@Value` → `@ConfigurationProperties`).
- **"Verify recipe behavior"** flags = the recipe source lists a transformation, but its behavior on YAML property-value FQCN strings (e.g. `spring.kafka.producer.value-serializer`) should be confirmed with a dry-run on your exact repo before relying on it.
- **Version caveat**: `UpgradeSpringBoot_4_0` pins `newVersion: 4.0.x`. This repo targets **4.1.0**. After running the 4.0 recipe, manually bump `4.0.x` → `4.1.x` (and let the BOMs resolve 4.1.0 / 2025.1.2). There is no `UpgradeSpringBoot_4_1` recipe (only `SpringBootProperties_4_1`) as of 2026-08-16 — the 4.1 delta is small.