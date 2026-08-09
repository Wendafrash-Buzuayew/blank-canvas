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

## 💳 Bonus Extensions (Phase 2 Integrations)
- **EthIO-Payment Connectors**: Telebirr, M-PESA, ETHQR & CBE Birr gateway webhooks
- **Inventory & Stock Management**: Real-time ingredient deductions per dish order
- **Offline Sync Mode**: Local cache queue for intermittent connectivity
