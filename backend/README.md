# QRServe — Multi-Tenant Restaurant & Hotel QR Menu & Order Engine

![Java 21](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot 3.5.x](https://img.shields.io/badge/SpringBoot-3.5.x-green.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)
![Redis](https://img.shields.io/badge/Redis-7-red.svg)
![Kafka](https://img.shields.io/badge/Apache_Kafka-Event_Driven-black.svg)
![Docker](https://img.shields.io/badge/Docker-Kubernetes_Ready-blue.svg)

QRServe is an enterprise-grade, high-throughput multi-tenant digital menu and real-time order management backend platform built for restaurants, hotels, coffee shops, and bars.

---

## 🏛️ Architecture & Domain-Driven Design (DDD)

The system adheres strictly to **Hexagonal Architecture (Ports and Adapters)**, **Clean Architecture**, and **Domain-Driven Design (DDD)** principles to guarantee tenant data isolation, zero cross-tenant leaks, and seamless scalability to thousands of concurrent merchants.

```
                    +------------------------------------+
                    |        API Gateway / Ingress       |
                    +-----------------+------------------+
                                      |
         +----------------------------+----------------------------+
         |                            |                            |
+--------v-------+           +--------v-------+           +--------v-------+
|  Auth Service  |           | Merchant Serv. |           |   Menu Service |
+--------+-------+           +--------+-------+           +--------+-------+
         |                            |                            |
         +----------------------------+----------------------------+
                                      |
         +----------------------------+----------------------------+
         |                            |                            |
+--------v-------+           +--------v-------+           +--------v-------+
| Order Service  |           |   QR Service   |           | Notification  |
+--------+-------+           +--------+-------+           +--------+-------+
         |                            |                            |
         +----------------------------+----------------------------+
                                      |
                    +-----------------v------------------+
                    | Analytics & Performance Engine     |
                    +------------------------------------+
```

---

## 📂 Project Structure

```
backend/
├── build.gradle
├── settings.gradle
├── docker-compose.yml
├── postman_collection.json
├── README.md
├── k8s/
│   ├── postgres-deployment.yaml
│   ├── redis-deployment.yaml
│   └── qrserve-backend-app.yaml
├── shared/
│   ├── common/                # Base entities, TenantContext ThreadLocal, Flyway migrations
│   ├── security/              # JWT TokenProvider, Security Filters & Principal
│   ├── events/                # Domain Events (OrderCreatedEvent, OrderStatusUpdatedEvent)
│   └── exceptions/            # Global REST Exception Handlers & custom exceptions
├── auth-service/              # Authentication, Login, JWT Issuance & Token Refresh
├── merchant-service/          # Multi-tenant Merchant, Branch & Table management
├── menu-service/              # Hierarchy Category & Product Menu Builder
├── order-service/             # Customer QR Ordering, Kitchen KDS Board & Order Lifecycle
├── qr-service/                # High-res PNG, SVG & PDF Printable Table Stand Exporter
├── notification-service/      # WebSocket Server for Real-Time Kitchen & Table Pushes
└── analytics-service/         # Real-time Sales, Revenue, AOV & Dish Ranking Engine
```

---

## 🔐 Multi-Tenant Security & RBAC Roles

Every database record is tenant-partitioned via `merchant_id`. In-flight HTTP requests dynamically propagate `merchantId` through standard `JwtAuthenticationFilter` into `TenantContext` ThreadLocal variables.

### Role Permission Matrix

| Role | Multi-Branch Access | Menu Edit | Place Order | Kitchen KDS Board | Analytics |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `SUPER_ADMIN` | All Tenants | ✅ | ✅ | ✅ | System-wide |
| `MERCHANT_OWNER` | All Branches | ✅ | ✅ | ✅ | Full Merchant |
| `BRANCH_MANAGER` | Single Branch | ✅ | ✅ | ✅ | Branch-level |
| `WAITER` | Single Branch | ❌ | ✅ | ✅ | ❌ |
| `KITCHEN` | Single Branch | ❌ | ❌ | ✅ | ❌ |
| `CASHIER` | Single Branch | ❌ | ❌ | ✅ | Sales Only |
| `CUSTOMER` | Unauthenticated | ❌ | ✅ (Table QR) | ❌ | ❌ |

---

## 🛠️ Complete API Reference

### 1. Authentication
- `POST /api/auth/login` — Authenticate user and issue signed JWT access & refresh tokens
- `POST /api/auth/refresh` — Refresh access token
- `POST /api/auth/logout` — Invalidate user session

### 2. Merchants & Branches
- `POST /api/merchants` — Register a new multi-tenant merchant
- `GET /api/merchants/{id}` — Fetch merchant business profile
- `POST /api/branches` — Add a physical branch to a merchant

### 3. Table & QR Management
- `POST /api/tables` — Create dining table and auto-generate QR target URL
- `GET /api/qr/{tableId}` — Retrieve table QR metadata
- `POST /api/qr/export/pdf` — Export printable PDF table stand graphics
- `POST /api/qr/export/png` — Export high-resolution PNG QR vector

### 4. Menu Builder
- `POST /api/categories` — Create menu category
- `POST /api/products` — Add dish/product to menu category
- `GET /api/menu/{merchantId}` — Retrieve full published menu hierarchy

### 5. Customer Ordering & Kitchen Dashboard
- `POST /api/orders` — Customer places order from scanned QR table
- `PATCH /api/orders/{id}/status` — Advance order status (`ACCEPTED`, `PREPARING`, `READY`, `DELIVERED`, `PAID`, `CANCELLED`)
- `GET /api/kitchen/orders` — Live Kitchen Display System (KDS) order board

### 6. Analytics
- `GET /api/analytics/today` — Real-time today metrics (Revenue, AOV, Occupancy %)
- `GET /api/analytics/revenue` — Daily sales trends & revenue analytics
- `GET /api/analytics/popular-items` — Bestselling products & dish ranking

---

## ⚡ Real-Time WebSocket Channels

Connect via WebSocket to `/ws/orders` for real-time kitchen & dining room alerts:
- `/order/new` — Triggered instantly when a guest submits a new table order
- `/order/status` — Pushed when kitchen marks order as `PREPARING` or `READY`
- `/order/cancelled` — Alert for order cancellations
- `/table/occupied` — Dynamic table occupancy update on kitchen floor layout

---

## 🚀 Local Development & Docker Setup

```bash
# 1. Navigate to backend workspace
cd backend

# 2. Build Gradle project binaries
./gradlew build -x test

# 3. Spin up PostgreSQL, Redis, Kafka, and Microservices via Docker Compose
docker-compose up --build -d

# 4. Verify API Gateway is listening
curl http://localhost:8080/api/merchants/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11
```

---

## ☸️ Kubernetes Deployment

```bash
# Apply production cluster manifests
kubectl apply -f backend/k8s/postgres-deployment.yaml
kubectl apply -f backend/k8s/redis-deployment.yaml
kubectl apply -f backend/k8s/qrserve-backend-app.yaml
```

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

### Required environment

Every service fails to start without these — deliberately. See `.env.example`.

| Variable | Why it has no default |
|---|---|
| `JWT_SECRET` | A default means anyone can forge tokens |
| `QR_SIGNATURE_SECRET` | A default means anyone can forge QR signatures |
| `PUBLIC_BASE_DOMAIN` | A default emits QR codes pointing at the wrong host — a printed sheet of paper that does not work, discovered by a customer |

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

### Kubernetes

```bash
kubectl apply -f backend/k8s/config.yml          # PUBLIC_BASE_DOMAIN
kubectl apply -f backend/k8s/proxy-ingress.yml   # wildcard host + TLS
```

The ingress needs wildcard DNS for `*.qrserve.safaricom.et` and a wildcard
certificate in the `qrserve-wildcard-tls` secret. The wildcard is **single-label**:
a certificate for `*.qrserve.safaricom.et` does not cover
`a.b.qrserve.safaricom.et`, which is why branches are path segments rather than
second-level subdomains.

> **Caveat:** `backend/k8s/deployment.yml` describes only four of the nine
> services. The ingress above is correct but cannot be exercised until the
> manifests deploy the services they claim to. A green `kubectl apply` on the
> ingress is not evidence that routing works.

### Tenant isolation is a CI gate

`backend/merchant-service/src/test/java/com/qrserve/merchant/TenantIsolationIT.java`
seeds two merchants and asserts that merchant A's credential cannot reach
merchant B's data. In a shared deployment an isolation defect is one restaurant
reading another's revenue, so **new tenant-scoped endpoints belong in that file**.

```bash
cd backend && ./gradlew :merchant-service:test
```


---

## 💳 Bonus Extensions (Phase 2 Integrations)
- **EthIO-Payment Connectors**: Telebirr, M-PESA, ETHQR & CBE Birr gateway webhooks
- **Inventory & Stock Management**: Real-time ingredient deductions per dish order
- **Offline Sync Mode**: Local cache queue for intermittent connectivity
