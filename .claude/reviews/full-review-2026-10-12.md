# Full Project Review — QRServe (main @ fa0199c)

**Reviewed**: 2026-10-12
**Scope**: whole repo (backend services + shared, React frontend, infra/CI/k8s/config)
**Decision**: BLOCK — critical auth and tenant-isolation holes reachable in the deployed `site-bp` overlay

## Summary
Builds and unit tests are green, but production can currently be taken over three independent ways
(seeded default super-admin, dev-fake Super App login enabled in k8s, cross-tenant order/menu/table
access). Fix the CRITICAL list before the next deploy; most fixes are a few lines each.

Findings marked **[verified]** were re-checked by hand against the code; the rest come from the
parallel reviewers and were read from source but not re-traced.

## Findings

### CRITICAL
1. **Default super-admin seeded in every environment** [verified] — `backend/auth-service/src/main/java/com/qrserve/auth/AuthServiceApplication.java:33-49`.
   `CommandLineRunner` creates `admin@hotel.com` / `password` as `SUPER_ADMIN` on any startup where it is missing, including prod. The frontend even advertises the credentials (`src/pages/LoginPage.tsx:15-16,131`).
   Fix: gate the seeder on a dev profile (`@Profile("dev")`) and disable/rotate the account in every deployed DB now.
2. **Passwordless merchant takeover via Super App dev-fake, enabled in k8s** [verified] — `backend/k8s/base/auth-service.yaml:72-73` sets `SUPERAPP_DEV_FAKE_ENABLED="true"`; `overlays/site-bp` (what `deploy.yml:30` deploys) does not override it. Also `backend/docker-compose.yml:71` defaults to `true` under `SPRING_PROFILES_ACTIVE=prod`.
   `/api/auth/superapp/exchange` and `/superapp-login` are `permitAll`; any caller who knows a merchant short code gets a `MERCHANT_OWNER` session; unknown codes auto-provision merchants.
   Fix: remove the env var from base, default compose to `false`, and fail startup if the flag is true under `prod`.
3. **Kitchen orders: cross-tenant read of all orders** [verified] — `order-service/.../KitchenController.java:23-32`, `KitchenService.java:39-54`.
   `branchId`/`merchantId` are caller-supplied query params; no filter → `orderRepository.findAll()`. Any KITCHEN user sees every tenant's orders.
   Fix: take merchant from the principal (non-SUPER_ADMIN), reject foreign `branchId`, never `findAll()`.
4. **Order get/update by id has no tenant check** [verified] — `order-service/.../OrderController.java:57-71`, `OrderService.getOrder:214`, `updateOrderStatus:152` (no principal passed in).
   Any staff role can read or change any tenant's order (and free its table).
   Fix: pass the principal, compare `order.getMerchantId()`, 404 on mismatch.

### HIGH
Security / tenant isolation
5. **Menu mutations not tenant-scoped** — `menu-service/.../MenuService.java:124-213,299-303` via `ProductController:101-115`, `CategoryController:70-84`. Update/delete product & category and create-product-by-categoryId never check `merchantId`; ids are sequential Longs. Fix: `requireTenantAccess(entity.getMerchantId(), principal)` as `getProduct` already does.
6. **Tables / assignments / customer requests not tenant-scoped** — `merchant-service`: `TableController.java:35-40` (createTable on foreign branch), `:157-164` (updateTableStatus), `TableAssignmentV1Controller.java:40-79`, `TableAssignmentController.java:41-61`, `CustomerRequestController.java:61-66` (getRequests by branch), `WaiterTaskV1Controller.java:111-125` (`merchantId` from query param). Fix: derive tenant from `@AuthenticationPrincipal` everywhere.
7. **Anonymous writes into any tenant** — `POST /api/orders` and `POST /api/customer-requests` are `permitAll` with no QR signature; customer request takes `merchantId/branchId/tableId` independently. Fix: require the signed QR token, derive merchant/branch from the table.
8. **Negative/zero quantity orders** [verified] — `order-service/.../dto/CreateOrderRequest.java:18-27`: `quantity` only `@NotNull`, and `items` lacks `@Valid` so nested constraints never run. Anonymous negative-total orders. Fix: `@Valid` on `items`, `@Min(1) @Max(...)` on `quantity`.
9. **Production login form prefilled with super-admin creds** — `src/pages/LoginPage.tsx:15-16,130-132` when `VITE_ENABLE_PHASE_2=true`. Fix: gate on `import.meta.env.DEV`, start empty.
10. **Committed Capacitor dev server** — `capacitor.config.ts:10-11` `server.url: 'http://192.168.8.135:3000'`, `cleartext: true`: native builds load a remote cleartext page. Fix: env-gate `server` for dev only.
11. **CI runs an unpinned remote script as root** — `.github/workflows/deploy.yml:155` `curl …/kustomize/master/hack/install_kustomize.sh | bash` in the job holding the prod kubeconfig. Fix: pinned release + checksum. Also confirm the `production` environment has required reviewers (deploys on every push to main).

