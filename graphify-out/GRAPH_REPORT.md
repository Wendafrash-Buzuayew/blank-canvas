# Graph Report - blank-canvas  (2026-09-04)

## Corpus Check
- 141 files · ~186,420 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 2198 nodes · 6432 edges · 125 communities (93 shown, 32 thin omitted)
- Extraction: 94% EXTRACTED · 6% INFERRED · 0% AMBIGUOUS · INFERRED: 401 edges (avg confidence: 0.81)
- Token cost: 373,077 input · 0 output

## Community Hubs (Navigation)
- Super App Auth & Token Provisioning
- Cross-Service DTOs & Lombok
- Frontend API Client & Merchant Mgmt
- Table QR Provisioning & Controller
- Order Service Business Logic
- Merchant/Branch/Table Entities & Tenant Context
- Admin CRUD Hooks & Pages
- Merchant Dashboard Components & Types
- Tenant Isolation Integration Test
- Merchant Settings Resolution
- Live Dashboard Pages & Urgency
- EMVCo QR Payload Generation
- App Shell, Routing & Phase Flags
- Public/Fallback Controllers & Routing
- Order/Category Entities & Kitchen Service
- Tenant Host & Role Hierarchy Tests
- Merchant/Table/Menu Controllers
- Table Assignment Controller & Service
- Auth Service User Management
- Product/Category Controller Tenant Tests
- QR Signature Service & URL Tests
- QR Generator Service (Export/Fetch)
- STOMP Auth Interceptor & Tests
- Domain Event Publishers & Listeners
- Microservice Application Entrypoints
- Waiter Entity & Service
- Codebase Review Findings & Migration Plan
- Gateway Tenant Resolution & Caching
- Customer Request Controller & Service
- Menu Category/Product DTOs & Service
- App Router & Landing/Analytics Pages
- Design System Tokens & Components
- Slug Normalization Utility & Tests
- Public Menu URL Builder & Tests
- QR Designer & Table Management UI
- Frontend Dev Tooling Dependencies
- Menu Category Controller & Tests
- Analytics Controller & Service
- Auth Controller Endpoints
- Tenant Context Filter Tests
- Frontend Runtime Dependencies
- TypeScript Compiler Config
- Customer Cart & Ordering UI
- RestTemplate Config Beans
- Waiter Controller & Service
- Realtime STOMP Hooks & Client
- Redis Config & Event Publisher
- Global Exception Handler
- Terminal Map Service Tests
- Order Controller Endpoints
- Multi-Tenant Subdomain & QR Design Docs
- Phase 1 Frontend Shell Design Docs
- Dev Fake Super App Auth Port
- Product Repository Queries
- Event Publish Methods & Trace Context
- JWT & Tenant Context Filters
- Remediation Plan Tasks (Security Fixes)
- Service Application Configs (YAML)
- Security Reviewer Agent & Smoke Test Skill
- QR PNG Rendering & Tests
- Order/Table Status Client Tests
- Tenant Resolution Filter Tests
- Spring Security Config
- Merchant Settings Resolver Tests
- Branch/Merchant Service & Exceptions
- STOMP WebSocket Config
- Gradle Classpath Resolver Script
- Kafka Producer Config
- Kafka Consumer Config
- QR Controller Endpoints
- UserPrincipal (Spring Security)
- Super App Payments Design Spec
- Frontend Tenant Slug Parsing
- Backend README & K8s Ingress Config
- Realtime WebSocket Client Core
- Table Assignment V1 Controller
- Tenant Smoke Test Script Internals
- Postgres MCP Server Config
- Verify-Backend Harness (Script+Skill)
- Terminal Label Generation & Tests
- Super App Auth & QR Plan
- Gap Analysis Report Sections
- Kafka/Redis Domain Event Listeners
- Kitchen Controller Endpoint
- npm Scripts
- Order Session Local Storage
- Customer Request & Waiter Request Types
- Frontend Optimization Summary
- STOMP Destination Constants
- Terminal Map Entity
- Table QR Provisioning Plan Doc
- Package Metadata Fields
- Customer Order Progress UI
- Discovery Service (Eureka Server)
- Gradle Wrapper Script
- Kafka Consumer Config Test
- K8s Redis Deployment
- Vite Env Type Declarations
- K8s Backend App Deployment
- Framer Motion Dependency
- SockJS Client Dependency
- STOMP.js Dependency
- Tailwind Vite Plugin Dependency
- Canvas Confetti Types Dependency
- AI Studio Scaffold README
- Gradle Module Dependency Rules (Doc)
- Multi-Tenant Isolation Gaps (Doc)
- Auth Service Autoscaler (HPA)
- Merchant Service Autoscaler (HPA)
- Order Service Autoscaler (HPA)
- API Gateway K8s Service
- Auth Service K8s Service
- Kafka K8s Service
- Merchant Service K8s Service
- Order Service K8s Service
- Postgres K8s External Service
- Order Controller Patch Mapping
- Reactive ServerHttpRequest Type

