# Gap Analysis Report — QRServe Backend Microservices

## 1. Executive Summary

This report identifies gaps between the **existing backend implementation** and the **target API contract** defined by the Phase 2 task requirements. The codebase has a solid foundation: entities, repositories, services, and controllers exist for most domains. However, several **endpoints, DTOs, entity fields, event publishing, RBAC guards, and API gateway routes** are missing or misaligned.

---

## 2. Project Structure Overview

### 2.1 Gradle Multi-Module Layout (`backend/settings.gradle`)

| Module | Package | Purpose |
|---|---|---|
| `:auth-service` | `com.qrserve.auth` | JWT authentication, user management |
| `:merchant-service` | `com.qrserve.merchant` | Merchants, branches, tables, waiters, assignments, customer requests |
| `:menu-service` | `com.qrserve.menu` | Categories, products, digital menu |
| `:order-service` | `com.qrserve.order` | Order lifecycle, kitchen display |
| `:qr-service` | `com.qrserve.qr` | QR code generation & export |
| `:notification-service` | `com.qrserve.notification` | WebSocket/STOMP notifications |
| `:analytics-service` | `com.qrserve.analytics` | KPI metrics, revenue, popular items |
| `:api-gateway` | `com.qrserve.gateway` | Spring Cloud Gateway routing |
| `:discovery-service` | `com.qrserve.discovery` | Eureka server |
| `:shared:common` | `com.qrserve.shared.common` | TenantContext, TraceContext |
| `:shared:exceptions` | `com.qrserve.shared.exceptions` | GlobalExceptionHandler, ResourceNotFoundException |
| `:shared:events` | `com.qrserve.shared.events` | OrderCreatedEvent, OrderStatusUpdatedEvent, CustomerRequestEvent, WaiterAlertEvent |
| `:shared:security` | `com.qrserve.shared.security` | SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter, UserPrincipal, UserRole |

### 2.2 Microservice Dependency Rules (from `build.gradle`)

- All microservices depend on `:shared:common`, `:shared:exceptions`, `:shared:security`
- `:order-service`, `:notification-service`, `:analytics-service` additionally depend on `:shared:events`
- **No direct cross-service imports** — services communicate via REST (RestTemplate) or Kafka events
- `:api-gateway` uses Spring Cloud Gateway (reactive, WebFlux)

---

## 3. Existing Implementations (What Works)

### 3.1 Merchant Service
- **Entities**: `MerchantEntity` (UUID id, slug, name, phone, city, address, logoUrl, category), `BranchEntity` (Long id, UUID merchantId, name, phone, address), `TableEntity` (Long id, Long branchId, UUID merchantId, tableNumber, capacity, status, qrToken), `WaiterEntity`, `TableAssignmentEntity`, `CustomerRequestEntity`
- **Repositories**: All JPA repositories with tenant-scoped query methods
- **Services**: `MerchantService`, `BranchService`, `TableService`, `WaiterService`, `TableAssignmentService`, `CustomerRequestService`
- **Controllers**: `MerchantController` (`/api/merchants`), `BranchController` (`/api/branches`), `TableController` (`/api/tables`), `WaiterController` (`/api/waiters`), `TableAssignmentController` (`/api/table-assignments`), `CustomerRequestController` (`/api/customer-requests`)
- **DTOs**: `CreateMerchantRequest`, `CreateBranchRequest`, `CreateTableRequest`, `CreateTableResponse`, `CreateWaiterRequest`, `UpdateWaiterRequest`, `AssignTableRequest`, `CreateCustomerRequestDto`, `UpdateRequestStatusDto`

### 3.2 Order Service
- **Entities**: `OrderEntity` (UUID id, orderNumber, merchantId, branchId, tableId, customerName, status, totalAmount, note, paymentMethod, paymentStatus), `OrderItemEntity`
- **Repositories**: `OrderRepository`, `OrderItemRepository` with tenant-scoped query methods
- **Services**: `OrderService` (createOrder, updateOrderStatus, getAllOrders, getOrder), `KitchenService` (getKitchenOrders), `OrderEventPublisher` (publishes to Kafka)
- **Controllers**: `OrderController` (`/api/orders`), `KitchenController` (`/api/kitchen`)
- **DTOs**: `CreateOrderRequest`, `CreateOrderResponse`, `UpdateOrderStatusRequest`, `KitchenOrderResponse`
- **Order statuses**: `PENDING, ACCEPTED, PREPARING, READY, DELIVERED, PAID, CANCELLED`

