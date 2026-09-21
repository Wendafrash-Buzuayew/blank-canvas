# QRServe — Multi-Tenant Restaurant & Hotel QR Menu & Order Engine

![Java 17](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot 4.1.0](https://img.shields.io/badge/SpringBoot-4.1.0-green.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-blue.svg)
![Redis](https://img.shields.io/badge/Redis-red.svg)
![Kafka](https://img.shields.io/badge/Apache_Kafka-Event_Driven-black.svg)
![Docker](https://img.shields.io/badge/Docker-Kubernetes_Ready-blue.svg)

QRServe is a multi-tenant digital menu and real-time order management backend for restaurants, hotels, coffee shops and bars. One deployment serves every tenant, separated by `merchantId`.

The React frontend lives at the repository root — see [`../README.md`](../README.md).

> **Versions:** the Gradle toolchain pins Java **17** (`build.gradle`), and the Spring Boot plugin and BOM are both **4.1.0** — which is Spring Framework 7 / Spring Security 7, not the 3.x line. That matters in tests: Spring Boot 4 moved `@AutoConfigureMockMvc` into a module this project does not depend on, so MockMvc is built by hand (see `TenantIsolationIT`). PostgreSQL and Redis versions are **not pinned** anywhere — `docker-compose.yml` doesn't declare them at all (see Local Development), so you get whatever you run.

---

## 🏛️ Architecture

Requests enter through Spring Cloud Gateway, which resolves the tenant from the `Host` header and routes by service name via Eureka (`lb://menu-service`). Services own their own PostgreSQL database and communicate synchronously over REST and asynchronously over Kafka.

```
                        +-------------------------+
                        |   api-gateway  :8081    |  tenant resolution, routing,
                        +------------+------------+  circuit breakers, retries
                                     |
                      lb:// (resolved via Eureka)
                                     |
    +--------------+--------------+--+-----------+--------------+--------------+
    |              |              |              |              |              |
+---v----+   +-----v-----+  +-----v----+   +-----v----+   +-----v-----+  +-----v------+
|  auth  |   | merchant  |  |   menu   |   |  order   |   |    qr     |  | back-office|
| :8087  |   |  :8085    |  |  :8086   |   |  :8083   |   |  :8088    |  |   :8090    |
+---+----+   +-----+-----+  +-----+----+   +-----+----+   +-----+-----+  +-----+------+
    |              |              |              |              |              |
    +--------------+--------------+------+-------+--------------+--------------+
                                         |
                           Kafka domain events
                                         |
                     +-------------------+-------------------+
                     |                                       |
          +----------v-----------+              +------------v---------+
          | notification :8084   |              |  analytics  :8089    |
          | STOMP/WebSocket push |              |  sales, AOV, ranking |
          +----------------------+              +----------------------+

          discovery-service :8761 (Eureka)  —  every service registers here
```

---

## 📂 Project Structure

Ten deployable modules plus four shared libraries — the authoritative list is `settings.gradle`.

```
backend/
├── build.gradle
├── settings.gradle
├── docker-compose.yml
├── postman_collection.json
├── k8s/
│   ├── base/                   # Every real service, one site's worth — see k8s/base/kustomization.yaml
│   ├── overlays/site-bp/       # `kubectl apply -k` this, not the files under base/ directly
│   ├── postgres-deployment.yaml  # local/dev only — see its own header comment
│   └── redis-deployment.yaml     # local/dev only — see its own header comment
├── shared/
│   ├── common/                # Base entities, TenantContext ThreadLocal, Flyway migrations
│   ├── security/              # JWT TokenProvider, security filters, UserPrincipal, UserRole
│   ├── events/                # Domain events (OrderCreatedEvent, OrderStatusUpdatedEvent)
│   └── exceptions/            # GlobalExceptionHandler & custom exceptions
├── discovery-service/         # Eureka service registry — everything else registers with it
├── api-gateway/               # Spring Cloud Gateway: routing, tenant resolution, resilience
├── auth-service/              # Login, JWT issuance & refresh, Super App SSO, onboarding state
├── merchant-service/          # Merchant, branch & table management; subscription tiers
├── menu-service/              # Category/product menu builder, templates, ETHQR payment proxy
├── order-service/             # QR ordering, kitchen KDS board, order lifecycle, payment terminals
├── qr-service/                # QR metadata, signed digital-menu URLs, PNG export
├── notification-service/      # STOMP/WebSocket server for kitchen & waiter pushes
├── analytics-service/         # Sales, revenue, AOV & dish ranking
└── back-office-service/       # Audit logs and operational reports
```

---

## 🔐 Multi-Tenant Security & RBAC Roles

Every database record is tenant-partitioned via `merchant_id`. In-flight requests propagate `merchantId` from the JWT through `JwtAuthenticationFilter` into a `TenantContext` ThreadLocal.

### Role Permission Matrix

The seven values of `shared/security/.../UserRole.java`:

| Role | Multi-Branch Access | Menu Edit | Place Order | Kitchen KDS Board | Analytics |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `SUPER_ADMIN` | All Tenants | ✅ | ✅ | ✅ | System-wide |
| `MERCHANT_OWNER` | All Branches | ✅ | ✅ | ✅ | Full Merchant |
| `BRANCH_MANAGER` | Single Branch | ✅ | ✅ | ✅ | Branch-level |
| `WAITER` | Single Branch | ❌ | ✅ | ✅ | ❌ |
| `KITCHEN` | Single Branch | ❌ | ❌ | ✅ | ❌ |
| `CASHIER` | Single Branch | ❌ | ❌ | ✅ | Sales Only |
| `CUSTOMER` | Unauthenticated | ❌ | ✅ (Table QR) | ❌ | ❌ |

### Subscription tiers

Merchants carry a tier (`MerchantTier`), enforced server-side rather than only in the UI:

- **FREE** — capped at **1 branch**. A second `POST /api/branches` is rejected with **`403`** (`TierLimitExceededException`), distinct from a `400` validation error so the client can render an upgrade prompt.
- **PRO** — unlimited branches, multi-language ETHQR payment standees, bulk per-table exports, custom banners.

---

## 🛠️ API Reference

### 1. Authentication
- `POST /api/auth/login` — Authenticate and issue signed JWT access & refresh tokens
- `POST /api/auth/refresh` — Refresh an access token
- `POST /api/auth/logout` — Invalidate the session
- `POST /api/auth/superapp-login` — Super App SSO handshake (`msisdn` + `shortCode`, plus `signature`/`superAppToken`). First-time callers are auto-registered and flagged `onboardingComplete = false`.
- `POST /api/auth/superapp/exchange` — Older handshake shape: exchanges an opaque Super App token
- `GET /api/auth/me` — Current principal, including `onboardingComplete`
- `PATCH /api/auth/me/onboarding` — Mark first-run onboarding complete
- `PATCH /api/auth/me/credentials` — Set an email/password fallback login for a Super App-provisioned account
- `POST /api/auth/users` · `GET /api/auth/users` — Staff user management within a merchant

> `signature`/`superAppToken` are **accepted but not yet verified** — no signing key or algorithm has been supplied by the Super App team. The dev-only fake exchange is gated behind `SUPERAPP_DEV_FAKE_ENABLED`, which must never be true where untrusted traffic can reach it.

### 2. Merchants & Branches
- `POST /api/merchants` — Register a merchant (slug is permanent; see below)
- `GET /api/merchants/{id}` — Merchant business profile
- `PUT /api/merchants/{id}` — Update profile; rejects any slug change
- `POST /api/branches` — Add a branch. **`403`** on the Free tier's second branch.
- `GET /api/branches/{id}` — Branch by id (tenant-scoped; `403` cross-tenant)
- `GET /api/branches/merchant/{merchantId}` — A merchant's branches

### 3. Tables & QR
- `POST /api/tables` — Create a table and provision its QR token
- `GET /api/qr/{tableId}` — QR metadata and target URL
- `POST /api/qr/export/png` — High-resolution PNG
- `GET /api/qr/digital-menu/{merchantSlug}/{branchSlug}/url` — The signed URL a QR should encode
- `GET /api/qr/digital-menu/{merchantSlug}/{branchSlug}` — Public menu resolution (unauthenticated)

> `POST /api/qr/export/pdf` exists but is **deprecated and emits an invalid PDF** — it hand-assembles PDF bytes with a wrong `/Length` and no xref table, so no conforming reader opens the file. Printable standees are produced by the frontend's standee studio through the browser's own print pipeline, which yields real vector output. See the header comment in `src/lib/standee.ts`.

### 4. Menu Builder
- `POST /api/categories` — Create a category
- `POST /api/products` — Add a product to a category
- `GET /api/menu/{merchantId}` — Merchant-level menu hierarchy
- `GET /api/menu/branch/{branchId}` — **A branch's published menu.** Menus are branch-scoped: each branch owns an independent copy of the catalog, so this is the endpoint a guest-facing menu reads.
- `GET /api/menu/branch/{branchId}/manage` — The editable (draft) view for the menu builder
- `POST /api/menu/branch/{branchId}/publish` — Publish a branch's draft
- Menu templates: create, update and preview the rendered design

### 5. Payments
- `GET /api/payment/ethqr?branchId={id}` — Generate the branch merchant's Safaricom ETHQR payment QR (`SUPER_ADMIN` / `MERCHANT_OWNER`).

  Proxies `POST https://qr.safaricom.et/api/qr/generate` with a body of **exactly one field**, `{"accountNumber": "<shortCode>"}` — adding anything else makes the provider return a different schema entirely. Returns the provider's response mapped field-for-field: `qrImageUrl`, `merchantName`, `accountNumber`, `mobileNumber`, `city`. There is no `amount` parameter; a printed standee code is reusable and open-amount by design. Failures surface as **`502`** naming the upstream (`safaricom-ethqr`), not a blanket `503`.

### 6. Customer Ordering & Kitchen
- `POST /api/orders` — Place an order from a scanned table QR
- `PATCH /api/orders/{id}/status` — Advance status (`ACCEPTED`, `PREPARING`, `READY`, `DELIVERED`, `PAID`, `CANCELLED`)
- `GET /api/kitchen/orders` — Live KDS board
- `GET /api/v1/public/orders/{orderId}` — Guest order tracking (unauthenticated)

### 7. Analytics & Back Office
- `GET /api/analytics/today` — Today's revenue, AOV, occupancy
- `GET /api/analytics/revenue` — Daily sales trends
- `GET /api/analytics/popular-items` — Dish ranking
- `GET /api/audit-logs`, `GET /api/reports/...` — Back-office audit and reporting

### Error statuses

| Status | Meaning |
|---|---|
| `400` | `BusinessException` — the request is invalid as sent |
| `403` | Denied, or a tier limit (`TierLimitExceededException`, carrying its real message) |
| `404` | `ResourceNotFoundException` — it genuinely does not exist |
| `502` | `UpstreamServiceException` — a named dependency failed; body includes `upstream` |
| `503` | `ServiceUnavailableException`, or the gateway's circuit-breaker fallback |

A `503` from the gateway with `{"error":"SERVICE_UNAVAILABLE"}` is the `forward:/fallback` route, which also fires when a service is running but **not registered with Eureka** — check the registry at `http://localhost:8761` before assuming the service is down.

---

## ⚡ Real-Time WebSocket Channels

STOMP over WebSocket. Clients connect to the **`/ws`** endpoint (proxied by Vite in development), subscribe under `/topic/...`, and send to the `/app` prefix.

| Destination | Purpose |
|---|---|
| `/topic/merchant/{merchantId}/branch/{branchId}/kitchen` | Kitchen board for one branch |
| `/topic/merchant/{merchantId}/branch/{branchId}/waiters` | Waiter calls and table assignments |
| `/topic/orders/{orderId}` | One order's status, for guest tracking |

Destinations are built by `StompDestinations` and fanned out from Kafka by `DomainEventListener`; Redis pub/sub (`RedisEventPublisher` / `RedisEventSubscriber`) carries them between instances so any node can serve any subscriber.

**Subscriptions are authorized, not open.** `StompAuthInterceptor` matches each destination against the subscriber's own `merchantId`/`branchId` — without it any authenticated client could subscribe to `/topic/merchant/{anyMerchantId}/...` and read another restaurant's traffic. A guest tracking one order is permitted only `/topic/orders/{that id}`. New destinations need a matching rule there.

---

## 🚀 Local Development & Docker Setup

`docker-compose.yml` declares **MinIO, `discovery-service`, the eight services and the gateway**. It does *not* declare PostgreSQL, Redis or Kafka — services reach those at `host.docker.internal`, so start them yourself first.

```bash
cd backend

# 1. Secrets. Services exit at startup without them.
cp .env.example .env              # PowerShell: copy .env.example .env

# 2. Build the boot jars. The Dockerfiles only COPY an existing jar,
#    so this is not optional — see Redeploying a change below.
./gradlew build -x test           # Windows: .\gradlew.bat build -x test

# 3. Start discovery, MinIO, the services and the gateway.
docker compose up --build -d

# 4. Check they came up and registered.
docker compose ps
curl http://localhost:8086/actuator/health/readiness
curl -H "Accept: application/json" http://localhost:8761/eureka/apps
```

The gateway listens on **8081**, not 8080. Every `/api/**` call through it requires a bearer token except the explicitly public routes, so an unauthenticated `curl` returns `403` — that is the security filter working, not a routing fault.

### Ports

| Port | Service | | Port | Service |
|---|---|---|---|---|
| 8081 | api-gateway | | 8088 | qr-service |
| 8083 | order-service | | 8089 | analytics-service |
| 8084 | notification-service | | 8090 | back-office-service |
| 8085 | merchant-service | | 8761 | discovery-service (Eureka) |
| 8086 | menu-service | | 9000/9001 | MinIO |
| 8087 | auth-service | | | |

External: PostgreSQL `5432`, Redis `6379`, Kafka `9092`.

### Databases

One per service, all on the same instance: `qrserve_auth`, `qrserve_merchant`, `qrserve_menu`, `qrserve_order`, `qrserve_qr`, `qrserve_notification`, `qrserve_analytics`, `qrserve_backoffice`.

### Redeploying a change

Each `Dockerfile` only does `COPY <svc>/build/libs/<svc>-1.0.0-SNAPSHOT-boot.jar app.jar`. Rebuilding an image **without** rebuilding the jar silently redeploys the old code:

```bash
./gradlew :menu-service:bootJar      # Windows: .\gradlew.bat :menu-service:bootJar
docker compose build menu-service
docker compose up -d --force-recreate --no-deps menu-service
```

Container environment is fixed when the container is created, so editing `docker-compose.yml` or `.env` changes nothing until the container is recreated. A long-running stack can therefore be several commits behind the working tree while looking perfectly healthy.

### Tests

```bash
./gradlew test                      # every module
./gradlew :merchant-service:test    # one module
```

---

## ☸️ Kubernetes Deployment

All ten services, via Kustomize — see
`docs/superpowers/plans/2026-09-16-multi-dc-phase0-bp-rollout.md` for the full,
ordered rollout runbook (secrets, DB prerequisites, verification per step).
Short version:

```bash
kubectl create secret generic qrserve-secrets \
  --from-literal=jwt-secret="$(openssl rand -base64 48)" \
  --from-literal=qr-signature-secret="$(openssl rand -base64 32)" \
  --from-literal=db-password="<the real Postgres VM password>"

kubectl apply -k backend/k8s/overlays/site-bp   # includes the ingress — wildcard host + TLS
```

Never `kubectl apply -f` an individual file under `backend/k8s/base/` — always
go through the `overlays/site-<name>` layer above it, even today with BP as
the only site, so a second site is never hand-copied from scratch.

---

## 🏢 Multi-tenancy: subdomains, QR codes, secrets

Every merchant is served at `{merchantSlug}.{PUBLIC_BASE_DOMAIN}`. One deployment
serves all tenants, separated by `merchantId`.

### Tenant subdomains in local development

Subdomains do not resolve against `localhost`, and asking every developer to edit
`/etc/hosts` per tenant is friction that gets bypassed — which leaves the
subdomain code path exercised only in staging.

Instead use a public wildcard that resolves to loopback. `localtest.me` and
`sslip.io` both do, with no setup at all:

```bash
PUBLIC_BASE_DOMAIN=localtest.me:3000
PUBLIC_URL_SCHEME=http
```

Then `http://sunrise.localtest.me:3000/menu/main/1` reaches the Vite dev server,
which proxies to the gateway with the `Host` header intact, and the real tenant
resolution path runs locally.

Two settings make that work and are easy to break:

- `vite.config.ts` sets **`changeOrigin: false`** on both proxies. With
  `changeOrigin: true` the `Host` header is rewritten to the proxy target and the
  tenant label is gone before the gateway sees it — tenant resolution silently
  never fires.
- `server.allowedHosts` includes `.localtest.me`. Vite 6 blocks unrecognised
  `Host` headers.

The path form still works on a bare host: `http://localhost:3000/menu/demo/main/1`
is the landing page's demo link and needs no subdomain.

### Reserved subdomains

`admin`, `api`, `app`, `www`, `static`, `assets`, `ws`, `mail` and `status` never
resolve as a tenant and are rejected at merchant creation.
`admin.qrserve.safaricom.et` is reserved for `SUPER_ADMIN` cross-tenant work,
which asserts no tenant.

An unresolvable label under the base domain returns **404** — never a fallback to
some default tenant, because on a wildcard domain that would turn every mistyped
subdomain into a cross-tenant read.

### Merchant slugs are permanent

The slug is a hostname and gets printed onto physical table stands, so
`PUT /api/merchants/{id}` rejects any attempt to change it. Allowing renames needs
an alias table so old hostnames keep resolving; that is deliberately not built
yet, and the rejection is the guard that makes deferring it safe.

Branch slugs are path segments rather than hostnames, so they are unique **per
merchant**, not globally — two tenants may each have a branch called `main`.

### Required environment

Every service fails to start without these — deliberately. See `.env.example`
for the complete, commented list.

| Variable | Why it has no default |
|---|---|
| `JWT_SECRET` | A default means anyone can forge tokens |
| `QR_SIGNATURE_SECRET` | A default means anyone can forge QR signatures |
| `PUBLIC_BASE_DOMAIN` | A default emits QR codes pointing at the wrong host — a printed sheet of paper that does not work, discovered by a customer |
| `PUBLIC_MENU_DOMAIN` | Same, for the path-based `/m/{merchant}/{branch}` menu URLs |

Optional, but worth knowing: `SAFARICOM_QR_BASE_URL` defaults to the real
provider endpoint and `SAFARICOM_QR_API_KEY` is empty (a real call with no
`Authorization` header succeeds). Pointing the base URL at a mock and forgetting
is a recognised failure mode — each instance logs its configured ETHQR endpoint
at startup and warns when it is not `qr.safaricom.et`.

### Rotating the QR signing secret

QR codes are printed onto physical table stands, so rotating
`QR_SIGNATURE_SECRET` must not invalidate them all at once:

1. Set `QR_SIGNATURE_SECRET_PREVIOUS` to the current value.
2. Set `QR_SIGNATURE_SECRET` to the new value.
3. Deploy. Codes already printed still validate; new ones are signed with the new
   secret.
4. Reprint at leisure, then clear `QR_SIGNATURE_SECRET_PREVIOUS`.

The signing key is derived per tenant as `HMAC-SHA256(masterSecret, merchantId)`,
so a compromise is confined to one restaurant's codes rather than the whole
platform's.

### Kubernetes config

`PUBLIC_BASE_DOMAIN`/`PUBLIC_MENU_DOMAIN` (the `qrserve-config` ConfigMap) and
the ingress are both part of `backend/k8s/base/`, applied via
`kubectl apply -k backend/k8s/overlays/site-bp` — see the Kubernetes
Deployment section above.

The ingress needs wildcard DNS for `*.qrserve.safaricom.et` and a wildcard
certificate in the `qrserve-wildcard-tls` secret. The wildcard is **single-label**:
a certificate for `*.qrserve.safaricom.et` does not cover
`a.b.qrserve.safaricom.et`, which is why branches are path segments rather than
second-level subdomains.

All ten services now have real manifests under `backend/k8s/base/` (as of the
Phase 0 hardening pass — see
`docs/superpowers/specs/2026-09-16-multi-dc-production-deployment-design.md`),
so the ingress can actually be exercised end to end; this previously wasn't
true (only four of ten services had manifests, `discovery-service` had none
at all, and neither the gateway nor auth/merchant/order ever had
`EUREKA_SERVER_URL` set — `lb://` routing had no real instance to resolve).

### Tenant isolation is a CI gate

`backend/merchant-service/src/test/java/com/qrserve/merchant/TenantIsolationIT.java`
seeds two merchants and asserts that merchant A's credential cannot reach
merchant B's data. In a shared deployment an isolation defect is one restaurant
reading another's revenue, so **new tenant-scoped endpoints belong in that file**.

```bash
cd backend && ./gradlew :merchant-service:test
```

---

## 🗺️ Roadmap

Shipped: Safaricom **ETHQR** payment-QR generation with the five-language
standee template (see `GET /api/payment/ethqr` above).

Not yet built:

- **Further payment connectors** — Telebirr, CBE Birr gateway webhooks
- **Inventory & stock management** — real-time ingredient deductions per order
- **Offline sync mode** — local cache queue for intermittent connectivity
- **Real Super App signature verification** — see the note under Authentication
