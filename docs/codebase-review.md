# QRServe Monorepo — Full Codebase Review

**Date:** 2026-08-15
**Scope:** All backend microservices (auth, merchant, menu, order, qr, notification, analytics, api-gateway, discovery, shared modules) + React frontend.
**Method:** Read-only static analysis. No code was modified in this phase.

---

## Security

### [Security] — Broad `permitAll()` on `/api/menu/**` shadows narrower rules and exposes write endpoints
**Severity:** Critical
**Location:** `backend/shared/security/src/main/java/com/qrserve/shared/security/SecurityConfig.java:64-70`
**Issue:** The rule block `"/api/menu/**", "/api/v1/menu/**"` is `permitAll()`. Because Spring Security uses first-match-wins, this makes **every** menu endpoint public — including `POST /api/products`, `PUT /api/products/{id}`, `DELETE /api/products/{id}`, `POST /api/categories`, `PUT/DELETE /api/categories/{id}` (all under `/api/products/**` / `/api/categories/**` are NOT covered, but `/api/menu/**` is). More importantly, the broad `permitAll` on `/api/menu/**` sits **above** the narrower `.authenticated()` rules, so any future narrower rule under `/api/menu/**` would be silently shadowed.
**Recommendation:** Remove `/api/menu/**` and `/api/v1/menu/**` from the broad `permitAll`; permit only the specific public read endpoint `GET /api/menu/{merchantId}` and `GET /api/v1/menu/{merchantId}`, and move all narrow rules above any broad `permitAll`.

### [Security] — Controllers rely on `anyRequest().authenticated()` with no role checks (privilege escalation)
**Severity:** Critical
**Location:** `backend/merchant-service/.../controller/MerchantController.java:24-48`, `BranchController.java:24-34`, `TableController.java:25-48`, `TableAssignmentController.java:25-66`, `WaiterController.java:39-130`, `menu-service/.../ProductController.java:25-56`, `CategoryController.java:25-49`, `order-service/.../OrderController.java:26-50`, `KitchenController.java:22-30`, `qr-service/.../QrController.java:23-47`, `analytics-service/.../AnalyticsController.java:24-46`
**Issue:** None of these controllers use `@PreAuthorize`. The shared `SecurityConfig` only requires *any* authenticated JWT (`.anyRequest().authenticated()`), so a `CUSTOMER` or `WAITER` can create/update/delete merchants, branches, tables, waiters, products, categories, orders, and read all analytics. Only `TableAssignmentV1Controller` and `WaiterTaskV1Controller` have `@PreAuthorize`.
**Recommendation:** Add `@PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER',...)")` to every mutating endpoint and tenant-scope reads; enforce role checks at the controller layer, not just authentication.

### [Security] — Hardcoded default JWT secret in code and config
**Severity:** Critical
**Location:** `backend/shared/security/.../JwtTokenProvider.java:25`, `backend/auth-service/src/main/resources/application.yml:30`, `backend/merchant-service/src/main/resources/application.yml:29`, `backend/menu-service/src/main/resources/application.yml:44`, `backend/order-service/src/main/resources/application.yml:42`, `backend/notification-service/src/main/resources/application.yml:33`, `backend/qr-service/src/main/resources/application.yml:32`, `backend/analytics-service/src/main/resources/application.yml:33`, `backend/docker-compose.yml:34,55,76,99,123,147,168,188`
**Issue:** The JWT secret has a well-known, publicly-visible default (`dGhpcy1pcy1hLXNlY3JldC1rZXkt...`) baked into `@Value` defaults, every `application.yml`, and docker-compose. If `JWT_SECRET` is not overridden in production, anyone can forge valid JWTs for any role (including SUPER_ADMIN).
**Recommendation:** Remove the default secret; fail fast at startup if `JWT_SECRET` is unset (use `@ConfigurationProperties` + `@Validated` or a required `@Value` without default). Rotate the secret in all environments.

### [Security] — Hardcoded default QR signature secret
**Severity:** High
**Location:** `backend/shared/common/.../QrSignatureService.java:29`
**Issue:** `@Value("${qr.signature-secret:qrserve-tamper-proof-signature-secret-change-me}")` — a known default HMAC secret. Anyone can forge valid QR signatures, defeating the tamper-protection on public menu resolution and customer requests.
**Recommendation:** Remove the default; require `qr.signature-secret` to be set via env var. Rotate in production.

### [Security] — QR signature is optional on public endpoints (spoofable customer requests)
**Severity:** High
**Location:** `backend/merchant-service/.../controller/PublicCustomerRequestController.java:53`, `.../service/PublicMenuResolutionService.java:50`
**Issue:** The QR signature is only validated "IF provided" (`signature != null && !signature.isBlank()`). Any caller can POST a customer request or resolve a menu without a valid signature, enabling spam/abuse and bypassing the intended QR provenance check.
**Recommendation:** Make the signature mandatory in production (or gate via a config flag `qr.signature-required: true`), and reject requests without a valid signature.