### 3.3 Menu Service
- **Entities**: `CategoryEntity`, `ProductEntity`
- **Repositories**: `CategoryRepository`, `ProductRepository`
- **Services**: `MenuService` (createCategory, createProduct, getFullMenu)
- **Controllers**: `MenuController` (`/api/menu`), `ProductController` (`/api/products`), `CategoryController` (`/api/categories`)
- **DTOs**: `MenuResponse`, `CreateCategoryRequest`, `CreateProductRequest`

### 3.4 QR Service
- **Service**: `QrGeneratorService` (getQrForTable, exportPng, exportPdf) — uses RestTemplate to call merchant-service for table/merchant info
- **Controller**: `QrController` (`/api/qr`)
- **DTOs**: `QrMetadataResponse`, `QrExportRequest`
- **QR URL format**: `https://qrserve.com/menu/{merchantSlug}/{branchId}/{tableId}`

### 3.5 Auth Service
- **Entity**: `UserEntity` (UUID id, UUID merchantId, name, email, passwordHash, UserRole role, enabled)
- **Service**: `AuthService` (login, refreshToken, createUser, getUserInfo)
- **Controller**: `AuthController` (`/api/auth`) with `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_OWNER')")` on createUser
- **DTOs**: `LoginRequest`, `LoginResponse`, `RefreshRequest`, `UserInfoResponse`, `CreateUserRequest`
- **Roles**: `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER, WAITER, KITCHEN, CASHIER, CUSTOMER`

### 3.6 Shared Modules
- **Events**: `OrderCreatedEvent`, `OrderStatusUpdatedEvent`, `CustomerRequestEvent`, `WaiterAlertEvent`
- **Security**: `SecurityConfig` (JWT filter, method security enabled, permitAll for `/api/auth/**`), `JwtTokenProvider`, `JwtAuthenticationFilter`, `UserPrincipal`, `UserRole`
- **Common**: `TenantContext` (ThreadLocal tenant), `TraceContext` (MDC-based tracing)
- **Exceptions**: `GlobalExceptionHandler`, `ResourceNotFoundException`, `BusinessException`, `UnauthorizedException`

### 3.7 API Gateway
- Routes: `/api/auth/**` → auth-service, `/api/merchants/**`, `/api/branches/**`, `/api/tables/**`, `/api/waiters/**`, `/api/table-assignments/**`, `/api/customer-requests/**` → merchant-service, `/api/orders/**`, `/api/kitchen/**` → order-service, `/api/categories/**`, `/api/products/**`, `/api/menu/**` → menu-service, `/api/qr/**` → qr-service, `/api/analytics/**` → analytics-service, `/ws/**` → notification-service

---

## 4. Identified Gaps

### 4.1 QR & Menu Resolution

| # | Gap | Details |
|---|---|---|
| G1 | **No QR payload resolution endpoint** | The task requires QR payload to resolve to `{ "merchantId": Long, "branchId": Long, "tableId": Long, "signature": String }`. Currently, `QrController` only generates QR codes; there is no endpoint to **decode/resolve** a QR payload back to table context. |
| G2 | **No slug-based menu resolution** | The task requires `GET /api/v1/public/menu/{merchantSlug}/{branchSlug}/{tableNumber}`. Currently, `MenuController` only supports `GET /api/menu/{merchantId}` (by UUID, not slug). The frontend `CustomerMenuPage.tsx` explicitly notes this as a known limitation: "Public menu requires merchant resolution by slug." |
| G3 | **BranchEntity has no slug field** | `BranchEntity` lacks a `slug` column, which is required for slug-based resolution (`{merchantSlug}/{branchSlug}/{tableNumber}`). |
| G4 | **No QR payload DTO** | No shared DTO exists for the QR payload resolution response. Should be placed in `:shared:common` or `:shared:events`. |

### 4.2 Waiter Module Backend