## God Nodes (most connected - your core abstractions)
1. `useAuth()` - 56 edges
2. `TenantIsolationIT` - 52 edges
3. `JwtTokenProvider` - 41 edges
4. `ResourceNotFoundException` - 37 edges
5. `QrSignatureService` - 36 edges
6. `CustomerRequestEntity` - 31 edges
7. `MerchantEntity` - 31 edges
8. `UserPrincipal` - 30 edges
9. `TableAssignmentEntity` - 30 edges
10. `TableEntity` - 30 edges

## Surprising Connections (you probably didn't know these)
- `Hardcoded Secret Defaults` --conceptually_related_to--> `backend docker-compose.yml`  [INFERRED]
  .claude/agents/qrserve-security-reviewer.md → backend/docker-compose.yml
- `Realtime and Connection State Indicators` --semantically_similar_to--> `Real-Time WebSocket Channels`  [INFERRED] [semantically similar]
  DESIGN.md → backend/README.md
- `verify-backend Skill` --references--> `JUnitRunner`  [EXTRACTED]
  .claude/skills/verify-backend/SKILL.md → backend/scripts/JUnitRunner.java
- `QRServe Backend README` --references--> `backend/.env.example`  [EXTRACTED]
  backend/README.md → .claude/skills/tenant-smoke-test/SKILL.md
- `QRServe App Shell HTML` --conceptually_related_to--> `Route-Level Code Splitting`  [INFERRED]
  index.html → docs/frontend-optimization-summary.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Shared Fail-Fast Secrets Contract (JWT_SECRET, QR_SIGNATURE_SECRET, PUBLIC_BASE_DOMAIN)** — backend_analytics_service_src_main_resources_application, backend_api_gateway_src_main_resources_application, backend_auth_service_src_main_resources_application, backend_menu_service_src_main_resources_application, backend_merchant_service_src_main_resources_application, backend_notification_service_src_main_resources_application, backend_order_service_src_main_resources_application, backend_qr_service_src_main_resources_application, backend_docker_compose, backend_k8s_deployment [EXTRACTED 1.00]
- **Postgres Database-per-Service Provisioning** — backend_k8s_postgres_deployment, backend_auth_service_src_main_resources_application, backend_merchant_service_src_main_resources_application, backend_menu_service_src_main_resources_application, backend_order_service_src_main_resources_application, backend_qr_service_src_main_resources_application, backend_notification_service_src_main_resources_application, backend_analytics_service_src_main_resources_application [EXTRACTED 1.00]
- **QRServe Product Contexts Spanning Design and Backend Roles** — design_four_physical_contexts, backend_readme_role_permission_matrix, design_kitchen_ticket_component, backend_readme_websocket_channels [INFERRED 0.75]
- **Tenant Isolation CI Gate** — docs_superpowers_plans_2026_08_18_multi_tenant_subdomains_and_qr_tenantisolationit, docs_superpowers_specs_2026_08_18_multi_tenant_subdomain_qr_design_two_merchant_isolation_testing_gate, docs_superpowers_plans_2026_08_18_codebase_review_remediation_smoke_tenant_isolation_script [INFERRED 0.85]
- **QR URL Drift Bug Pattern** — docs_superpowers_plans_2026_08_18_multi_tenant_subdomains_and_qr_publicmenuurl_builder, docs_superpowers_specs_2026_08_18_multi_tenant_subdomain_qr_design_broken_qr_defect, docs_superpowers_specs_2026_08_20_superapp_miniapp_payments_design_qr_provisioning_emvco, docs_superpowers_specs_2026_08_20_superapp_miniapp_payments_design_tableqr_entity [INFERRED 0.80]
- **Phase 1 Feature Gating Mechanism** — docs_superpowers_specs_2026_08_27_mini_app_phase1_decoupling_design_phase_flag_decision, docs_superpowers_plans_2026_08_27_phase1_frontend_shell_phase_ts, docs_superpowers_plans_2026_08_27_superapp_auth_and_qr_superapp_ts, docs_superpowers_plans_2026_08_27_phase1_frontend_shell_approuter [INFERRED 0.85]
- **Single-Builder-One-Place Pattern for URLs and Payment Payloads** — docs_superpowers_plans_2026_08_18_multi_tenant_subdomains_and_qr_qrsignatureservice_rotation, docs_superpowers_plans_2026_08_20_table_qr_provisioning_and_merchant_settings_emvco_impl, docs_superpowers_plans_2026_08_20_table_qr_provisioning_and_merchant_settings_tableqrprovisioningservice [INFERRED 0.85]