### [Security] — End-user JWT forwarded to downstream services instead of service identity
**Severity:** High
**Location:** `backend/order-service/.../OrderService.java:230-242`, `.../KitchenService.java:110-122`, `backend/analytics-service/.../AnalyticsService.java:147-159`, `backend/qr-service/.../QrGeneratorService.java:147-159`
**Issue:** `getAuthHeaders()` copies the inbound end-user `Authorization` header and forwards it to merchant-service/menu-service/order-service. If the end-user token is absent (e.g. a public order placement), the downstream call goes unauthenticated; if present, the downstream service trusts a possibly-stale/expired end-user token. There is no service-to-service identity (client-credentials / service JWT).
**Recommendation:** Use a dedicated service identity (e.g. a service account JWT or mTLS) for inter-service calls; never forward the end-user token. At minimum, add a service-level auth filter on inter-service endpoints.

### [Security] — Tenant-scoping bypass in `listUsers`
**Severity:** High
**Location:** `backend/auth-service/.../controller/AuthController.java:68-76`
**Issue:** `UUID scope = principal != null && principal.getMerchantId() != null ? principal.getMerchantId() : merchantId;` — a MERCHANT_OWNER/BRANCH_MANAGER whose JWT has a null `merchantId` falls through to the caller-supplied `merchantId` query param, letting them list any merchant's users. Also `AuthService.listUsers(null)` calls `findAll()` (all users) for any caller.
**Recommendation:** Always scope non-SUPER_ADMIN callers to their own `merchantId` from the JWT; reject if absent. Only SUPER_ADMIN may pass an arbitrary `merchantId` or list all.

### [Security] — Privilege escalation: any MERCHANT_OWNER can create a SUPER_ADMIN
**Severity:** High
**Location:** `backend/auth-service/.../controller/AuthController.java:51-63`, `.../dto/CreateUserRequest.java:30-33`
**Issue:** `POST /api/auth/users` is `@PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER')")` and `CreateUserRequest.role` is caller-controlled with no check that the created role is lower than the caller's. A MERCHANT_OWNER can create a SUPER_ADMIN.
**Recommendation:** Enforce role hierarchy in `AuthService.createUser` — only SUPER_ADMIN may create SUPER_ADMIN/MERCHANT_OWNER; MERCHANT_OWNER may only create subordinate roles and must be forced to their own merchantId.

### [Security] — Tenant-isolation bypass in `updateRequestStatus` (merchantId optional)
**Severity:** High
**Location:** `backend/merchant-service/.../service/CustomerRequestService.java:70-76`
**Issue:** `if (merchantId != null && !merchantId.equals(request.getMerchantId()))` — the tenant check is skipped when `merchantId` is null. `CustomerRequestController.updateStatus` passes an optional `merchantId`, so any authenticated user can update any request by omitting the param.
**Recommendation:** Make `merchantId` mandatory and always enforce the tenant check; derive it from the JWT principal rather than the request.

### [Security] — `assignWaiterToTable` does not validate branchId against the table
**Severity:** Medium
**Location:** `backend/merchant-service/.../service/TableAssignmentService.java:31-46`
**Issue:** The method validates waiter and table belong to the same `merchantId`, but the `branchId` argument is taken from the caller and never checked against `table.getBranchId()`. A caller can create an assignment with a mismatched branch.
**Recommendation:** Validate `branchId.equals(table.getBranchId())` and `branchId.equals(waiter.getBranchId())`.