| # | Gap | Details |
|---|---|---|
| G5 | **No `/api/v1/tables/{tableId}/assign-waiter` endpoint** | The task requires `POST /api/v1/tables/{tableId}/assign-waiter`. Currently, table assignment is at `POST /api/table-assignments` (different URL pattern). |
| G6 | **No `/api/v1/waiters/tasks` endpoint** | The task requires `GET /api/v1/waiters/tasks` to query active waiter tasks. No such endpoint exists. |
| G7 | **No `/api/v1/tables/{tableId}/requests` endpoint** | The task requires `POST /api/v1/tables/{tableId}/requests` for customer requests (CALL_WAITER, REQUEST_WATER, REQUEST_BILL). Currently, customer requests are at `POST /api/customer-requests`. |
| G8 | **No `/api/v1/requests/{requestId}/resolve` endpoint** | The task requires `PATCH /api/v1/requests/{requestId}/resolve`. Currently, request status updates are at `PUT /api/customer-requests/{id}`. |
| G9 | **No CustomerRequestEvent publishing** | `CustomerRequestService.createRequest()` does not publish `CustomerRequestEvent` to Kafka. The event class exists in `:shared:events` but is never published. |
| G10 | **No WaiterAlertEvent publishing** | `WaiterAlertEvent` exists in `:shared:events` but is never published by any service. |

### 4.3 Order Lifecycle Transitions

| # | Gap | Details |
|---|---|---|
| G11 | **Order status flow mismatch** | Current statuses: `PENDING, ACCEPTED, PREPARING, READY, DELIVERED, PAID, CANCELLED`. Task requires: `CREATED → PREPARING → READY → SERVED → COMPLETED`. The `OrderEntity` comment and `UpdateOrderStatusRequest` comment list the old statuses. |
| G12 | **No status transition validation** | `OrderService.updateOrderStatus()` accepts any status string without validating the transition is valid (e.g., cannot go from `CREATED` directly to `COMPLETED`). |
| G13 | **No SERVED status handling** | The `updateTableStatus` call in `OrderService` only handles `DELIVERED` and `PAID` to set table to `AVAILABLE`. The new `SERVED` and `COMPLETED` statuses are not handled. |

### 4.4 RBAC Guarding

| # | Gap | Details |
|---|---|---|
| G14 | **No @PreAuthorize on merchant-service controllers** | `MerchantController`, `BranchController`, `TableController`, `WaiterController`, `TableAssignmentController`, `CustomerRequestController` have **no** `@PreAuthorize` annotations. The navigation matrix in `src/lib/navigation.ts` defines role-based access but the backend does not enforce it. |
| G15 | **No @PreAuthorize on order-service controllers** | `OrderController` and `KitchenController` have no `@PreAuthorize` annotations. |
| G16 | **No @PreAuthorize on menu-service controllers** | `MenuController`, `ProductController`, `CategoryController` have no `@PreAuthorize` annotations. |
| G17 | **No @PreAuthorize on qr-service controller** | `QrController` has no `@PreAuthorize` annotations. |
| G18 | **RBAC matrix not enforced** | The frontend `navigation.ts` defines roles: `SUPER_ADMIN, MERCHANT_OWNER, BRANCH_MANAGER, WAITER, KITCHEN, CASHIER, CUSTOMER`. The backend only has `@PreAuthorize` on `AuthController.createUser`. |

### 4.5 API Gateway Routes

| # | Gap | Details |
|---|---|---|
| G19 | **No `/api/v1/` routes in gateway** | The API gateway only routes `/api/...` (without `/v1/`). The task requires all new controllers to expose endpoints under `/api/v1/...`. |
| G20 | **No public route for menu resolution** | The gateway has no route for public (unauthenticated) menu access by slug. |

### 4.6 Shared DTOs / Events

| # | Gap | Details |
|---|---|---|
| G21 | **No shared QR payload DTO** | No DTO in `:shared:common` or `:shared:events` for QR payload resolution. |
| G22 | **No shared OrderStatus enum** | Order statuses are string literals scattered across services. Should be centralized in `:shared:common`. |
| G23 | **No shared CustomerRequestType enum** | Request types are string literals. Should be centralized. |

### 4.7 Multi-Tenant Isolation

| # | Gap | Details |
|---|---|---|
| G24 | **TableService.getAllTables() not tenant-scoped** | `TableService.getAllTables()` calls `tableRepository.findAll()` without filtering by merchant/branch. |
| G25 | **OrderService.getAllOrders() not tenant-scoped** | `OrderService.getAllOrders()` calls `orderRepository.findAll()` without filtering. |
| G26 | **MerchantService.getAllMerchants() not tenant-scoped** | `MerchantService.getAllMerchants()` returns all merchants without filtering. (May be intentional for SUPER_ADMIN, but lacks RBAC guard.) |

