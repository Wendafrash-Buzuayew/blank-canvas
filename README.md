# QRServe

Multi-tenant digital menu, QR ordering and payment-standee platform for restaurants, hotels, cafés and bars.

This repository is a monorepo: a React single-page app at the root, and a Spring Boot microservice backend under [`backend/`](backend/).

| | |
|---|---|
| **Frontend** | React 19 · TypeScript · Vite 6 · Tailwind CSS 4 · React Router 7 · TanStack Query |
| **Backend** | Java 17 · Spring Boot · Spring Cloud Gateway · Eureka · Gradle |
| **Data** | PostgreSQL (one database per service) · Redis · Kafka · MinIO |
| **Mobile** | Capacitor (Android / iOS shells around the same web app) |

---

## What it does

One deployment serves many merchants, each with its own branches, menus, tables and staff, isolated at every layer.

- **Guests** scan a table QR to open a branch's menu (`/m/{merchant}/{branch}`), order, track the order live, and pay — no app install
- **Merchant owners** build menus (categories, products, media, discounts), manage branches and tables, design menu templates, and generate printable QR & ETHQR payment standees in five languages
- **Kitchen** works a live order board over WebSocket
- **Waiters** get table assignments and guest call requests in real time
- **Admins** manage merchants, users, reviews and back-office reports

Merchant plans are tiered: the Free tier is capped at one branch and a basic menu QR, while Pro unlocks multi-branch, the multi-language ETHQR payment standee, per-table bulk exports and custom banners.

## Repository layout

```
.
├── src/                    # React app
│   ├── pages/              # Route-level screens (merchant, kitchen, waiter, customer, admin)
│   ├── components/         # Shared UI, menu renderers, QR & standee studio
│   ├── lib/                # Framework-free logic + its `*.test.ts` files
│   ├── hooks/ context/     # Data fetching, auth/tenant context
│   └── router/             # Route table and route guards
├── backend/                # Spring Boot services — see backend/README.md
├── docs/                   # Design specs and implementation plans
├── capacitor.config.ts     # Native shell config
└── vite.config.ts          # Dev server + /api proxy to the gateway
```

## Prerequisites

- **Node.js 20+** and npm — the repo declares no `engines` constraint; Vite 6 needs 18 or newer
- **JDK 17** — the Gradle toolchain pins `JavaLanguageVersion.of(17)`
- **Docker** — runs the backend services
- **PostgreSQL, Redis and Kafka**, reachable from Docker containers. These are *not* declared in `backend/docker-compose.yml`; the services reach them at `host.docker.internal`, so run them separately (locally or as their own containers).

## Quick start

### 1. Backend

```bash
cd backend
cp .env.example .env              # PowerShell: copy .env.example .env
docker compose up -d
```