## Communities (125 total, 32 thin omitted)

### Community 0 - "Super App Auth & Token Provisioning"
Cohesion: 0.06
Nodes (21): BranchProvisionResponse, MerchantProvisionResponse, SuperAppAuthPort, SuperAppMerchantClaim, HttpHeaders, SuperAppProvisioningService, SuperAppProvisioningServiceTest, Override (+13 more)

### Community 1 - "Cross-Service DTOs & Lombok"
Cohesion: 0.09
Nodes (40): PopularItemDto, DailySalesPoint, RevenueAnalyticsResponse, TodayAnalyticsResponse, LoginResponse, RefreshRequest, SuperAppExchangeRequest, CategoryDto (+32 more)

### Community 2 - "Frontend API Client & Merchant Mgmt"
Cohesion: 0.05
Nodes (62): LoginModal(), LoginModalProps, AuthContext, AuthContextType, AuthProvider(), AuthUser, mapUserInfoToAuthUser(), useCreateMerchant() (+54 more)

### Community 3 - "Table QR Provisioning & Controller"
Cohesion: 0.08
Nodes (16): GetMapping, PostMapping, RequestMapping, RestController, TableController, Entity, PrePersist, TableQrEntity (+8 more)

### Community 4 - "Order Service Business Logic"
Cohesion: 0.07
Nodes (23): UpdateTableStatusRequest, HttpHeaders, OrderService, ProductInfo, TableInfo, OrderServiceMenuLookupTest, allowedNext(), canTransitionTo() (+15 more)

### Community 5 - "Merchant/Branch/Table Entities & Tenant Context"
Cohesion: 0.07
Nodes (20): PostMapping, RequestMapping, RestController, PublicCustomerRequestController, BranchEntity, Entity, PrePersist, Entity (+12 more)

### Community 6 - "Admin CRUD Hooks & Pages"
Cohesion: 0.09
Nodes (41): EntitySelect(), EntitySelectProps, EmptyState(), ErrorState(), SKELETON_COLS, Spinner(), useCreateBranch(), useCreateCategory() (+33 more)

### Community 7 - "Merchant Dashboard Components & Types"
Cohesion: 0.09
Nodes (39): SuperAdminView(), SuperAdminViewProps, KitchenDisplaySystem(), KitchenDisplaySystemProps, AnalyticsMetrics, AnalyticsView(), AnalyticsViewProps, PopularItem (+31 more)

### Community 8 - "Tenant Isolation Integration Test"
Cohesion: 0.10
Nodes (8): TenantIsolationIT, com.qrserve.merchant.entity.TableEntity, com.qrserve.merchant.service.MerchantEventPublisher, org.junit.jupiter.api.Test, org.springframework.boot.test.context.SpringBootTest, org.springframework.test.context.ActiveProfiles, org.springframework.test.web.servlet.MockMvc, org.springframework.web.context.WebApplicationContext

### Community 9 - "Merchant Settings Resolution"
Cohesion: 0.11
Nodes (18): ResolvedMerchantSettings, Entity, PrePersist, PreUpdate, MerchantSettingsEntity, MerchantSettingsRepository, MerchantSettingsResolver, SettingsRow (+10 more)

### Community 10 - "Live Dashboard Pages & Urgency"
Cohesion: 0.13
Nodes (32): Navbar(), useAuth(), useBranches(), useKitchenOrders(), useMerchant(), useOrders(), useResolveRequest(), useTables() (+24 more)

### Community 11 - "EMVCo QR Payload Generation"
Cohesion: 0.11
Nodes (5): Emvco, EmvcoMerchant, EmvcoPayload, TerminalLabel, EmvcoPayloadTest

### Community 12 - "App Shell, Routing & Phase Flags"
Cohesion: 0.12
Nodes (23): DashboardLayout(), DashboardLayoutProps, MobileBottomNav(), Sidebar(), SidebarProps, getNavigationForRole(), getRoleHomeRoute(), NavItem (+15 more)

### Community 13 - "Public/Fallback Controllers & Routing"
Cohesion: 0.11
Nodes (19): FallbackController, GetMapping, RequestMapping, RestController, MenuController, BranchController, GetMapping, PostMapping (+11 more)

### Community 14 - "Order/Category Entities & Kitchen Service"
Cohesion: 0.11
Nodes (17): CategoryEntity, Entity, PrePersist, CategoryRepository, Entity, PrePersist, PreUpdate, OrderEntity (+9 more)

