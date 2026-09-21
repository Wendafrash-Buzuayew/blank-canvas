# Deploying QRServe to Kubernetes

Twelve Deployments (ten Spring Boot services, Kafka, and the frontend), fronted
by one Ingress. Everything is applied through kustomize — **never** by
`kubectl apply -f` on individual files, which bypasses the overlay and its
site labels.

```
backend/k8s/
  base/                     every resource, site-agnostic
  overlays/site-bp/         the thing you actually apply
  postgres-deployment.yaml  local/dev only — NOT in the base
  redis-deployment.yaml     local/dev only — NOT in the base
```

`postgres-deployment.yaml` and `redis-deployment.yaml` are mutually exclusive
with `base/data-tier-external.yaml`, which points at the external managed
Postgres and Redis via `ExternalName` Services. Applying both gives you two
things answering to the same Service name.

## Prerequisites

| Requirement | Why |
|---|---|
| `ingress-nginx` controller | The Ingress uses `ingressClassName: nginx` and `nginx.ingress.kubernetes.io/*` annotations. |
| `cert-manager` + a `letsencrypt-prod` ClusterIssuer **with a DNS-01 solver** | The certificate is a wildcard. Wildcards *cannot* be validated over HTTP-01 — with an HTTP-01 issuer the order never completes and the Ingress silently keeps serving its default self-signed cert. |
| Wildcard DNS for `*.qrserve.safaricom.et` | Each merchant is served at `{merchantSlug}.qrserve.safaricom.et`. |
| DNS for `menu.safaricom.et` | The phase-1 digital-menu URL family, and a separate certificate. |
| `metrics-server` | Every HPA scales on CPU utilisation; without it they sit at `<unknown>` and never scale. |
| Reachable external Postgres and Redis | See `base/data-tier-external.yaml`. |

## 1. Namespace

Every manifest currently hardcodes `namespace: default`. To deploy elsewhere,
set it in the overlay rather than editing thirteen files:

```bash
kubectl create namespace qrserve
cd backend/k8s/overlays/site-bp
kustomize edit set namespace qrserve
```

That rewrites the namespace on every resource at build time. Note the Ingress
must be in the same namespace as the Services it routes to.

## 2. Secrets

`base/secrets.example.yaml` is a template. It is deliberately **not** listed in
`base/kustomization.yaml`, so it is never applied — create the real Secret
imperatively so the values never touch git:

```bash
kubectl create secret generic qrserve-secrets \
  --namespace qrserve \
  --from-literal=jwt-secret="$(openssl rand -base64 48)" \
  --from-literal=qr-signature-secret="$(openssl rand -base64 32)" \
  --from-literal=db-password='<the real Postgres password>'
```

- `db-password` **must** match the external Postgres user referenced by every
  service's `DATABASE_USERNAME`.
- Rotating `jwt-secret` invalidates every issued access and refresh token, so
  every user is logged out. Schedule accordingly.
- Rotating `qr-signature-secret` invalidates signatures on **already-printed**
  QR codes. `QR_SIGNATURE_SECRET_PREVIOUS` exists for exactly this: set it to
  the outgoing secret during a rotation so printed table stands keep
  validating, and clear it once reprinting is done.

Non-secret configuration lives in `base/config.yaml` and is committed. Changing
`public-base-domain` changes every QR code generated from then on; codes already
printed keep pointing at the old host, so treat it as permanent once the first
stand is printed.

## 3. Dry run

```bash
# Render and inspect — catches kustomize errors, bad patch targets and
# unresolved resource references. Needs no cluster.
kubectl kustomize backend/k8s/overlays/site-bp

# Schema validation. Note --dry-run=client still needs cluster access, because
# kubectl fetches the OpenAPI schema and the REST mappings from the API server.
# There is no fully offline validation with kubectl alone; use kubeconform if
# you need one in a pre-commit hook.
kubectl apply -k backend/k8s/overlays/site-bp --dry-run=client

# The stronger check: runs admission controllers and defaulting server-side.
kubectl apply -k backend/k8s/overlays/site-bp --dry-run=server

# What would actually change.
kubectl diff -k backend/k8s/overlays/site-bp
```

## 4. Apply and verify

```bash
kubectl apply -k backend/k8s/overlays/site-bp

for d in discovery-service auth-service merchant-service menu-service \
         order-service qr-service notification-service analytics-service \
         back-office-service api-gateway-deployment frontend-deployment; do
  kubectl rollout status "deployment/$d" --timeout=5m
done
```

Health checks:

```bash
# Spring Boot services — the probes hit these paths.
kubectl exec deploy/merchant-service -- \
  wget -qO- localhost:8085/actuator/health/readiness

# Frontend — nginx answers this itself; it does not proxy the backend.
kubectl exec deploy/frontend-deployment -- wget -qO- localhost/healthz

# Eureka should list every service; `lb://` routing in the gateway depends on it.
kubectl exec deploy/api-gateway-deployment -- wget -qO- localhost:8761/eureka/apps