Fill in `.env` before starting — see [Environment](#environment); the services exit immediately if the required secrets are missing.

This starts Eureka (service discovery), MinIO, the API gateway and the eight business services. Give it a minute, then check they came up:

```bash
docker compose ps
curl http://localhost:8086/actuator/health/readiness   # any service, by its own port
```

Services register with Eureka and the gateway routes to them by name (`lb://menu-service`), so a service that is running but unregistered will surface as a `503` from the gateway rather than a connection error.

### 2. Frontend

From the repository root:

```bash
npm install
npm run dev
```

The app serves on **http://localhost:3000**. Vite proxies `/api` and `/ws` to the gateway at `http://localhost:8081` — override with `VITE_PROXY_TARGET` if your gateway is elsewhere.

> `changeOrigin` is deliberately **false** in the proxy config. Rewriting the `Host` header would erase the tenant subdomain label before the gateway could resolve it, so tenant resolution would silently never fire in development. Don't change it.

## Environment

The backend's required variables live in `backend/.env` (git-ignored — copy from [`backend/.env.example`](backend/.env.example), which documents every one). Four are mandatory and every service **fails fast at startup** without them, by design — the previous hardcoded defaults let anyone forge tokens and QR signatures:

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | Token signing key, shared by all services. Base64, ≥ 32 bytes decoded (`openssl rand -base64 48`). |
| `QR_SIGNATURE_SECRET` | HMAC key for QR payload signatures (`openssl rand -base64 32`). |
| `PUBLIC_BASE_DOMAIN` | Tenant base domain; merchants are served at `{merchantSlug}.${PUBLIC_BASE_DOMAIN}`. |
| `PUBLIC_MENU_DOMAIN` | Domain for path-based menu URLs (`/m/{merchant-slug}/{branch-slug}`). |

For local development, set `PUBLIC_BASE_DOMAIN=localtest.me:3000` and `PUBLIC_URL_SCHEME=http`. `localtest.me` is a public wildcard resolving to loopback, so tenant subdomains such as `http://sunrise.localtest.me:3000` work with no `/etc/hosts` editing.

The frontend needs no environment file.

## Ports

| Port | Service | | Port | Service |
|---|---|---|---|---|
| 3000 | Frontend (Vite dev server) | | 8086 | menu-service |
| 8081 | api-gateway | | 8087 | auth-service |
| 8083 | order-service | | 8088 | qr-service |
| 8084 | notification-service | | 8089 | analytics-service |
| 8085 | merchant-service | | 8090 | back-office-service |
| 8761 | discovery-service (Eureka) | | 9000/9001 | MinIO |

External infrastructure: PostgreSQL `5432`, Redis `6379`, Kafka `9092`.

Each service owns its own database — `qrserve_auth`, `qrserve_merchant`, `qrserve_menu`, `qrserve_order`, `qrserve_qr`, `qrserve_notification`, `qrserve_analytics`, `qrserve_backoffice`.

## Tests and checks

Frontend, from the repository root:

| Script | Does |
|---|---|
| `npm run dev` | Dev server on port 3000, bound to `0.0.0.0` |
| `npm run lint` | `tsc --noEmit` — type-checks the whole frontend |
| `npm run test:unit` | Unit tests for `src/lib` |
| `npm run build` | Production bundle |
| `npm run build:dev` | Bundle in development mode |
| `npm run preview` | Serve the built bundle |
| `npm run clean` | Removes `dist`. Unix-only — it shells out to `rm -rf`, so on Windows delete `dist` by hand or use `Remove-Item -Recurse -Force dist`. |

Backend, from `backend/`:

```bash
./gradlew test                    # all service test suites
./gradlew :menu-service:test      # one service
```

On Windows use the batch wrapper — `.\gradlew.bat test`.

Frontend unit tests are plain TypeScript files executed directly by `tsx`, with no test runner. Each prints `ok` / `FAIL` lines and sets a non-zero exit code on failure. Add a new suite by creating `src/lib/<name>.test.ts` **and** appending it to the `test:unit` script in `package.json` — nothing discovers it automatically.

## Redeploying a backend change

Each service's Dockerfile only copies an already-built jar (`COPY <svc>/build/libs/<svc>-1.0.0-SNAPSHOT-boot.jar app.jar`). Building the image without rebuilding the jar first silently redeploys the old code:

```bash
cd backend
./gradlew :menu-service:bootJar      # Windows: .\gradlew.bat :menu-service:bootJar
docker compose build menu-service
docker compose up -d --force-recreate --no-deps menu-service
```

Container environment is also fixed when the container is created, so editing `docker-compose.yml` or `.env` has no effect until you recreate the container.

## Mobile shells

`capacitor.config.ts` wraps the built web app for Android and iOS. Build the web assets first (`npm run build`), then use the Capacitor CLI (`npx cap sync`, `npx cap open android`).

## Further reading

- [`backend/README.md`](backend/README.md) — architecture, per-service breakdown, API surface, Kubernetes manifests
- [`docs/`](docs/) — design specs and implementation plans
- `backend/postman_collection.json` — importable API collection