### Community 15 - "Tenant Host & Role Hierarchy Tests"
Cohesion: 0.10
Nodes (5): TenantHost, TenantHostTest, TenantSlugResolverWiringTest, AuthServiceRoleHierarchyTest, org.junit.jupiter.api.DisplayName

### Community 16 - "Merchant/Table/Menu Controllers"
Cohesion: 0.09
Nodes (18): DeleteMapping, PutMapping, GetMapping, PostMapping, PutMapping, RequestMapping, RestController, MerchantController (+10 more)

### Community 17 - "Table Assignment Controller & Service"
Cohesion: 0.12
Nodes (12): GetMapping, PostMapping, PutMapping, RequestMapping, RestController, TableAssignmentController, AssignTableRequest, Entity (+4 more)

### Community 18 - "Auth Service User Management"
Cohesion: 0.10
Nodes (18): CreateUserRequest, LoginRequest, UserInfoResponse, Entity, PrePersist, PreUpdate, UserEntity, UserRepository (+10 more)

### Community 19 - "Product/Category Controller Tenant Tests"
Cohesion: 0.14
Nodes (14): GetMapping, PostMapping, PutMapping, RequestMapping, RestController, ProductController, ProductControllerTest, com.qrserve.menu.dto.CreateProductRequest (+6 more)

### Community 20 - "QR Signature Service & URL Tests"
Cohesion: 0.12
Nodes (3): TableQrUrlTest, QrSignatureService, QrSignatureServiceTest

### Community 21 - "QR Generator Service (Export/Fetch)"
Cohesion: 0.13
Nodes (7): QrExportRequest, BranchInfo, HttpHeaders, MerchantInfo, QrGeneratorService, TableInfo, TableQrInfo

### Community 22 - "STOMP Auth Interceptor & Tests"
Cohesion: 0.17
Nodes (10): Override, StompAuthInterceptor, StompPrincipal, StompAuthInterceptorTest, java.security.Principal, java.util.regex.Pattern, org.springframework.messaging.Message, org.springframework.messaging.MessageChannel (+2 more)

### Community 23 - "Domain Event Publishers & Listeners"
Cohesion: 0.20
Nodes (17): MerchantEventPublisher, TableQrEventPublisher, TenantCacheInvalidator, JwtHandshakeInterceptor, RedisEventSubscriber, TerminalMapListener, TerminalMapService, OrderEventPublisher (+9 more)

### Community 24 - "Microservice Application Entrypoints"
Cohesion: 0.15
Nodes (13): AnalyticsServiceApplication, ApiGatewayApplication, AuthServiceApplication, MenuServiceApplication, MerchantServiceApplication, NotificationServiceApplication, OrderServiceApplication, QrServiceApplication (+5 more)

### Community 25 - "Waiter Entity & Service"
Cohesion: 0.14
Nodes (10): RequestMapping, RestController, WaiterTaskV1Controller, Entity, PrePersist, PreUpdate, WaiterEntity, WaiterRepository (+2 more)