---

## 5. Navigation Matrix (RBAC Reference)

From `src/lib/navigation.ts` and `src/router/ProtectedRoute.tsx`:

| Role | Accessible Routes |
|---|---|
| `SUPER_ADMIN` | `/admin/dashboard`, `/admin/merchants`, `/admin/branches`, `/admin/users`, `/admin/tables`, `/admin/waiters`, `/admin/analytics`, `/admin/subscriptions`, `/admin/settings` |
| `MERCHANT_OWNER` | `/merchant/dashboard`, `/merchant/branches`, `/merchant/tables`, `/merchant/menu`, `/merchant/orders`, `/merchant/waiters`, `/merchant/analytics`, `/merchant/settings` |
| `BRANCH_MANAGER` | `/branch/dashboard`, `/branch/orders`, `/branch/tables`, `/branch/waiters`, `/branch/kitchen`, `/branch/reports` |
| `WAITER` | `/waiter/dashboard`, `/waiter/tables`, `/waiter/orders`, `/waiter/requests` |
| `KITCHEN` | `/kitchen/dashboard`, `/kitchen/incoming`, `/kitchen/preparing`, `/kitchen/ready` |
| `CASHIER` | (not in navigation but in UserRole enum) |
| `CUSTOMER` | (public menu access, no admin routes) |

---

## 6. Implementation Plan

### Phase 2A: Shared DTOs & Enums (`:shared:common`)
1. Add `OrderStatus` enum: `CREATED, PREPARING, READY, SERVED, COMPLETED, CANCELLED`
2. Add `CustomerRequestType` enum: `CALL_WAITER, REQUEST_WATER, REQUEST_BILL, ASSISTANCE`
3. Add `QrPayloadResponse` DTO: `{ merchantId, branchId, tableId, signature }`

### Phase 2B: Merchant Service Extensions
1. Add `slug` column to `BranchEntity` (backwards-compatible, nullable initially)
2. Add `GET /api/v1/public/menu/{merchantSlug}/{branchSlug}/{tableNumber}` endpoint
3. Add `POST /api/v1/tables/{tableId}/assign-waiter` endpoint
4. Add `GET /api/v1/waiters/tasks` endpoint
5. Add `POST /api/v1/tables/{tableId}/requests` endpoint
6. Add `PATCH /api/v1/requests/{requestId}/resolve` endpoint
7. Add `CustomerRequestEvent` publishing in `CustomerRequestService`
8. Add `@PreAuthorize` annotations on all merchant-service controllers

### Phase 2C: Order Service Extensions
1. Update `OrderEntity` status comment to reflect new lifecycle
2. Add status transition validation in `OrderService.updateOrderStatus()`
3. Handle `SERVED` and `COMPLETED` statuses in table status updates
4. Add `@PreAuthorize` annotations on order-service controllers

### Phase 2D: Menu Service Extensions
1. Add slug-based menu resolution endpoint
2. Add `@PreAuthorize` annotations on menu-service controllers

### Phase 2E: QR Service Extensions
1. Add QR payload resolution endpoint
2. Add `@PreAuthorize` annotations on qr-service controller

### Phase 2F: API Gateway Updates
1. Add routes for `/api/v1/...` endpoints
2. Add public route for menu resolution

### Phase 2G: Frontend API Client Updates
1. Add `/api/v1/` endpoint calls to `src/lib/api.ts`

---

## 7. Risk Assessment

| Risk | Severity | Mitigation |
|---|---|---|
| Breaking existing API consumers | Medium | New `/api/v1/` endpoints are additive; old `/api/` endpoints remain |
| Cross-service import violations | High | All shared DTOs go in `:shared:common` or `:shared:events`; no direct service-to-service class imports |
| Database migration for BranchEntity.slug | Low | Add as nullable column; populate via `@PrePersist` if blank |
| Order status enum change | Medium | Keep string-based status; add validation layer; don't change existing column type |
| RBAC over-restriction | Medium | Public endpoints (menu resolution, order creation, customer requests) remain unauthenticated |
