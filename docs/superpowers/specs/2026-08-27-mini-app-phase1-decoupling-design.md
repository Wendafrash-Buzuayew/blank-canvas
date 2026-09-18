# QRServe → M-PESA Merchant Mini App: Phase 1 Decoupling — Design

**Date:** 2026-08-27
**Status:** Approved design. Not yet planned or implemented.
**Scope:** Frontend, API Gateway, and deployment changes to expose a lean, mobile-first Phase 1 Merchant Mini App inside the M-PESA Super App, while preserving all Phase 2 code, controllers, microservices, and database tables untouched for later reactivation.

**2026-09-16 update:** Open item 1 below is resolved. The real Super App
handshake only ever carries a merchant short code and the caller's MSISDN -
no business name, phone, city, address or category. `SuperAppMerchantClaim`,
`DevFakeSuperAppAuthPort`, and `SuperAppProvisioningService` (§5) now reflect
this. The business-profile gap is filled by a new one-time onboarding form
(`UserEntity.onboardingComplete`, `OnboardingPage.tsx`, gated in
`ProtectedRoute`) shown immediately after first Super App login, which also
lets the merchant optionally set a real email/password as a browser-login
fallback (`PATCH /api/auth/me/credentials`) - email/password is otherwise
entirely optional, matching how mini apps are conventionally integrated.

---

## 1. Context

QRServe today is a full restaurant platform: table scanning, ordering, kitchen dispatch, waiter requests, and analytics, built as eight Spring Boot microservices behind an API Gateway plus a React/Vite frontend. The strategy is pivoting to ship a lean **Merchant Mini App** inside the M-PESA Super App first: a merchant logs in (via M-PESA identity), manages their menu, generates one printable QR, and customers who scan it see a live digital menu. Nothing else.

**Hard constraint, stated by the business:** do not delete or destroy any existing code, controllers, microservices, or database tables. Everything currently out of scope (Kitchen Display, Table Management, Waiter Assignment, Order Engine, live WebSocket streams, Analytics) is Phase 2 — needed later, must come back with zero rework.

This means the entire design is **additive gating**, never removal: a build-time flag, new route/profile files, and Kustomize overlays sit *around* the existing code rather than modifying or deleting it.

---

## 2. Decisions register

| Decision | Choice | Consequence |
|---|---|---|
| Flag scope | One-way, global, build-time pivot for now | A single `VITE_ENABLE_PHASE_2` env var (frontend) + a `phase1` Spring profile (gateway/deployment). No per-tenant toggle, no runtime flag service — that would solve a problem that doesn't exist yet. |
| Hidden-route access | Hard-block, redirect home | Direct navigation to a Phase 2 route (bookmark, old link) redirects to the dashboard, same mechanism as today's role-based redirect. Not just hidden from nav. |
| Role scope in Phase 1 | MERCHANT_OWNER only | SUPER_ADMIN/BRANCH_MANAGER/WAITER/KITCHEN/CASHIER logins are treated as unauthorized while `VITE_ENABLE_PHASE_2=false`. Their accounts, roles, and all backend authorization logic are untouched. |
| Table/QR scope | Keep the table+QR backend machinery, hide "table" framing entirely | QR codes are minted per-`TableEntity` in this codebase; Phase 1 auto-creates exactly one default table per merchant and never surfaces "table" as a concept to the merchant. |
| QR granularity | Single QR per merchant, not a list | Reached by auto-provisioning one default branch + one default table at registration time. |
| Super App merchant handshake | Design from scratch against a port | No real integration contract exists yet. A `SuperAppAuthPort` with a dev-fake implementation, matching the "design against ports" pattern already used in `docs/superpowers/specs/2026-08-20-superapp-miniapp-payments-design.md`. |
| Branch selection at onboarding | No-op | Each merchant gets exactly one auto-created default branch. No branch-picker screen in Phase 1. |
| notification-service | Decoupled along with order-service/analytics-service | Phase 1's "dynamic menu" is a live read on every page load, not a push. Nothing in Phase 1 needs a WebSocket. |
| Mobile shell | Bottom tab nav replaces the sidebar entirely in Phase 1 | Not just collapsed for mobile — `Sidebar` doesn't render at all when `VITE_ENABLE_PHASE_2=false`. Returns exactly as today when the flag is true. |