# Certificate — READY should be True. This is the step that fails when the
# ClusterIssuer has no DNS-01 solver.
kubectl get certificate
```

### Service ports

| Service | Port | | Service | Port |
|---|---|---|---|---|
| api-gateway | 8081 | | menu-service | 8086 |
| order-service | 8083 | | auth-service | 8087 |
| notification-service | 8084 | | qr-service | 8088 |
| merchant-service | 8085 | | analytics-service | 8089 |
| discovery-service | 8761 | | back-office-service | 8090 |
| frontend | 80 | | | |

## Routing

`/api` and `/ws` go to the gateway; everything else is the SPA. Those two
prefixes are exhaustive — every route predicate in the gateway's
`application.yml` is `Path=/api/**` or `Path=/ws/**`.

Tenant resolution is unaffected by the split: the gateway derives the tenant
from the `Host` header, and the browser sends the same `Host` on the SPA's
`/api` calls as on the page request itself.

## Things that will surprise you

### package-lock.json is not portable — fix this

222 of the lockfile's 350 `resolved` URLs point at
`http://registry.safaricomet.net/`, the internal mirror. The other 128 point at
`registry.npmjs.org`, so the file was generated across a registry change.

`npm ci` fetches each package from the URL recorded in the lockfile and ignores
the configured registry, so **any build that cannot resolve that host fails** —
including every GitHub-hosted runner. npm reports it as its own internal
`Exit handler never called!` rather than naming the host, which makes it look
like a dependency problem.

Both the frontend `Dockerfile` and `.github/workflows/deploy.yml` work around
it by rewriting the host before `npm ci`. That is safe — versions and integrity
hashes are untouched, and the tarballs are identical bytes from either mirror —
but it is a workaround in two places.

The fix, once, on a machine whose `.npmrc` uses the public registry:

```bash
rm package-lock.json
npm install
git commit package-lock.json
```

Then delete the `sed` line from both files. Until then the URLs are also plain
HTTP, which is worth removing on its own.

### The frontend image is environment-specific

The frontend is a **client-side Vite SPA, not a Next.js server**. Vite
substitutes `import.meta.env.VITE_*` at *build* time, so `VITE_API_BASE_URL`
and `VITE_WS_URL` are literals inside the compiled JavaScript.

Adding `env:` to `frontend.yaml` therefore does **nothing** — the browser runs
the bundle and never sees the pod's environment. Repointing the app at a
different backend means rebuilding the image, not re-deploying it.

The images are built with the same-origin defaults (`/api`, `/ws`), which the
Ingress routes to the gateway. Keeping those defaults is what makes one image
valid for every site.

### There are no database migrations

There is no Liquibase and no Flyway. Schema comes from:

- `ddl-auto: ${DDL_AUTO:update}` — Hibernate reconciles entities against the
  live schema on every service start, and
- hand-applied SQL under each service's `src/main/resources/db/manual/`.

So there is no migration Job in this directory and none in the deploy
workflow: there would be no tool for it to invoke.
`backend/shared/common/src/main/resources/db/migration/V1__init_schema.sql`
uses Flyway's naming convention but nothing reads it — Flyway is not a
dependency of any module.

What this costs you, stated plainly:

- `ddl-auto: update` **never drops or narrows** anything. Renamed columns are
  left behind, and a type change it cannot make is skipped silently.
- The `db/manual/` scripts have no applied-version ledger, so nothing records
  which have run against which environment. Re-running is not generally safe.
- Two services starting concurrently can both attempt DDL against the same
  schema.

Set `DDL_AUTO=validate` in production once the schema is stable: services then
fail fast on a mismatch instead of mutating the schema under load. That
requires a real migration tool to make the changes, which is the point at which
adopting Flyway — a dependency in `backend/build.gradle`, `db/manual/*.sql`
converted to versioned migrations, and a pre-rollout Job — becomes worth doing.

## Rollout strategy

`base/kustomization.yaml` patches a surge-only `RollingUpdate`
(`maxUnavailable: 0`, `maxSurge: 1`) onto every Deployment, as a single patch
rather than repeated per file.

`maxUnavailable` must stay `0` while the PodDisruptionBudgets use
`minAvailable: 1` against `replicas: 2` — the Kubernetes default of 25% would
let a rollout itself take a service to one pod, leaving no headroom for a
concurrent node drain.

**Kafka is patched separately to `Recreate`** and must stay that way. It is a
single-node KRaft broker with a fixed `KAFKA_NODE_ID` and itself as the sole
controller-quorum voter; surging would briefly run two brokers claiming that
same ID behind one Service. It accepts a short outage instead, which is the
right trade for a single-replica stateful component.

## Rollback

```bash
kubectl rollout undo deployment/<name>
```

Images are tagged with the commit SHA rather than `latest`, which is what makes
this work — a moving tag would roll back to the same bytes.

Note that a rollback reverts **code only**. Because `ddl-auto: update` has
already applied this release's schema changes and does not reverse them, an
older image meets a newer schema. Additive changes are usually tolerated; a
column the old code requires and the new code dropped is not.