Correctness
12. **Notification consumer likely cannot deserialize any event** [verified config] — `notification-service/.../KafkaConsumerConfig.java:26-29`: `USE_TYPE_INFO_HEADERS=false` with no default type, four listener types on one factory, no `ErrorHandlingDeserializer`. Realtime kitchen/waiter/guest updates would never fire and a bad record blocks the partition. Fix: per-listener default type (or type-inferring converter) + `ErrorHandlingDeserializer`. Confirm at runtime.
13. **Order status lost updates** — `OrderEntity` has no `@Version`; concurrent READY→DELIVERED / READY→CANCELLED both pass and publish contradictory events. Fix: `@Version`, map conflict to 409.
14. **Table freed/stuck wrongly** — `OrderService.java:63-149,180-182`: DELIVERED/PAID frees the table even with other open orders; CANCELLED never frees it. Fix: free only when no non-terminal orders remain.
15. **createOrder holds a DB tx across REST retries + Kafka; no idempotency** — `OrderService.java:63-149`, `AppConfig.java:30` (no connect timeout), pool size 5, gateway time limit 10s → 503 while the order commits; guest retries duplicate. Events published before commit (`:122-136,181-195`). Fix: move I/O out of the tx, `@TransactionalEventListener(AFTER_COMMIT)`, idempotency key, connect timeout.
16. **Gateway tenant cache poisoned by merchant-service blips** — `api-gateway/.../TenantSlugResolver.java:73-94`: any 5xx/timeout cached as "unknown tenant". Fix: only 404 → negative cache; add timeout.
17. **One circuit breaker for all gateway routes** — `api-gateway/application.yml` every route `name: default`. Fix: distinct names.
18. **Analytics "today" is all-time and includes cancelled** — `analytics-service/.../AnalyticsService.java:42-56`. Fix: server-side date range (explicit TZ), exclude CANCELLED.
19. **EMVCo payload breaks table creation for long/non-ASCII merchant names** — `merchant-service/.../TableQrProvisioningService.java:111-116` + `EmvcoPayload.java:79-97`; Amharic names or >25 chars → every `createTable` 400s. Fix: sanitize/truncate when building the payload.
20. **Catch-all exception handler turns client errors into 500** — `shared/exceptions/.../GlobalExceptionHandler.java:181`: bad JSON, missing params, 405, 413, `DataIntegrityViolationException` all → 500. Fix: extend `ResponseEntityExceptionHandler`, map integrity violations to 409.
21. **Public menu endpoint leaks drafts / merges branches** — `menu-service/.../MenuService.java:307-311`, `MenuController.java:28-32`. Fix: serve only PUBLISHED per-branch menu.
22. **`setPrimaryBranch` violates the one-primary unique index** — `BranchService.java:101-108` (flush order). Fix: clear old primary with a `@Modifying` query first.
23. **Duplicate table numbers break public menu resolve** — `TableEntity`/`TableService.java:35`: no unique `(branch_id, table_number)`. Fix: constraint + 400.
24. **Frontend: refresh failure logs out silently but UI stays "logged in"** — `src/lib/api.ts:322-343`: network error/5xx → `clearTokens()` without notifying `AuthContext`. Fix: clear only on 400/401/403 and dispatch `qrserve_auth_update`.
25. **Frontend: logout keeps the previous user's query cache** — `src/context/AuthContext.tsx:165-176`. Fix: `queryClient.clear()` on logout.
26. **Frontend: cross-tab token state diverges** — `src/lib/api.ts:243-244` module-level tokens never re-read on `storage` events.