---

## 3. Scope

**Phase 1 (exposed):**
1. Merchant Auth — M-PESA Super App token exchange, auto-registration on first entry.
2. Menu Management — Category/Product CRUD with inline price/availability edits.
3. Single QR Generator & PDF Exporter — one QR per merchant, regenerate on demand, printable templates.
4. Public digital menu view — scan → live menu, no order, no cart, no waiter call.

**Phase 2 (decoupled, code fully preserved):** Kitchen Display System, Table Management & waiter assignment, Waiter Call/Service Dock, Order Engine & checkout, live WebSocket/STOMP streams, Analytics, SUPER_ADMIN platform console, BRANCH_MANAGER role surface.

---

## 4. Frontend

### 4.1 Phase flag

`src/lib/phase.ts` (new) exports `isPhase2Enabled()`, reading `import.meta.env.VITE_ENABLE_PHASE_2 === 'true'` (default `false`).

### 4.2 Mobile shell

- **`MobileBottomNav.tsx`** (new) — fixed bottom tab bar: Dashboard, Menu & QR, Settings. ≥48px touch targets, M-PESA green accent, icon + label.
- **`DashboardLayout.tsx`** — when `!isPhase2Enabled()`: `Sidebar` does not render at all (not just collapsed), `MobileBottomNav` renders instead, content constrains to a centered 375–430px frame (no `lg:ml-64` desktop offset), header shrinks to logo + page title (the user/role chip moves into the Settings tab). When the flag is true, renders exactly as today — this is a conditional branch in the same component, nothing removed.

### 4.3 Routing

- **`navigation.ts`** — `ROLE_NAVIGATION_PHASE1.MERCHANT_OWNER`: Dashboard, Menu & QR (→ `/merchant/menu`, Section 4.4), Settings. The existing full `ROLE_NAVIGATION` map (all roles) is untouched underneath; `getNavigationForRole` picks the trimmed map when `!isPhase2Enabled()`.
- **`AppRouter.tsx`** — new `PhaseGate` component (same shape as the existing role-based `ProtectedRoute`), wrapping every Phase 2 route: `/admin/*`, `/branch/*`, `/waiter/*`, `/kitchen/*`, `/merchant/branches`, `/merchant/users`, `/merchant/orders`, `/merchant/waiters`, `/merchant/analytics`, `/merchant/tables` (the existing full table-management page — untouched, just gated). Redirects to the dashboard when `!isPhase2Enabled()`.
- **Login gate** — `ProtectedRoute`/`AuthContext`: any role other than `MERCHANT_OWNER` is treated as unauthorized when `!isPhase2Enabled()`, with a short explanatory message before logging out.

### 4.4 `MerchantMenuDashboard.tsx` (new)

Two tabs (not a linear wizard — onboarding already happened once during auth):