### [Security] — STOMP topic authorization gap (any authenticated user can subscribe to any topic)
**Severity:** High
**Location:** `backend/notification-service/.../interceptor/StompAuthInterceptor.java:54-60`
**Issue:** `StompAuthInterceptor` only checks that a principal exists on SUBSCRIBE/SEND/ACK frames; it does not verify the user is authorized for the specific `/topic/merchant/{merchantId}/...` destination. Any authenticated user can subscribe to another merchant's kitchen/waiter topics or another customer's order topic.
**Recommendation:** Add destination-level authorization in the interceptor (parse the topic, compare merchantId/branchId/orderId against the principal's claims).

### [Security] — Unauthenticated raw WebSocket handler still registered alongside STOMP
**Severity:** High
**Location:** `backend/notification-service/.../config/WebSocketConfig.java:18-21`
**Issue:** `WebSocketConfig` registers `/ws/orders` with `OrderWebSocketHandler` and `setAllowedOrigins("*")` with **no authentication**. This legacy raw handler coexists with the STOMP `/ws` endpoint and is fully unauthenticated.
**Recommendation:** Remove `WebSocketConfig` and `OrderWebSocketHandler` (dead code — STOMP replaced it); if kept, add the JWT handshake interceptor.

### [Security] — Catch-all exception handler leaks internal messages
**Severity:** Medium
**Location:** `backend/shared/exceptions/.../GlobalExceptionHandler.java:80-88`
**Issue:** `handleGeneralException` returns `ex.getMessage()` verbatim to clients, exposing internal details (SQL, stack hints, downstream URLs).
**Recommendation:** Return a generic message for unhandled exceptions and log the real cause server-side.

### [Security] — `getRoleFromToken` silently defaults to CUSTOMER on missing role
**Severity:** Medium
**Location:** `backend/shared/security/.../JwtTokenProvider.java:100-104`
**Issue:** If the `role` claim is missing, the token is treated as CUSTOMER. Combined with the hardcoded secret, a forged token without a role claim gets the lowest privilege — but a forged token *with* a role claim gets full privilege. The default masks token-issuance bugs.
**Recommendation:** Reject tokens missing required claims rather than defaulting.

### [Security] — Refresh token is not distinguished from access token
**Severity:** Medium
**Location:** `backend/auth-service/.../service/AuthService.java:60-84`, `backend/shared/security/.../JwtTokenProvider.java:61-68`
**Issue:** `generateRefreshToken` produces a JWT with no `type` claim and the same signing key as the access token. `refreshToken()` accepts any valid token (including an access token) as a refresh token, and there is no refresh-token revocation/rotation store.
**Recommendation:** Add a `type: REFRESH` claim, validate it in `refreshToken()`, and implement refresh-token rotation/revocation (e.g. Redis allowlist).

### [Security] — `expiresIn` hardcoded instead of using configured expiry
**Severity:** Low
**Location:** `backend/auth-service/.../service/AuthService.java:56,82`
**Issue:** `expiresIn(3600)` is hardcoded, ignoring `jwt.access-expiration-ms`. The frontend uses this value for token lifetime decisions.
**Recommendation:** Return the configured `jwt.access-expiration-ms / 1000`.

---

## Data & Persistence

### [Data] — N+1 query in `getFullMenu`
**Severity:** High
**Location:** `backend/menu-service/.../service/MenuService.java:137-159`
**Issue:** For each category, `productRepository.findByCategoryId(cat.getId())` runs a separate query (N+1). With many categories this is slow.
**Recommendation:** Fetch all products for the merchant in one query (`findByMerchantId`) and group in memory, or use a `JOIN FETCH`/`@EntityGraph`.

### [Data] — N+1 query + per-row REST call in `getKitchenOrders`
**Severity:** High
**Location:** `backend/order-service/.../service/KitchenService.java:56-85`
**Issue:** For each order, `fetchTableNumber()` makes a REST call to merchant-service AND `orderItemRepository.findByOrderId()` runs a query (N+1 on both).
**Recommendation:** Batch-fetch table numbers (one REST call for all tableIds) and batch-fetch order items (`findByOrderIdIn`).

### [Data] — N+1 query in `WaiterTaskV1Controller.getTasks`
**Severity:** Medium
**Location:** `backend/merchant-service/.../controller/WaiterTaskV1Controller.java:66`
**Issue:** `tableRepository.findById(a.getTableId())` inside the assignment loop (N+1).
**Recommendation:** Collect tableIds and batch-fetch with `findAllById`.

### [Data] — `ddl-auto: update` in every service; Flyway disabled
**Severity:** High
**Location:** `backend/auth-service/src/main/resources/application.yml:20,27`, `merchant-service:20,26`, `menu-service:20,26`, `order-service:20,26`, `notification-service:24,30`, `qr-service:20,26`, `analytics-service:20,26`
**Issue:** All services use `spring.jpa.hibernate.ddl-auto: ${DDL_AUTO:update}` and `spring.flyway.enabled: false`. Schema is auto-mutated at runtime — unsafe for production (no versioned migrations, risk of destructive changes).
**Recommendation:** Enable Flyway, create `V1__init.sql` migrations per service, and set `ddl-auto: validate` in production.

### [Data] — Missing indexes on frequently-filtered columns
**Severity:** Medium
**Location:** `backend/order-service/.../repository/OrderRepository.java:12-16`, `backend/merchant-service/.../repository/CustomerRequestRepository.java:12-16`, `TableAssignmentRepository.java:13-18`, `WaiterRepository.java:13-20`
**Issue:** Frequent `WHERE` on `merchant_id`, `branch_id`, `status`, `table_id`, `waiter_id` columns have no explicit indexes (only PK/unique). As data grows, these queries degrade.
**Recommendation:** Add composite indexes (e.g. `(branch_id, status)`, `(merchant_id, status)`, `(table_id, status)`, `(waiter_id, status)`) via Flyway migrations.

### [Data] — External HTTP calls inside `@Transactional` (partial-failure risk)
**Severity:** High
**Location:** `backend/order-service/.../service/OrderService.java:53-128`
**Issue:** `createOrder` is `@Transactional` and performs `fetchTable` (REST), `fetchProduct` (REST per item), `updateTableStatus` (REST), and Kafka publish inside the DB transaction. A downstream failure after `orderRepository.save` can leave the DB transaction open/rolled back while side effects (table status) already happened, or vice-versa.
**Recommendation:** Keep the DB transaction scoped to persistence only; perform REST/Kafka calls after commit (e.g. `@TransactionalEventListener(AFTER_COMMIT)` or outbox pattern).

### [Data] — `updateTableStatus` swallows failures (table left OCCUPIED)
**Severity:** Medium
**Location:** `backend/order-service/.../service/OrderService.java:217-225`
**Issue:** `updateTableStatus` catches all exceptions and only logs a warning. If the table-status update fails, the order is created but the table stays AVAILABLE (or stays OCCUPIED after delivery).
**Recommendation:** Retry with backoff or publish a compensating event; at minimum surface the failure to monitoring.

### [Data] — `getTodayMetrics` ignores `merchantId` (cross-tenant data leak)
**Severity:** Critical
**Location:** `backend/analytics-service/.../service/AnalyticsService.java:40-73`
**Issue:** `getTodayMetrics(UUID merchantId)` fetches **all** orders (`fetchOrders()`) and **all** tables (`fetchTables()`) regardless of `merchantId`. Every merchant sees global revenue/occupancy.
**Recommendation:** Pass `merchantId` to the downstream order/table queries and filter server-side.

---

## Configuration Correctness

### [Config] — Resilience4j circuit breaker configured but never applied to routes
**Severity:** High
**Location:** `backend/api-gateway/src/main/resources/application.yml:89-95`
**Issue:** `resilience4j.circuitbreaker.instances.default` is defined, but no route uses a `CircuitBreaker` filter. The config is dead — no route is protected.
**Recommendation:** Add `- name: CircuitBreaker` filter with `args.name: default` to each route, or remove the dead config.

### [Config] — Gateway route ordering: `/api/v1/**` on merchant-service shadows `/api/v1/auth/**`
**Severity:** Critical
**Location:** `backend/api-gateway/src/main/resources/application.yml:31-40`
**Issue:** The merchant-service route predicate includes `Path=/api/v1/**` (line 40). The auth-service route only matches `/api/auth/**` (line 34), not `/api/v1/auth/**`. Since the merchant route is declared after auth but matches `/api/v1/**`, any `/api/v1/auth/**` request is routed to merchant-service, which has no such controller → 404. The frontend calls `/auth/refresh` (not `/v1/auth/refresh`), but the SecurityConfig permits `/api/v1/auth/**` — inconsistent.
**Recommendation:** Add an explicit auth route for `/api/v1/auth/**` before the merchant `/api/v1/**` route, or remove `/api/v1/**` from the merchant route and enumerate specific v1 paths.

### [Config] — Deprecated Jackson 2 Kafka serializers in Boot 4.1
**Severity:** High
**Location:** `backend/order-service/.../config/KafkaProducerConfig.java:11,27`, `backend/order-service/src/main/resources/application.yml:31`, `backend/notification-service/.../config/KafkaConsumerConfig.java:11,27`
**Issue:** Uses `org.springframework.kafka.support.serializer.JsonSerializer` / `JacksonJsonDeserializer` (Jackson 2-based). Spring Boot 4.1 / Spring Kafka 3.x moved to Jackson 3 (`JacksonJsonSerializer`/`JacksonJsonDeserializer` in `org.springframework.kafka.support.serializer`). These may be deprecated/removed.
**Recommendation:** Migrate to `JacksonJsonSerializer`/`JacksonJsonDeserializer` (Jackson 3) and verify `TRUSTED_PACKAGES`/`USE_TYPE_INFO_HEADERS` property names.

### [Config] — merchant-service Kafka producer missing VALUE_SERIALIZER
**Severity:** Critical
**Location:** `backend/merchant-service/.../config/KafkaProducerConfig.java:27-34`
**Issue:** The producer factory sets only `KEY_SERIALIZER_CLASS_CONFIG` (StringSerializer). `VALUE_SERIALIZER_CLASS_CONFIG` is never set, so the default `BytesSerializer` is used and `KafkaTemplate<String, Object>` will fail to serialize event objects at runtime.
**Recommendation:** Set `VALUE_SERIALIZER_CLASS_CONFIG` to `JacksonJsonSerializer.class` (Jackson 3) — matching order-service.

### [Config] — Orphaned `@Value` keys / env vars not read
**Severity:** Medium
**Location:** `backend/docker-compose.yml:100-102,124,169-170` vs `backend/order-service/src/main/resources/application.yml:37-39`, `backend/analytics-service/src/main/resources/application.yml:28-30`
**Issue:** `MERCHANT_SERVICE_URL`/`MENU_SERVICE_URL`/`ORDER_SERVICE_URL` are set in Compose and read via `services.*-url` — consistent. However `REDIS_HOST`/`REDIS_PORT` are set for menu-service/notification-service/api-gateway but `notification-service` reads them via `spring.data.redis.*` (correct) while `menu-service` also uses `spring.data.redis.*` (correct). No orphan found here, but `qr.signature-secret` is referenced in code with no env var set anywhere in Compose — it silently uses the hardcoded default.
**Recommendation:** Add `QR_SIGNATURE_SECRET` to Compose and all services; audit all `@Value` defaults for production safety.

### [Config] — Hardcoded dependency versions that the BOM already manages
**Severity:** Low
**Location:** `backend/build.gradle:42-44,49-50,78-80`
**Issue:** `jjwt-api/impl/jackson:0.12.6`, `mapstruct:1.6.0`, `springdoc-openapi-starter-webmvc-ui:3.1.0`, `zxing:3.5.3` are hardcoded. `spring-boot-dependencies` BOM manages many of these (e.g. springdoc, zxing may not be BOM-managed, but jjwt/mapstruct versions should be verified against the BOM to avoid conflicts).
**Recommendation:** Remove versions for artifacts the BOM manages; keep explicit versions only for non-BOM artifacts and document why.

### [Config] — `management.tracing.sampling.probability: 1.0` in all services
**Severity:** Low
**Location:** All `application.yml` files (e.g. `backend/auth-service/src/main/resources/application.yml:44`)
**Issue:** 100% trace sampling in production is expensive.
**Recommendation:** Lower to 0.1 in production via env var.

### [Config] — Gateway debug logging at TRACE/DEBUG in default config
**Severity:** Low
**Location:** `backend/api-gateway/src/main/resources/application.yml:112-114`
**Issue:** `org.springframework.cloud.gateway: TRACE`, `org.springframework.security: TRACE`, `reactor.netty: DEBUG` are on by default — noisy and a minor info-leak risk.
**Recommendation:** Gate behind a profile/env flag.

---

## Messaging & Realtime

### [Messaging] — STOMP heartbeat TaskScheduler correctly wired (no issue)
**Severity:** Info
**Location:** `backend/notification-service/.../config/StompWebSocketConfig.java:54-71`
**Issue:** `SimpleBrokerMessageHandler` requires an explicit `TaskScheduler` when heartbeats are configured; `heartBeatTaskScheduler()` bean is present and wired via `.setTaskScheduler(...)`. This is correct for Boot 4.1.
**Recommendation:** None — keep as-is. (Documented as a required pattern for the migration plan.)

### [Messaging] — Redis serializer correctly uses Jackson 3 builder (no issue)
**Severity:** Info
**Location:** `backend/notification-service/.../config/RedisConfig.java:51-52`
**Issue:** Uses `GenericJacksonJsonRedisSerializer.builder().build()` (Jackson 3, no no-arg constructor) — correct for Boot 4.1.
**Recommendation:** None.

### [Messaging] — Frontend STOMP client re-reads token on reconnect (no stale-closure bug)
**Severity:** Info
**Location:** `src/lib/realtime.ts:74-81`
**Issue:** `webSocketFactory` and `beforeConnect` both call `getAuthToken()` fresh on every (re)connect, so a rotated token is used. `disconnect()` clears subscriptions/handlers. No stale-token bug.
**Recommendation:** None.

### [Messaging] — `useCreateTableRequest` drops the QR signature
**Severity:** High
**Location:** `src/hooks/useApiData.ts:533-557`
**Issue:** The mutation destructures `signature` but never passes it to `customerRequestApi.createRequest`. `CustomerMenuPage.tsx:90` passes `signature`, but it is silently discarded — the backend never receives it, so the (optional) signature check is never exercised from the customer flow.
**Recommendation:** Pass `signature` through to `customerRequestApi.createRequest` (and add it to the request body/query).

### [Messaging] — Legacy raw WebSocket handler + NotificationService are dead code
**Severity:** Medium
**Location:** `backend/notification-service/.../handler/OrderWebSocketHandler.java`, `.../service/NotificationService.java`, `.../config/WebSocketConfig.java`
**Issue:** STOMP replaced the raw handler, but `OrderWebSocketHandler`, `NotificationService`, and `WebSocketConfig` remain and are still registered (unauthenticated `/ws/orders`).
**Recommendation:** Remove the legacy handler/config/service or migrate `NotificationService` to use `SimpMessagingTemplate`.

---

## API Contract Consistency

### [API] — Phantom endpoint: analytics calls `GET /api/tables` which does not exist
**Severity:** Critical
**Location:** `backend/analytics-service/.../service/AnalyticsService.java:132` vs `backend/merchant-service/.../controller/TableController.java:31-35`
**Issue:** `fetchTables()` calls `merchantServiceUrl + "/api/tables"`, but `TableController` exposes `GET /api/tables/all` for listing all tables (and `GET /api/tables/{id}`). `GET /api/tables` has no mapping → 404, caught and swallowed → analytics always shows 0 tables.
**Recommendation:** Change the URL to `/api/tables/all` (or add a `GET /api/tables` mapping).

### [API] — Phantom endpoints: frontend calls `PUT/DELETE /api/branches/{id}` and `PUT/DELETE /api/tables/{id}` with no backend mapping
**Severity:** High
**Location:** `src/lib/api.ts:438-447,476-491` vs `backend/merchant-service/.../controller/BranchController.java`, `TableController.java`
**Issue:** `branchApi.updateBranch`/`deleteBranch` call `PUT/DELETE /api/branches/{id}`; `tableApi.updateTable`/`deleteTable` call `PUT/DELETE /api/tables/{id}`. `BranchController` only has `POST` and `GET /merchant/{merchantId}`; `TableController` only has `POST`, `GET /all`, `GET /{id}`, `PATCH /{id}/status`. These frontend calls are phantom (404).
**Recommendation:** Either add the backend mappings or remove the unused frontend methods.

### [API] — Phantom endpoint: frontend `deleteMerchant` calls `DELETE /api/merchants/{id}` with no backend mapping
**Severity:** Medium
**Location:** `src/lib/api.ts:412-415` vs `backend/merchant-service/.../controller/MerchantController.java`
**Issue:** `merchantApi.deleteMerchant` calls `DELETE /api/merchants/{id}`; `MerchantController` has no DELETE mapping.
**Recommendation:** Add the mapping or remove the frontend method.

### [API] — Phantom endpoint: frontend `getWaiters` sends `merchantId` param the backend ignores
**Severity:** Medium
**Location:** `src/lib/api.ts:707-713` vs `backend/merchant-service/.../controller/WaiterController.java:52-104`
**Issue:** `waiterApi.getWaiters` sends `?merchantId=...`, but `WaiterController.getWaiters` only reads `branchId` and derives merchant from the JWT. The `merchantId` param is ignored (harmless but misleading).
**Recommendation:** Remove the unused param or honor it for SUPER_ADMIN.

### [API] — `/api/analytics/orders` returns today's metrics, not order analytics
**Severity:** Medium
**Location:** `backend/analytics-service/.../controller/AnalyticsController.java:42-46`
**Issue:** `getOrdersAnalytics` calls `analyticsService.getTodayMetrics(merchantId)` — the endpoint is mislabeled and returns the same payload as `/today`.
**Recommendation:** Implement a dedicated order-analytics method or remove the endpoint.

### [API] — DTO shape mismatch: `CreateOrderRequest` frontend omits `customerName`/`note` in some flows
**Severity:** Low
**Location:** `src/pages/CustomerMenuPage.tsx:67-71` vs `backend/order-service/.../dto/CreateOrderRequest.java`
**Issue:** `placeOrder` sends only `{ tableId, items }`; `customerName`/`note` are optional so this works, but the backend defaults `customerName` to "Guest". Acceptable, but the frontend `CreateOrderRequest` type includes them — inconsistent usage.
**Recommendation:** Align the frontend payload with the intended UX (send customerName when known).

### [API] — Inconsistent `/api/**` vs `/api/v1/**` versioning
**Severity:** Medium
**Location:** `backend/merchant-service/.../controller/` (both `TableAssignmentController` and `TableAssignmentV1Controller`, `WaiterController` and `WaiterTaskV1Controller`)
**Issue:** Duplicate/unversioned and versioned controllers coexist for the same domain (e.g. `POST /api/table-assignments` vs `POST /api/v1/tables/{tableId}/assign-waiter`). The frontend uses both `tableAssignmentApi` and `waiterTaskApi.assignWaiter` for the same intent — duplicate logic and inconsistent paths.
**Recommendation:** Consolidate on the v1 contract; remove the unversioned duplicates or mark them deprecated.

---

## Error Handling & Resilience

### [Error] — Downstream failures masked as "not found"
**Severity:** High
**Location:** `backend/order-service/.../service/OrderService.java:186-189,211-214`, `backend/qr-service/.../service/QrGeneratorService.java:114-117,137-140`
**Issue:** `fetchTable`/`fetchProduct`/`fetchMerchant` catch **all** exceptions (including connection refused, timeout, 500) and rethrow `ResourceNotFoundException("... not found")`. A downstream outage is reported to the client as "Table not found", hiding the real cause.
**Recommendation:** Distinguish 404 (genuine not-found) from transport/5xx errors; rethrow a `ServiceUnavailableException`/`BusinessException` with the real cause for non-404 failures.

### [Error] — Analytics silently returns empty data on downstream failure
**Severity:** Medium
**Location:** `backend/analytics-service/.../service/AnalyticsService.java:124-127,138-141`
**Issue:** `fetchOrders`/`fetchTables` catch all exceptions and return `List.of()`, so a downstream outage silently produces "0 revenue / 0 tables" dashboards with no error surfaced.
**Recommendation:** Surface partial-failure state (e.g. return a degraded flag or throw a 503) rather than silently returning zeros.

### [Error] — `updateTableStatus` swallows failures
**Severity:** Medium
**Location:** `backend/order-service/.../service/OrderService.java:217-225`
**Issue:** See Data section — table status drift is silent.
**Recommendation:** Retry/compensate and alert.

### [Error] — Resilience4j not applied to gateway routes
**Severity:** High
**Location:** `backend/api-gateway/src/main/resources/application.yml:89-95`
**Issue:** See Config section — circuit breaker configured but unused, so no route has retry/circuit-breaker protection.
**Recommendation:** Wire `CircuitBreaker`/`Retry` filters into routes.

---

## General Code Quality

### [Quality] — Dead code: `JwtAuthenticationFilter.getJwtFromRequest` duplicates `parseJwt`
**Severity:** Low
**Location:** `backend/shared/security/.../JwtAuthenticationFilter.java:76-82`
**Issue:** `getJwtFromRequest` is never called and duplicates `parseJwt`.
**Recommendation:** Remove it.

### [Quality] — Duplicate imports in `WaiterController`
**Severity:** Low
**Location:** `backend/merchant-service/.../controller/WaiterController.java:16,21,23`
**Issue:** `GrantedAuthority`, `HttpServletRequest` imported twice; unused imports (`UsernameNotFoundException`, `AccessDeniedException`, `Authentication`).
**Recommendation:** Clean up imports.

### [Quality] — Manual JWT parsing in `WaiterController.getWaiters` instead of `@AuthenticationPrincipal`
**Severity:** Medium
**Location:** `backend/merchant-service/.../controller/WaiterController.java:58-60,132-147`
**Issue:** Re-parses the JWT from the raw header rather than using the authenticated `UserPrincipal`; inconsistent with the rest of the codebase and re-validates the token manually.
**Recommendation:** Use `@AuthenticationPrincipal UserPrincipal` and derive role/merchantId from it.

### [Quality] — Hardcoded mock data in analytics
**Severity:** Medium
**Location:** `backend/analytics-service/.../service/AnalyticsService.java:77-82,90-114`
**Issue:** `getRevenueAnalytics` returns hardcoded `DailySalesPoint` history; `getPopularItems` returns hardcoded items. Not real analytics.
**Recommendation:** Implement real aggregation (from order-service events/DB) or clearly mark as placeholder.

### [Quality] — Hand-rolled PDF generation in QR service
**Severity:** Medium
**Location:** `backend/qr-service/.../service/QrGeneratorService.java:77-94`
**Issue:** `exportPdf` builds a minimal PDF by string concatenation with a raw PNG embedded as DCTDecode (JPEG filter) — the PNG bytes are labeled `/Filter /DCTDecode` which is incorrect for PNG data; the PDF may be corrupt/unreadable.
**Recommendation:** Use a proper PDF library (e.g. OpenPDF/iText) or embed the PNG with the correct `/FlateDecode` filter.

### [Quality] — Unused frontend dependencies
**Severity:** Low
**Location:** `package.json:14,21,23,24`
**Issue:** `@google/genai`, `axios` (code uses `fetch`), `express`, `dotenv` appear unused in the Vite app.
**Recommendation:** Remove unused deps to reduce install size; move `@types/canvas-confetti` to devDependencies.

### [Quality] — No route-level code splitting (all pages statically imported)
**Severity:** Medium
**Location:** `src/router/AppRouter.tsx:3-22`
**Issue:** Every page is statically imported, so the initial bundle includes all admin/merchant/kitchen/waiter pages.
**Recommendation:** Use `React.lazy` + `Suspense` for route-level code splitting.

### [Quality] — `enabled: isAuthenticated()` does not react to token state changes
**Severity:** Medium
**Location:** `src/hooks/useApiData.ts:53,63,105,147,155,207,249,333,342,375,384,393,464`
**Issue:** `isAuthenticated()` reads a module-level `authToken` variable that is not reactive. Queries gated with `enabled: isAuthenticated()` will not re-enable when the user logs in (the component must remount). The `AuthContext` has a reactive `authState`, but the hooks don't use it.
**Recommendation:** Gate queries on the reactive `useAuth().isAuthenticated` (or a reactive token selector) instead of the non-reactive `isAuthenticated()`.

### [Quality] — Broad query invalidation over-refetches unrelated views
**Severity:** Medium
**Location:** `src/hooks/useApiData.ts:270,280,290,301,311`
**Issue:** `useUpdateCategory`/`useDeleteCategory`/`useCreateProduct`/`useUpdateProduct`/`useDeleteProduct` invalidate `['menu']` (all menu queries) rather than the specific `['menu', merchantId]` key.
**Recommendation:** Invalidate the specific `['menu', merchantId]` key (and `['menu', categoryId]`/`['products', ...]` as needed).

### [Quality] — `useCreateTableRequest` ignores `signature` (see Messaging)
**Severity:** High
**Location:** `src/hooks/useApiData.ts:548`
**Issue:** Signature dropped.
**Recommendation:** Pass through.

### [Quality] — Missing input validation on public-facing endpoints
**Severity:** Medium
**Location:** `backend/merchant-service/.../controller/TableController.java:45` (`@RequestBody Map<String,String>` with no validation), `backend/order-service/.../controller/OrderController.java:44-50` (status not validated against enum)
**Issue:** `updateTableStatus` accepts any string; `updateOrderStatus` accepts any status string (no enum validation) — invalid states can be persisted.
**Recommendation:** Validate status against allowed enums (`@Pattern` or enum binding).

---

## Prioritized Optimization / Recommendation Plan

### Do before next deploy
- [ ] Remove hardcoded default JWT secret and QR signature secret; fail fast if unset; rotate secrets.
- [ ] Fix gateway route ordering so `/api/v1/auth/**` routes to auth-service (not merchant-service).
- [ ] Add `@PreAuthorize` role checks to all merchant/menu/order/qr/analytics controllers (privilege escalation).
- [ ] Fix analytics `fetchTables` phantom URL (`/api/tables` → `/api/tables/all`) and enforce `merchantId` tenant scoping in `getTodayMetrics`.
- [ ] Fix merchant-service Kafka producer missing `VALUE_SERIALIZER_CLASS_CONFIG` (events currently fail to serialize).
- [ ] Fix `useCreateTableRequest` dropping the QR signature.
- [ ] Fix tenant-isolation bypasses: `AuthController.listUsers`, `CustomerRequestService.updateRequestStatus`, `TableAssignmentService.assignWaiterToTable` branchId.
- [ ] Remove unauthenticated raw WebSocket handler (`/ws/orders`) or add auth.

### Do this sprint
- [ ] Migrate Kafka serializers to Jackson 3 (`JacksonJsonSerializer`/`JacksonJsonDeserializer`).
- [ ] Wire Resilience4j `CircuitBreaker`/`Retry` filters into gateway routes (currently dead config).
- [ ] Fix error masking in OrderService/QrGeneratorService (distinguish 404 from 5xx).
- [ ] Fix N+1 queries in `MenuService.getFullMenu`, `KitchenService.getKitchenOrders`, `WaiterTaskV1Controller.getTasks`.
- [ ] Move external HTTP/Kafka calls out of `@Transactional` in `OrderService.createOrder`.
- [ ] Add STOMP destination-level authorization in `StompAuthInterceptor`.
- [ ] Fix phantom frontend endpoints (`PUT/DELETE /api/branches/{id}`, `PUT/DELETE /api/tables/{id}`, `DELETE /api/merchants/{id}`) — add backend mappings or remove frontend methods.
- [ ] Make `enabled: isAuthenticated()` reactive (use `useAuth().isAuthenticated`).
- [ ] Scope query invalidation to specific keys (`['menu', merchantId]`).
- [ ] Enable Flyway; replace `ddl-auto: update` with versioned migrations + `validate`.
- [ ] Fix `/api/analytics/orders` returning today's metrics; implement real analytics or remove.
- [ ] Fix QR PDF generation (incorrect DCTDecode filter for PNG).

### Backlog / nice-to-have
- [ ] Implement service-to-service identity (client-credentials) instead of forwarding end-user JWT.
- [ ] Add refresh-token rotation/revocation and `type` claim.
- [ ] Add missing DB indexes via Flyway.
- [ ] Route-level code splitting (`React.lazy`) in `AppRouter`.
- [ ] Remove dead code: `JwtAuthenticationFilter.getJwtFromRequest`, `NotificationService`/`OrderWebSocketHandler`/`WebSocketConfig`, duplicate imports in `WaiterController`.
- [ ] Remove unused frontend deps (`axios`, `@google/genai`, `express`, `dotenv`).
- [ ] Lower trace sampling to 0.1 in production; gate gateway TRACE/DEBUG logging behind a profile.
- [ ] Consolidate `/api/**` vs `/api/v1/**` duplicate controllers.
- [ ] Add input validation (status enums) on `updateTableStatus`/`updateOrderStatus`.
- [ ] Return configured `expiresIn` instead of hardcoded 3600.