### MEDIUM
- Disabled users can still log in/refresh — `auth-service/.../AuthService.java:34-91` never checks `isEnabled()`.
- QR signature optional on public customer-request / menu-resolution / review endpoints; `GET /api/qr/digital-menu/*/*` hands out valid branch signatures publicly.
- CORS `allowedOriginPatterns("*")` + `allowCredentials(true)` — `SecurityConfig:147-150`, gateway `application.yml:18-27`, `StompWebSocketConfig:47`.
- Gateway `discovery.locator.enabled: true` exposes every Eureka service at `/{service-id}/**`, bypassing the route table; Eureka has no auth.
- `GET /api/tables/{id}` public, returns full entity incl. `qrToken`; ids sequential.
- Containers run as root, floating image tags, no `securityContext`/NetworkPolicy; Kafka beta image, PLAINTEXT, no PVC, no probes.
- DB defaults `postgres/postgres` superuser everywhere; `ddl-auto: update` default in prod profile; minio `latest` + `minioadmin`.
- `deploy.yml:163` interpolates `secrets.KUBE_CONFIG` directly into the script — pass via `env:`.
- Discounts: `effectivePrice` frozen in 10-min Redis cache and computed in JVM zone (UTC vs Addis +3) — `MenuService.java:288-326`.
- Customer cart uses `item.price` not `effectivePrice` — `src/pages/CustomerMenuPage.tsx:179,187,511`, `CartSheet.tsx:38`: displayed total ≠ charged total.
- Race conditions without DB constraints: active table assignment, menu-per-branch, branch create (free-tier cap, primary), QR reprint version — all check-then-act.
- `MerchantSettingsService.java:34-41` doesn't fall back to merchant-wide `destinationRef` when branch row has null → silent downgrade to MENU_URL QR.
- `MerchantService.updateMerchant` ignores `shortCode` — can never be corrected.
- `BranchMenuBackfillRunner.java:177-188` drops discount fields.
- 401 used for forbidden/tenant mismatch (frontend treats as expired session) — use 403.
- `KitchenService` N+1 + fake `"T-01"` table on lookup failure; `ReportService` N+1 and errors → empty lists; `new RestTemplate()` without timeouts in analytics/qr/back-office.
- Order `paymentStatus` never leaves `UNPAID`; table PATCH failures only logged.
- Notification: Redis publish failures swallowed then offset acked; `auto-offset-reset: earliest` replays history as live alerts.
- `V1__init_schema.sql:144-153` explicit ids without `setval`, demo users with placeholder bcrypt (Flyway currently disabled; manual SQL indexes not applied on fresh DBs).
- Access JWT sent in WebSocket query string (logs); reconnect loop reuses an expired token — `src/lib/realtime.ts:125-131`.
- Tokens in `localStorage`; `X-Tenant-ID` header derived client-side (`src/lib/api.ts:366`) — backend must never trust it.
- `src/lib/digitalMenu.ts:92-102` `.json()` on possibly-empty review POST response → error shown after success → duplicates.
- `graphify-out/` (~340 generated files) committed — `git rm -r --cached graphify-out` + ignore.