- **Menu tab** — reuses the existing `MenuManager.tsx` CRUD components (category list, product list), restyled single-column for mobile: sticky "+ Category" and "+ Item" quick-add, inline editable price + availability toggle per product, wired to the existing `useUpdateProduct` mutation (no backend change — `available`/`price` already exist on `UpdateProductRequest`).
- **QR Code tab** — reuses `QRDesigner.tsx` with its table-selector UI removed (there's exactly one QR now — Section 5). Shows the QR preview, template picker, PDF/PNG export.
- **Header CTA** — sticky "Generate Printable QR" button opening a bottom-sheet modal pre-configured with the PDF template and a Download action.

### 4.5 `CustomerMenuPage.tsx` adjustments

Existing file, `isPhase2Enabled()` conditionals around Phase 2 pieces — nothing deleted:

- **Removed when `false`:** cart state + Add/quantity-stepper controls (price becomes plain text), the cart bar, `<CartSheet>`, `<ServiceDock>` + `useCreateTableRequest`, `<OrderProgress>` + `usePublicOrderTracking` + `useOrderStream` (WebSocket).
- **Kept:** `usePublicMenuResolution` + `usePublicMenu` (already the entire mechanism behind live price updates — a GET on every page load, no push needed), search, sticky category rail, item cards (image/name/description/price).
- **Copy/badge change:** the "Table {tableNumber}" badge and "Order from your table" subtitle are internal-table artifacts that would look like bugs to a customer scanning a merchant's single QR. In Phase 1 mode these become just the merchant/branch name and "Browse our menu."

---

## 5. Merchant Auth via Super App

- **`SuperAppAuthPort`** (new interface) — `exchangeToken(rawToken): SuperAppMerchantClaim` (external merchant ref, business name, phone). A dev-fake accepts a token verbatim as JSON for local testing; the real Safaricom adapter drops in later behind the same port — no real contract exists yet, matching the "design against ports" approach already used for the payments work.
- **Auto-registration, in `auth-service`:** on token exchange, look up `MerchantEntity` by a new `superAppMerchantRef` column.
  - Not found → call a new internal `merchant-service` endpoint (`POST /internal/merchants/provision`, server-to-server) which, in one call, creates the `MerchantEntity` + one default `BranchEntity` + one default `TableEntity` (Section 6) + provisions that table's QR via the existing `TableQrProvisioningService`. `auth-service` then creates the `UserEntity` (role `MERCHANT_OWNER`) tied to the returned merchant.
  - Found → skip straight to issuing a JWT.
  - Either path ends in the same `LoginResponse` shape the app already handles — reuses `JwtTokenProvider`/`AuthService` as-is.
- **`LoginPage.tsx`** — new code path: on mount, check for a Super App token (query param or `postMessage` bridge); if present, exchange it automatically and redirect into the dashboard, no form shown. The existing email/password form stays as a local-dev/testing fallback — untouched, just runs second.

---

## 6. Single Merchant QR

Reuses existing, tested code with **zero backend changes.** `TableQrProvisioningService.mint()` already falls back to a `MENU_URL`-profile QR (a signed table-scoped menu URL) whenever a merchant has no payment settlement destination configured — which is every merchant in Phase 1, since settlement/payment configuration doesn't exist yet. Auto-provisioning one default table per merchant (Section 5) and calling the existing `provision(...)` on it produces exactly the "one simple digital-menu QR" required, for free.

**Forward-compatible:** the day Phase 2's settlement/payment work lands (per the existing payments design doc), this same QR automatically starts minting real EMVCo payment payloads the moment a merchant configures a destination — no migration.

**Customer access path (unchanged):** `https://{merchantSlug}.{baseDomain}/menu/{branchSlug}/{tableNumber}?signature=...` (what the QR encodes) → wildcard DNS/cert + the existing ingress (`backend/k8s/proxy-ingress.yml`) route `/api/**` to `api-gateway` and everything else to the frontend SPA, using `TenantHost`/`TenantResolutionGlobalFilter` to derive the tenant from the `Host` header. Frontend calls `GET /api/v1/public/menu/{merchantSlug}/{branchSlug}/{tableNumber}` then `GET /api/menu/{merchantId}` — both already `permitAll`. None of this layer changes; only the gateway's own internal route table does (Section 7).

---

## 7. API Gateway (`application-phase1.yml`, new)

Spring Boot profile YAML *replaces* list properties rather than merging them, so this file fully redefines `spring.cloud.gateway.server.webflux.routes` with only Phase 1 entries. The default profile (no `phase1`) keeps every route exactly as today.

| Route | Phase 1 | Notes |
|---|---|---|
| `auth-service` (`/api/auth/**`, `/api/v1/auth/**`) | Kept, unchanged | Login/refresh/logout + Super App token exchange |
| `menu-service` (`/api/categories/**`, `/api/products/**`, `/api/menu/**`) | Kept, unchanged | Menu CRUD + public menu read |
| `qr-service` (`/api/qr/**`) | Kept, unchanged | QR render/export |
| `merchant-service` | **Narrowed** | Predicate becomes `/api/merchants/**, /api/branches/**, /api/tables/**, /api/v1/public/**` — drops the old `/api/v1/**` catch-all, explicitly excludes `/api/waiters/**`, `/api/table-assignments/**`, `/api/customer-requests/**` |
| `order-service`, `order-service-public` | **Removed** | Whole entries dropped |
| `analytics-service` | **Removed** | Whole entry dropped |
| `notification-service` (`/ws/**`) | **Removed** | Whole entry dropped |

CORS, resilience4j, Eureka, and tracing config are copied through unchanged.

---

## 8. Microservices & Deployment

- **docker-compose:** add `profiles: ["phase2"]` to the `order-service`, `analytics-service`, `notification-service` blocks in `backend/docker-compose.yml` (Compose's native profile mechanism — purely additive). Plain `docker-compose up` starts only the Phase 1 set; `docker-compose --profile phase2 up` restores the full stack unchanged. `api-gateway` gets `SPRING_PROFILES_ACTIVE=phase1`.
- **Kubernetes:** a new `backend/k8s/overlays/phase1/` Kustomize overlay (base `deployment.yml`/`hpa.yml` — both multi-document files covering every service — stay untouched) with strategic-merge patches: `replicas: 0` on the order-service/analytics-service/notification-service Deployments, `minReplicas`/`maxReplicas: 0` on their HPAs (`order-service-hpa` currently has `minReplicas: 2`, which would otherwise fight a `replicas: 0` Deployment), and `SPRING_PROFILES_ACTIVE=phase1` added to `api-gateway`'s env. `kubectl apply -k backend/k8s` still deploys the full stack; `kubectl apply -k backend/k8s/overlays/phase1` deploys the lean one.
- **Database:** no schema/table changes anywhere. Order/analytics tables sit idle, not deleted.

---

## 9. Step-by-step plan

1. **Frontend navigation shell** — `phase.ts`, `MobileBottomNav`, `DashboardLayout` conditional, `PhaseGate`, login role-gate. Testable today against the existing full backend and email/password login.
2. **Merchant Auth via Super App** — `SuperAppAuthPort` + dev fake, auto-registration (`auth-service` + new `merchant-service` internal endpoint), `LoginPage` integration. Testable standalone with the dev-fake token.
3. **Single Merchant QR** — wire default branch/table/QR auto-creation into step 2's registration flow.
4. **`MerchantMenuDashboard`** — Menu tab (reuse `MenuManager`) + QR tab (reuse `QRDesigner`, table-selector stripped) + PDF export CTA. Depends on steps 1–3.
5. **`CustomerMenuPage` adjustments** — strip cart/order/service-dock/WebSocket behind the flag, strip table badge/copy. Independent of 1–4; testable against any existing table's menu URL.
6. **API Gateway `phase1` profile** — `application-phase1.yml`. Independent; testable in isolation (hit each allowed/blocked path).
7. **Deployment** — docker-compose profiles + K8s overlay. Depends on step 6.
8. **End-to-end validation** — Super App dev-fake token → auto-register → dashboard shows only Menu & QR/Settings tabs → generate QR → scan/hit the URL → customer sees the live, read-only menu → merchant edits a price → re-scan shows the new price, no reprint. Confirm every Phase 2 URL redirects home, and confirm `/api/orders/**`, `/api/analytics/**`, `/ws/**` are unreachable through the `phase1` gateway.

Steps 1, 2, 5, and 6 can proceed in parallel; 3 and 4 follow 2; 7 follows 6; 8 is the integration gate.

---

## 10. Testing

- Frontend: `PhaseGate` redirect behavior, `MobileBottomNav` render vs. `Sidebar` absence, `CustomerMenuPage` with/without the flag (existing `tsx` harness style, per repo convention — no new test runner).
- `SuperAppAuthPort` dev fake: token exchange → new merchant → registration → JWT; token exchange → existing merchant → JWT only.
- Gateway: route-reachability check per path in the Section 7 table, both profiles.
- End-to-end (step 8 above), run manually against a local `phase1` docker-compose stack before first deploy.

## 11. Open items

1. Real Super App token format and merchant-claim shape are unknown — same situation the payments design doc is already in for its own external contracts.
2. Whether the M-PESA Super App directory/scanner can deep-link straight into the Mini App, or whether merchants must be told to open a URL manually.
3. M-PESA brand template specifics for the PDF export (exact colors/logo placement) — not blocking, cosmetic.

## 12. Out of scope

Everything under Phase 2 (Section 3) is explicitly out of scope for this design and must not be touched: Kitchen Display, Table Management/assignment, Waiter Call/Service Dock, Order Engine, live WebSocket streams, Analytics, payments/EMVCo settlement (tracked separately in `2026-08-20-superapp-miniapp-payments-design.md`), multi-branch onboarding.