### Community 26 - "Codebase Review Findings & Migration Plan"
Cohesion: 0.08
Nodes (29): QRServe Codebase Review, assignWaiterToTable Missing branchId Validation, Resilience4j Circuit Breaker Never Applied, ddl-auto update With Flyway Disabled, Deprecated Jackson 2 Kafka Serializers, End-User JWT Forwarded Downstream, Gateway Route Ordering Shadows /api/v1/auth/**, getTodayMetrics Ignores merchantId (Cross-Tenant Leak) (+21 more)

### Community 27 - "Gateway Tenant Resolution & Caching"
Cohesion: 0.14
Nodes (11): Override, TenantResolutionGlobalFilter, Builder, TenantSlugResolver, TenantCacheKeys, org.springframework.cloud.gateway.filter.GlobalFilter, org.springframework.core.Ordered, org.springframework.data.redis.core.ReactiveStringRedisTemplate (+3 more)

### Community 28 - "Customer Request Controller & Service"
Cohesion: 0.15
Nodes (10): CustomerRequestController, GetMapping, PostMapping, RequestMapping, RestController, CustomerRequestEntity, Entity, PrePersist (+2 more)

### Community 29 - "Menu Category/Product DTOs & Service"
Cohesion: 0.14
Nodes (8): CreateCategoryRequest, CreateProductRequest, UpdateCategoryRequest, UpdateProductRequest, MenuService, ResourceNotFoundException, org.springframework.cache.annotation.Cacheable, org.springframework.cache.annotation.CacheEvict

### Community 30 - "App Router & Landing/Analytics Pages"
Cohesion: 0.10
Nodes (18): App(), LandingPage(), LandingPageProps, usePopularItems(), useRevenueAnalytics(), queryClient, AnalyticsPage(), AnalyticsPage (+10 more)

### Community 31 - "Design System Tokens & Components"
Cohesion: 0.09
Nodes (24): QRServe Design System, Accessibility Requirements, --color-brand QRServe Crimson, Colour Token System, Compliance Ledger, Button Component Spec, Chip/Badge Component Spec, Modal/Dialog/Sheet Component Spec (+16 more)

### Community 33 - "Public Menu URL Builder & Tests"
Cohesion: 0.17
Nodes (3): QrTargetUrlTest, PublicMenuUrl, PublicMenuUrlTest

### Community 34 - "QR Designer & Table Management UI"
Cohesion: 0.20
Nodes (17): QRDesigner(), useAssignWaiterV1(), useCreateTable(), useDeleteTable(), useTableQr(), useUpdateTableStatus(), useWaitersLookup(), CreateTableResponse (+9 more)

### Community 35 - "Frontend Dev Tooling Dependencies"
Cohesion: 0.10
Nodes (21): autoprefixer, esbuild, devDependencies, autoprefixer, esbuild, tailwindcss, tsx, @types/node (+13 more)

### Community 36 - "Menu Category Controller & Tests"
Cohesion: 0.15
Nodes (11): CategoryController, DeleteMapping, GetMapping, PostMapping, PutMapping, RequestMapping, RestController, CategoryControllerTest (+3 more)

### Community 37 - "Analytics Controller & Service"
Cohesion: 0.21
Nodes (9): AnalyticsController, GetMapping, RequestMapping, RestController, AnalyticsService, HttpHeaders, com.qrserve.analytics.dto.PopularItemDto, com.qrserve.analytics.dto.RevenueAnalyticsResponse (+1 more)

### Community 38 - "Auth Controller Endpoints"
Cohesion: 0.19
Nodes (11): AuthController, GetMapping, PostMapping, RequestMapping, RestController, com.qrserve.auth.dto.CreateUserRequest, com.qrserve.auth.dto.LoginRequest, com.qrserve.auth.dto.LoginResponse (+3 more)

### Community 39 - "Tenant Context Filter Tests"
Cohesion: 0.28
Nodes (6): TenantContextFilterTest, MockHttpServletRequest, MockHttpServletResponse, org.junit.jupiter.api.AfterEach, org.springframework.mock.web.MockHttpServletRequest, org.springframework.mock.web.MockHttpServletResponse

### Community 40 - "Frontend Runtime Dependencies"
Cohesion: 0.11
Nodes (19): canvas-confetti, lucide-react, dependencies, canvas-confetti, lucide-react, qrcode, react, react-dom (+11 more)

### Community 41 - "TypeScript Compiler Config"
Cohesion: 0.11
Nodes (18): DOM, DOM.Iterable, ES2022, compilerOptions, allowImportingTsExtensions, allowJs, experimentalDecorators, isolatedModules (+10 more)

### Community 42 - "Customer Cart & Ordering UI"
Cohesion: 0.16
Nodes (15): CartLine, CartSheet(), Props, ACTIONS, Props, ServiceDock(), useCreateOrder(), useCreateTableRequest() (+7 more)

### Community 43 - "RestTemplate Config Beans"
Cohesion: 0.18
Nodes (10): AppConfig, RestTemplate, AppConfig, RestTemplate, AppConfig, RestTemplate, AppConfig, RestTemplate (+2 more)

### Community 44 - "Waiter Controller & Service"
Cohesion: 0.18
Nodes (11): DeleteMapping, GetMapping, PostMapping, PutMapping, RequestMapping, RestController, WaiterController, com.qrserve.merchant.dto.CreateWaiterRequest (+3 more)

### Community 45 - "Realtime STOMP Hooks & Client"
Cohesion: 0.22
Nodes (16): eventKey(), LiveEvent, useEventBuffer(), useKitchenStream(), useOrderStream(), useRealtimeStatus(), useReconnectResync(), useStompSubscription() (+8 more)

### Community 46 - "Redis Config & Event Publisher"
Cohesion: 0.19
Nodes (11): RedisTemplate, RedisConfig, RedisEventPublisher, LettuceConnectionFactory, MessageListenerAdapter, org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory, org.springframework.data.redis.connection.RedisConnectionFactory, org.springframework.data.redis.core.RedisTemplate (+3 more)

### Community 47 - "Global Exception Handler"
Cohesion: 0.32
Nodes (7): ErrorResponse, GlobalExceptionHandler, ResponseEntity, org.springframework.security.core.AuthenticationException, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.RestControllerAdvice, org.springframework.web.bind.MethodArgumentNotValidException

### Community 49 - "Order Controller Endpoints"
Cohesion: 0.20
Nodes (9): GetMapping, PostMapping, RequestMapping, RestController, OrderController, UpdateOrderStatusRequest, com.qrserve.order.dto.CreateOrderRequest, com.qrserve.order.entity.OrderEntity (+1 more)

### Community 50 - "Multi-Tenant Subdomain & QR Design Docs"
Cohesion: 0.23
Nodes (16): Multi-Tenant Subdomains and QR Generation Implementation Plan, PublicMenuUrl Builder, QrSignatureService Per-Tenant Key and Rotation Overlap, Slugs Normaliser Utility, Host/JWT Tenant Precedence Rule, TenantContextFilter, TenantIsolationIT Test Gate, TenantResolutionGlobalFilter (+8 more)

### Community 51 - "Phase 1 Frontend Shell Design Docs"
Cohesion: 0.22
Nodes (16): AppRouter requiresPhase2 Route Wiring, CustomerMenuPage Phase-Gated Ordering UI, DashboardLayout Mobile Shell, LoginPage Phase Gate, MobileBottomNav Component, src/lib/navigation.ts Phase-Aware Navigation Table, src/lib/phase.ts Phase Gating Logic, Phase 1 Frontend Shell & Customer Menu Gating Implementation Plan (+8 more)

### Community 52 - "Dev Fake Super App Auth Port"
Cohesion: 0.22
Nodes (4): DevFakeSuperAppAuthPort, Override, DevFakeSuperAppAuthPortTest, com.fasterxml.jackson.databind.ObjectMapper

### Community 53 - "Product Repository Queries"
Cohesion: 0.21
Nodes (7): Entity, PrePersist, PreUpdate, ProductEntity, ProductRepository, org.springframework.data.jpa.repository.Modifying, org.springframework.data.jpa.repository.Query

### Community 55 - "JWT & Tenant Context Filters"
Cohesion: 0.28
Nodes (7): JwtAuthenticationFilter, Override, TenantContextFilter, jakarta.servlet.FilterChain, jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse, org.springframework.web.filter.OncePerRequestFilter

### Community 56 - "Remediation Plan Tasks (Security Fixes)"
Cohesion: 0.17
Nodes (15): Gateway CircuitBreaker and GET-only Retry, Gateway /api/v1/auth Routing Fix, javac + JUnit Verification Harness (Gradle Loopback Workaround), Fail-Fast JWT Secret Hygiene, Kafka Serializer Migration to Jackson 3, Codebase Review Remediation Implementation Plan, Role-Based @PreAuthorize Authorization Matrix, QR Signature Secret Hardening (+7 more)

### Community 57 - "Service Application Configs (YAML)"
Cohesion: 0.48
Nodes (14): analytics-service application.yml, api-gateway application.yml, auth-service application.yml, discovery-service application.yml, backend docker-compose.yml, k8s Deployment Manifests, k8s Postgres Deployment + DB Init, menu-service application.yml (+6 more)

### Community 58 - "Security Reviewer Agent & Smoke Test Skill"
Cohesion: 0.14
Nodes (14): backend/.env.example, smoke-tenant-isolation.sh, QRServe Security Reviewer Agent, Hardcoded Secret Defaults, End-User JWT Forwarded as Service-to-Service Auth, Missing @PreAuthorize on Mutating Endpoints, Optional QR/Tenant Signature Validation, permitAll() Ordering in SecurityConfig (+6 more)

### Community 60 - "Order/Table Status Client Tests"
Cohesion: 0.18
Nodes (10): TRACKING_TTL_MS, KITCHEN_ACTIVE, mostAdvancedStatus(), ORDER_STATUS, OrderStatus, RANK, statusRank(), TABLE_STATUS (+2 more)

### Community 61 - "Tenant Resolution Filter Tests"
Cohesion: 0.38
Nodes (4): HttpHeaders, TenantResolutionGlobalFilterTest, org.springframework.cloud.gateway.filter.GatewayFilterChain, org.springframework.mock.web.server.MockServerWebExchange

### Community 62 - "Spring Security Config"
Cohesion: 0.23
Nodes (7): SecurityConfig, org.springframework.boot.CommandLineRunner, org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity, org.springframework.security.config.annotation.web.builders.HttpSecurity, org.springframework.security.crypto.password.PasswordEncoder, org.springframework.security.web.SecurityFilterChain, org.springframework.web.cors.CorsConfigurationSource

### Community 64 - "Branch/Merchant Service & Exceptions"
Cohesion: 0.22
Nodes (7): BranchService, MerchantService, PublicMenuResolutionService, BusinessException, com.qrserve.merchant.repository.BranchRepository, com.qrserve.merchant.repository.MerchantRepository, com.qrserve.shared.exceptions.ResourceNotFoundException

### Community 65 - "STOMP WebSocket Config"
Cohesion: 0.23
Nodes (8): Override, StompWebSocketConfig, EnableWebSocketMessageBroker, org.springframework.messaging.simp.config.ChannelRegistration, org.springframework.messaging.simp.config.MessageBrokerRegistry, org.springframework.scheduling.TaskScheduler, StompEndpointRegistry, WebSocketMessageBrokerConfigurer

### Community 66 - "Gradle Classpath Resolver Script"
Cohesion: 0.15
Nodes (10): args, cacheRoot, chosen, classpath, GATEWAY_ARTIFACT_MARKERS, includeGateway, printJunitConsole, printLombokJar (+2 more)

### Community 67 - "Kafka Producer Config"
Cohesion: 0.30
Nodes (6): KafkaTemplate, KafkaProducerConfig, KafkaTemplate, KafkaProducerConfig, org.springframework.context.annotation.Bean, org.springframework.kafka.core.ProducerFactory

### Community 68 - "Kafka Consumer Config"
Cohesion: 0.27
Nodes (6): ConcurrentKafkaListenerContainerFactory, KafkaConsumerConfig, ConcurrentKafkaListenerContainerFactory, KafkaConsumerConfig, org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory, org.springframework.kafka.core.ConsumerFactory

### Community 69 - "QR Controller Endpoints"
Cohesion: 0.24
Nodes (8): GetMapping, PostMapping, RequestMapping, RestController, QrController, com.qrserve.qr.dto.QrExportRequest, com.qrserve.qr.dto.QrMetadataResponse, com.qrserve.qr.service.QrGeneratorService

### Community 70 - "UserPrincipal (Spring Security)"
Cohesion: 0.29
Nodes (4): Override, UserPrincipal, org.springframework.security.core.GrantedAuthority, org.springframework.security.core.userdetails.UserDetails

### Community 71 - "Super App Payments Design Spec"
Cohesion: 0.27
Nodes (12): Every Generated QR Code Is Broken (Defect 1), QR Generation URL Shape and Single Builder Design, Super App Mini-App Payments Design, Double Payment Prevention via Database Constraint, HostPort Super App Bridge Interface, Payment Matcher Cascade, MerchantSettings Configuration, Non-Table Fulfilment (Takeout) Customer Surface (+4 more)

### Community 72 - "Frontend Tenant Slug Parsing"
Cohesion: 0.23
Nodes (8): currentTenantSlug(), MenuRouteParams, MenuTarget, PUBLIC_BASE_DOMAIN, RESERVED_LABELS, resolveMenuTarget(), stripPort(), tenantSlugFromHost()

### Community 73 - "Backend README & K8s Ingress Config"
Cohesion: 0.20
Nodes (11): k8s ConfigMap (qrserve-config), k8s Ingress (proxy-ingress.yml), QRServe Backend README, Hexagonal Architecture / DDD Principles, k8s Deployment Manifest Incompleteness Caveat, Merchant Slug Permanence, Role Permission Matrix (RBAC), Tenant Subdomain Routing Scheme (+3 more)

### Community 75 - "Table Assignment V1 Controller"
Cohesion: 0.29
Nodes (8): PostMapping, RequestMapping, RestController, TableAssignmentV1Controller, com.qrserve.merchant.repository.TableRepository, com.qrserve.merchant.repository.WaiterRepository, com.qrserve.merchant.service.TableAssignmentService, com.qrserve.shared.common.dto.WaiterAssignmentDto

### Community 76 - "Tenant Smoke Test Script Internals"
Cohesion: 0.38
Nodes (9): c_fail(), c_pass(), head(), login(), need(), payload(), smoke-tenant-isolation.sh script, status() (+1 more)

### Community 77 - "Postgres MCP Server Config"
Cohesion: 0.47
Nodes (9): npx, postgres-analytics, postgres-auth, postgres-menu, postgres-merchant, postgres-notification, postgres-order, postgres-qr (+1 more)

### Community 78 - "Verify-Backend Harness (Script+Skill)"
Cohesion: 0.22
Nodes (7): gradle-classpath.mjs, JUnitRunner, verify-backend Skill, @argfile No-BOM Forward-Slash Requirement, Newest-Version-Wins Classpath Resolution, Gradle Loopback Connection Failure, Lombok -processorpath Requirement

### Community 80 - "Super App Auth & QR Plan"
Cohesion: 0.42
Nodes (9): AuthContext.loginWithSuperAppToken, AuthController POST /api/auth/superapp/exchange, DevFakeSuperAppAuthPort, Merchant Auth via Super App + Single Merchant QR Implementation Plan, src/lib/superApp.ts Token Reader, SuperAppAuthPort Interface, SuperAppMerchantClaim Record, SuperAppProvisioningService (+1 more)

### Community 81 - "Gap Analysis Report Sections"
Cohesion: 0.25
Nodes (8): Gap: API Gateway /v1 Routes (G19-G20), Implementation Plan (Phase 2A-2G), Gap: Order Lifecycle Transitions (G11-G13), Gap: QR & Menu Resolution (G1-G4), Gap: RBAC Guarding (G14-G18), Risk Assessment, Gap: Shared DTOs / Events (G21-G23), Gap: Waiter Module Backend (G5-G10)

### Community 83 - "Kitchen Controller Endpoint"
Cohesion: 0.32
Nodes (6): GetMapping, RequestMapping, RestController, KitchenController, com.qrserve.order.dto.KitchenOrderResponse, com.qrserve.order.service.KitchenService

### Community 84 - "npm Scripts"
Cohesion: 0.25
Nodes (8): scripts, build, build:dev, clean, dev, lint, preview, test:unit

### Community 85 - "Order Session Local Storage"
Cohesion: 0.39
Nodes (7): clearTrackedOrder(), parseTrackedOrder(), readTrackedOrder(), storage(), TrackedOrder, TrackedOrderScope, writeTrackedOrder()

### Community 86 - "Customer Request & Waiter Request Types"
Cohesion: 0.33
Nodes (5): CustomerRequestDto, WaiterRequestType, CALL_WAITER, REQUEST_BILL, REQUEST_WATER

### Community 87 - "Frontend Optimization Summary"
Cohesion: 0.33
Nodes (7): useCreateTableRequest Drops QR Signature, Frontend Optimization Summary, Frontend QR Signature Passthrough Fix, Reactive Auth Gating for TanStack Query, Route-Level Code Splitting, Scoped Query Invalidation, QRServe App Shell HTML

### Community 89 - "Terminal Map Entity"
Cohesion: 0.53
Nodes (4): TerminalMapEntity, jakarta.persistence.Entity, jakarta.persistence.PrePersist, jakarta.persistence.Table

### Community 90 - "Table QR Provisioning Plan Doc"
Cohesion: 0.60
Nodes (6): Table QR Provisioning and Merchant Settings Plan, EMVCo Payload Builder Implementation, MerchantSettingsResolver, TableQrProvisioningService, TerminalLabel, order-service TerminalMap Projection

### Community 91 - "Package Metadata Fields"
Cohesion: 0.40
Nodes (4): name, private, type, version

### Community 92 - "Customer Order Progress UI"
Cohesion: 0.50
Nodes (4): OrderProgress(), Props, stepIndex(), STEPS

### Community 94 - "Gradle Wrapper Script"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 96 - "K8s Redis Deployment"
Cohesion: 0.67
Nodes (3): redis-deployment (K8s Deployment), redis-service (K8s Service), redis-cache ExternalName Service

## Ambiguous Edges - Review These
- `redis-service (K8s Service)` → `redis-cache ExternalName Service`  [AMBIGUOUS]
  backend/k8s/service.yml · relation: shares_data_with
- `QR Signature Secret Rotation Procedure` → `k8s Deployment Manifests`  [AMBIGUOUS]
  backend/k8s/deployment.yml · relation: conceptually_related_to

## Knowledge Gaps
- **244 isolated node(s):** `EntitySelectProps`, `CategoryFormState`, `ProductFormState`, `Props`, `LandingPageProps` (+239 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **32 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `redis-service (K8s Service)` and `redis-cache ExternalName Service`?**
  _Edge tagged AMBIGUOUS (relation: shares_data_with) - confidence is low._
- **What is the exact relationship between `QR Signature Secret Rotation Procedure` and `k8s Deployment Manifests`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `QRServe Backend README` connect `Backend README & K8s Ingress Config` to `Tenant Isolation Integration Test`, `Service Application Configs (YAML)`, `Security Reviewer Agent & Smoke Test Skill`?**
  _High betweenness centrality (0.063) - this node is a cross-community bridge._
- **Why does `backend/.env.example` connect `Security Reviewer Agent & Smoke Test Skill` to `Backend README & K8s Ingress Config`?**
  _High betweenness centrality (0.042) - this node is a cross-community bridge._
- **Why does `tenant-smoke-test Skill` connect `Security Reviewer Agent & Smoke Test Skill` to `Codebase Review Findings & Migration Plan`?**
  _High betweenness centrality (0.041) - this node is a cross-community bridge._
- **What connects `EntitySelectProps`, `CategoryFormState`, `ProductFormState` to the rest of the system?**
  _244 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Super App Auth & Token Provisioning` be split into smaller, more focused modules?**
  _Cohesion score 0.05643513789581205 - nodes in this community are weakly interconnected._