### LOW
- `DigitalMenuPage.tsx:189-198` ReviewForm needs `key={branchId}`.
- `AuthContext.tsx:105,140` `atob` on base64url throws after tokens are stored.
- `LoginPage.tsx:112` ignores `location.state.from` (deep links lost).
- `src/router/AppRouter.tsx:70` leftover demo `alert(...)` with a personal email.
- `Modal.tsx:44` title-derived ids collide; focus trap includes disabled/hidden elements.
- `useRealtime.ts:191-195` effect order causes socket churn.
- STOMP scoped to merchant, not branch; any staff can follow any order topic by UUID.
- Actions pinned by tag not SHA; no image scan/SBOM; base manifests reference `:latest`; `auth-service` replicas 4 vs HPA min 2.
- nginx lacks security headers / `server_tokens off`.
- `backend/init-db.sql` missing `qrserve_backoffice`; AWS SDK 2.29.29 stale; `metadata.json` AI Studio leftover.
- `ReviewService` averages in memory; `deleteBranch` can delete primary; no state-machine guard on customer requests/assignments; `MenuTemplateService.seedDefaults` races on multi-replica boot.
- Large files: `src/lib/api.ts` (1280 lines), `StandeeStudio.tsx` (999).
- `System.out.println` with emoji in `AuthServiceApplication.java:46`.

### Missing tests (risky logic)
`OrderService.updateOrderStatus` transitions/table-free rules, `createOrder` totals/quantity; `AnalyticsService` aggregation; notification `DomainEventListener`; gateway fallback/circuit breaker; `TableAssignmentService`, `CustomerRequestService`, `MerchantService` slug/update; `GlobalExceptionHandler` mappings; tenant checks on the endpoints in CRITICAL 3-4 / HIGH 5-6; no frontend tests at all (no `test` script).

## Validation Results

| Check | Result |
|---|---|
| Type check (`tsc --noEmit`) | Pass |
| Lint | Skipped — `npm run lint` is just `tsc` |
| Frontend tests | Skipped — none exist |
| Frontend build (`vite build`) | Pass (2m 2s) |
| Backend compile (all 14 modules, javac harness) | Pass |
| Backend unit tests (javac + JUnit launcher harness) | Pass — 393/393 (analytics, discovery have no tests) |
| `./gradlew build` | Not run — Gradle cannot run in this environment; run it locally |
| `npm audit` | Not run |

## Fix status (2026-10-12, uncommitted working tree)

All CRITICAL and HIGH findings fixed; MEDIUM/LOW fixed except the skips below.
Decision taken with the user: guest WRITE endpoints (orders, customer requests, reviews) require a
valid QR signature; menu viewing stays signature-optional.

Verification: frontend tsc / test:unit / build pass; backend javac harness compiles all modules,
489/489 unit tests pass. `./gradlew build` still to be run locally.

Skipped (with reason): order idempotency keys (needs API design); httpOnly-cookie tokens and
`X-Tenant-ID` trust (backend auth redesign); WebSocket token in query string (handshake auth can't
use headers from browsers); Kafka PVC/SASL; actions pinned by SHA (SHAs not verifiable offline);
`ddl-auto` default (no migration tooling in use); ReportService and kitchen pagination N+1;
splitting api.ts / StandeeStudio.tsx.

Ops steps: disable/rotate `admin@hotel.com` in every deployed auth DB; run
`merchant-service/src/main/resources/db/manual/007-*.sql`; set `MINIO_ROOT_USER/PASSWORD`; confirm
ingress controller namespace is `ingress-nginx` (NetworkPolicy); add required reviewers to the
GitHub `production` environment; smoke-test Kafka on `apache/kafka:3.7.1`; reprint any QR codes
that lack a signature (they can view the menu but no longer order/call waiter/review).

## Not covered
Git history secret scan, file-upload/media storage services, iOS project, `@SpringBootTest` integration tests, runtime smoke test (`tenant-smoke-test` skill needs a running stack).
