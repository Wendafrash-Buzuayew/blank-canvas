# QRServe Multi-DC Production Deployment & Cross-Site Health — Design + Rollout Plan

**Date:** 2026-09-16
**Status:** Proposed design. Not yet implemented. First rollout target: **BP k8s**.
**Scope:** Backend microservices (10 services + Kafka + Postgres/Redis data tier). A Phase 0 single-cluster production-hardening pass on BP, then a phased multi-DC rollout: app tier active-active, data tier single-writer with streaming replicas, integrated with the existing GSLB for cross-site traffic steering and a new k8s/Eureka-backed per-site health signal for it to consume.

---

## 1. Context

QRServe's backend is 10 Spring Boot microservices (`discovery-service`, `auth-service`, `merchant-service`, `menu-service`, `order-service`, `qr-service`, `notification-service`, `analytics-service`, `back-office-service`, `api-gateway`) plus Kafka, behind one nginx Ingress. Today this runs as a **single k8s cluster**, and an audit of `backend/k8s/` ahead of this design found the following gaps that any multi-DC plan has to close first, not inherit twice:

- Only 4 of 10 services (`auth-service`, `merchant-service`, `order-service`, `api-gateway`) have real Deployment/Service/HPA manifests. `discovery-service`, `menu-service`, `qr-service`, `notification-service`, `analytics-service`, `back-office-service` exist only in `docker-compose.yml` — there is nothing to deploy to a second site even if one existed.
- No container anywhere sets CPU/memory requests or limits.
- `backend/k8s/qrserve-backend-app.yaml` is a stray, inconsistent manifest (wrong DB name, wrong health path, doesn't match any real service) — dead weight that should not be carried into a multi-site layout.
- Every service points at **one shared Postgres VM and one Redis VM**, reached via `ExternalName` Services (`postgres-db`, `redis-cache` in `backend/k8s/service.yml`). There is no replication, no per-region data tier today.
- `discovery-service` (Eureka) is registered-to by every service, but only `api-gateway` actually uses it for traffic (`lb://` routes). Every direct service-to-service call (e.g. `merchant-service` → `menu-service`, `auth-service` → `merchant-service`) uses a hardcoded k8s DNS name instead, by explicit design (see `auth-service/application.yml`'s own comment: *"Plain HTTP, not lb:// ... so tenant/merchant provisioning does not depend on Eureka load-balancer state"*). Eureka has zero region/zone awareness configured.
- Liveness/readiness probes use Spring Boot's bare default groups (process-is-up), not real dependency health (DB, Eureka, Kafka).

**Confirmed decisions this design is built around** (see §2): the app tier will be **active-active** across DCs; the data tier stays **single-writer** (one Postgres primary, streaming read replicas at other sites) rather than a multi-master rewrite; a **GSLB already exists** at the network layer and this plan integrates with it rather than building one; and **Phase 0** (closing the gaps above) is in scope and happens first, entirely on BP, before any second site is touched.

**Hard constraints:**
- Nothing here should disrupt the in-flight Phase 1 Mini App work (`2026-08-27-mini-app-phase1-decoupling-design.md`) — this design is orthogonal to it (that doc scopes *which routes/services* are exposed; this one scopes *how many sites* run them).
- No application rewrite for multi-master writes. That is a separate, much larger initiative (see §9.3) and explicitly out of scope here.
- The existing GSLB is the traffic-steering authority. This plan's job is to give it a trustworthy per-site health signal, not to replace it.

---

## 2. Decisions register

| Decision | Choice | Consequence |
|---|---|---|
| App-tier topology | **Active-active** | Both/all sites run a full stack and can serve live traffic simultaneously. Works cleanly because auth is stateless JWT (no sticky sessions needed for the API surface in scope). |
| Data-tier topology | **Single-writer + streaming replica(s)**, not multi-master | One Postgres primary at a time (starts at BP); every write, from every site, targets that one primary over the inter-DC link. Secondary sites get a read replica for future local-read routing (§6), but ship as DR standby first. This is the pragmatic middle ground between the user's active-active app-tier goal and today's single-VM Postgres reality — see §9.4 for the read-routing gap this leaves open. |
| First site / initial primary | **BP k8s** | BP gets the full Phase 0 hardening pass and becomes the initial Postgres/Kafka primary. Every later site is added by cloning BP's (by-then) Kustomize base with a new overlay — never hand-built from scratch. |
| Cross-DC traffic steering | **Integrate with the existing GSLB** | This plan does not build DNS failover/anycast — it defines the health-check contract (§5) the GSLB polls per site, and lists exactly what needs confirming with the network team (§9.1). |
| Cross-site "service discovery" | **Eureka stays per-site**, never stretched over WAN | Each site's `discovery-service` is independent and scoped to its own cluster, exactly as today. What the user asked for — "use k8s service discovery to manage multi-site health checks" — is implemented as a **new per-site aggregate health endpoint** that reads the *local* Eureka registry to decide whether that whole site is fit to receive traffic (§5), not by federating Eureka itself. |
| Kafka | **Single cluster, hosted at the primary site** (same pattern as Postgres) | Secondary-site producers/consumers reach it over the inter-DC link initially. Cross-DC Kafka (MirrorMaker2 / per-site clusters) is deferred (§9.3) until a site needs to keep producing/consuming during a primary-site outage. |
| Redis | **Independent per site, no replication** | Confirmed usage is cache (`menu-service`, `TenantCacheInvalidator`, `TenantSlugResolver`) and a Kafka→WebSocket pub/sub relay (`notification-service`). None of it is source-of-truth data, so losing sync across sites is acceptable — each site gets its own Redis VM/instance. |
| Manifest structure | **Kustomize `base/` + per-site `overlays/`**, replacing the flat `backend/k8s/*.yml` | Every site is a `kubectl apply -k overlays/site-<name>` away from being stood up identically. This also finally gives the mini-app design's planned (but never-built) `overlays/phase1/` a real home. |
| Phase 0 scope | **Included, done first, BP-only** | Multi-DC on top of an incomplete, unlimited-resource, shallow-health-check baseline would just replicate every gap at every site. Nothing in Phase 1+ starts until Phase 0 is done and validated on BP alone. |

---

## 3. Phase 0 — Single-cluster production baseline (BP k8s only, no second site yet)

Everything in this phase changes only how BP is deployed; behavior is unchanged from today's `kubectl apply -k backend/k8s`. It converts today's ungoverned flat manifests into the base that every future site (including BP itself, redeployed) is stamped from.

### 3.1 Restructure to Kustomize

```
backend/k8s/
  base/
    kustomization.yaml
    discovery-service.yaml   (new)
    auth-service.yaml
    merchant-service.yaml
    menu-service.yaml        (new)
    order-service.yaml
    qr-service.yaml          (new)
    notification-service.yaml(new)
    analytics-service.yaml   (new)
    back-office-service.yaml (new)
    api-gateway.yaml
    kafka.yaml
    config.yaml
    secrets.example.yaml
  overlays/
    site-bp/
      kustomization.yaml     (patches: SITE_NAME=bp, replica counts, any BP-specific values)
      (later) phase1/        the mini-app design's planned overlay, now with a real home
```
`backend/k8s/qrserve-backend-app.yaml` is deleted — it corresponds to nothing real and would otherwise get carried into the base by accident.

### 3.2 Close the manifest gap — six missing services

Each of `discovery-service`, `menu-service`, `qr-service`, `notification-service`, `analytics-service`, `back-office-service` gets a Deployment + Service + HPA, mirroring the existing `auth-service`/`merchant-service`/`order-service` pattern exactly (same probe paths, same anti-affinity shape, same topology-aware Service annotation). Template (using `menu-service` as the example every other one copies):

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: menu-service
  namespace: default
spec:
  replicas: 3
  selector:
    matchLabels: { app: menu-service }
  template:
    metadata:
      labels: { app: menu-service }
    spec:
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions: [{ key: app, operator: In, values: ["menu-service"] }]
              topologyKey: topology.kubernetes.io/zone
      containers:
      - name: menu-service
        image: qrserve-menu-service:latest
        ports: [{ containerPort: 8086 }]
        env:
          - { name: SPRING_PROFILES_ACTIVE, value: "prod" }
          - { name: DATABASE_URL, value: "jdbc:postgresql://postgres-db:5432/qrserve_menu" }
          - { name: DATABASE_USERNAME, value: "postgres" }
          - name: DATABASE_PASSWORD
            valueFrom: { secretKeyRef: { name: qrserve-secrets, key: db-password } }
          - name: JWT_SECRET
            valueFrom: { secretKeyRef: { name: qrserve-secrets, key: jwt-secret } }
          - { name: MERCHANT_SERVICE_URL, value: "http://merchant-service:8085" }
          - { name: EUREKA_SERVER_URL, value: "http://discovery-service:8761/eureka" }
        resources:            # see §3.3 — every new manifest ships with limits from day one
          requests: { cpu: "250m", memory: "512Mi" }
          limits:   { cpu: "750m", memory: "1Gi" }
        livenessProbe:
          httpGet: { path: /actuator/health/liveness, port: 8086 }
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet: { path: /actuator/health/readiness, port: 8086 }
          initialDelaySeconds: 15
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: menu-service
  annotations: { service.kubernetes.io/topology-mode: "Auto" }
spec:
  selector: { app: menu-service }
  ports: [{ port: 8086, targetPort: 8086 }]
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: menu-service-hpa
spec:
  scaleTargetRef: { apiVersion: apps/v1, kind: Deployment, name: menu-service }
  minReplicas: 2
  maxReplicas: 8
  metrics:
  - type: Resource
    resource: { name: cpu, target: { type: Utilization, averageUtilization: 75 } }
```
`discovery-service` itself gets a plain Deployment + Service too (2 replicas is enough — see §5 on why Eureka HA matters more once the site-health check depends on it) but **no HPA**: Eureka's load is proportional to registered-instance count, not request volume, so it doesn't need to autoscale.

### 3.3 Resource requests/limits — starting values

None exist today anywhere. Proposed starting points (deliberately conservative; tune from real Prometheus/metrics-server data after a burn-in period rather than guessing further):

| Service | Requests (CPU/Mem) | Limits (CPU/Mem) | Why |
|---|---|---|---|
| `api-gateway` | 500m / 768Mi | 1 / 1.5Gi | Every request passes through it; reactive but still needs headroom under the resilience4j timeout config. |
| `auth-service`, `merchant-service`, `order-service` | 250m / 512Mi | 750m / 1Gi | Existing services, moderate JPA/Hibernate footprint. |
| `menu-service`, `qr-service`, `back-office-service` | 200m / 512Mi | 600m / 1Gi | Similar shape, generally lighter traffic. |
| `analytics-service`, `notification-service` | 200m / 512Mi | 600m / 1Gi | Notification-service also holds WebSocket connections — revisit if/when Phase 2 (live orders) is reactivated. |
| `discovery-service` | 150m / 384Mi | 400m / 512Mi | Small, in-memory registry only. |
| `kafka` | 500m / 1Gi | 1 / 2Gi | Already closest to production-sized among current manifests; unchanged. |

### 3.4 PodDisruptionBudgets

Add a `PodDisruptionBudget` per service (`minAvailable: 1` for anything with `minReplicas: 2`, matching the existing HPA floors) so a node drain or cluster upgrade on BP can't take a service to zero.

### 3.5 Dependency-aware health checks — and a deliberate non-change

Spring Boot's default `readiness` group already includes a `DataSourceHealthIndicator` once a DB is configured, so readiness *does* reflect real DB connectivity today — that part is fine as-is. What's missing is Eureka-awareness, and it's added deliberately as a **separate, higher-level signal, not folded into pod readiness**:

- **Pod-level readiness stays DB-only.** Making a pod's own readiness depend on Eureka reachability would pull individual healthy pods out of the in-cluster Service's endpoint list over a transient Eureka blip that most calls (everything except `api-gateway`'s `lb://` routes) don't even depend on. That's a self-inflicted outage for no benefit.
- **A new site-level signal is added instead** — `/actuator/health/site`, detailed in §5 — which *does* use Eureka's registry, but to answer a different question ("is this whole site fit to receive traffic from the GSLB") rather than "should k8s route to this one pod."

---

## 4. Phase 1 — Data tier for multi-DC

### 4.1 Postgres: primary + streaming replica

- BP's existing Postgres VM becomes the **primary**. Enable WAL shipping / streaming replication (`wal_level = replica`, a replication slot per standby, `pg_basebackup` to seed).
- Each secondary site gets its own Postgres VM as a **streaming standby**, replicating all 7 `qrserve_*` databases (they're one Postgres instance/cluster, so one replication stream covers all of them — no per-database setup needed).
- **Every site's `DATABASE_URL` continues to point at the primary for writes.** This is what makes "app-tier active-active without a data rewrite" actually work: a secondary site's pods are live, healthy, and serving reads/writes — those writes just cross the inter-DC link to reach the one primary, the same as a request today crosses from `api-gateway` to any backend pod. Latency-sensitive, but correct and simple, and does not require any application code change.
- The standby's role is (a) DR — promote it (`pg_promote()` or `pg_ctl promote`) if the primary site goes down, flip a config/DNS pointer, and every site's app tier keeps working unchanged; and (b) the future source for local reads (§9.4).
- Promotion starts as a **documented, tested manual runbook** (this infra is bare VMs, not a managed Postgres service or an already-running Patroni/repmgr cluster — building automatic failover is a real, separate undertaking, see §9.2). Automate only after the manual runbook has been drilled successfully at least once.

### 4.2 Redis: independent per site

No replication. Each site gets its own Redis VM/instance for `menu-service`'s cache, `TenantCacheInvalidator`/`TenantSlugResolver`'s tenant-slug cache, and `notification-service`'s Kafka→WebSocket relay. A cold cache after failover just means one round of cache-misses repopulating from Postgres — cheap, and correctly scoped since this data was never meant to be durable.

### 4.3 Kafka: single cluster at the primary site, for now

`merchant-service`, `order-service`, `notification-service`, and `analytics-service` are the current producers/consumers. Kafka stays a single cluster hosted at the primary site (BP initially); secondary-site services reach it over the inter-DC link, exactly like the Postgres primary. This avoids standing up MirrorMaker2 or a stretched/quorum-based Kafka cluster before there's a concrete need for a secondary site to keep producing/consuming events *during* a primary-site outage — track as a follow-up (§9.3), not a Phase 1 blocker.

---

## 5. Phase 2 — Cross-site service discovery & health

This is the part of the ask that needs the most precision, because "Eureka spanning multiple datacenters" is the wrong shape for this: Netflix Eureka's peer-replication is designed for AZs within one region on a low-latency LAN, not independent k8s clusters across DCs on a WAN link — stretching it would be slow, fragile, and `api-gateway` doesn't even need a *cross-site* view of instances (it only ever load-balances to services inside its own cluster).

**What actually gets built:** each site keeps its own fully independent `discovery-service`, exactly as today. On top of that, a new lightweight **site-level aggregate health signal** answers "is this entire site healthy enough to receive live traffic," and it is this endpoint — not Eureka itself — that the GSLB polls per site.

### 5.1 `SiteReadinessHealthIndicator` (new, in `api-gateway`)

`api-gateway` is the one place already true per site (the single ingress point), so it's the natural home:

```java
@Component
public class SiteReadinessHealthIndicator implements HealthIndicator {

    private final DiscoveryClient discoveryClient; // Spring Cloud's Eureka-backed client, already on the classpath
    private static final List<String> CRITICAL_SERVICES =
        List.of("auth-service", "merchant-service", "menu-service", "order-service", "qr-service");

    @Override
    public Health health() {
        List<String> missing = CRITICAL_SERVICES.stream()
                .filter(name -> discoveryClient.getInstances(name).isEmpty())
                .toList();
        if (!missing.isEmpty()) {
            return Health.down().withDetail("missingServices", missing).build();
        }
        return Health.up().build();
    }
}
```

This literally *is* "using k8s service discovery to manage multi-site health" — it reads the same Eureka registry that `api-gateway`'s own `lb://` routing already depends on (populated by every pod's k8s-DNS-resolved self-registration) and turns "are all critical services present and registered in this cluster" into a single boolean.

### 5.2 Wire it into a dedicated Actuator group

```yaml
management:
  endpoint:
    health:
      group:
        site:
          include: siteReadiness, db   # db: a lightweight check against the primary, reusing the existing DataSource health check pattern
          show-details: always
```
Exposed at `GET /actuator/health/site` — `200` with `{"status":"UP"}` when the site is fit for traffic, `503` otherwise. This is deliberately a **different endpoint from `/actuator/health/readiness`** (§3.5): readiness is per-pod, this is per-site, and conflating them would let one flaky pod's Eureka deregistration take the whole site out of GSLB rotation.

### 5.3 What this buys operationally

- A site whose local `discovery-service` pod crashed, or whose critical services failed to come up after a bad rollout, fails `/health/site` and the GSLB stops sending it traffic — automatically, without anyone touching DNS by hand.
- It composes cleanly with §4.1's DB story: add the primary-DB reachability check into the same `site` group, and a site that's lost its link to the Postgres primary (the actual failure mode that matters most under this single-writer design) also gets correctly pulled out of rotation.

---

## 6. Phase 3 — Traffic steering: integrating with the existing GSLB

Since a GSLB already exists, this plan's job is narrow: define the contract it needs, then confirm the specifics with whoever owns it (§9.1 has the exact checklist). At minimum, the GSLB needs to be pointed at `https://<site-ingress-host>/actuator/health/site` per site, on whatever interval/threshold it already supports for health-checked failover or weighted routing.

**Session-affinity note:** the in-scope API surface (JWT bearer auth, no server-side session) needs no sticky sessions — any site can serve any request statelessly, which is exactly what makes active-active viable here with zero app changes. The one exception is `notification-service`'s WebSocket/STOMP connections, which do need affinity or a shared broker across sites — not a blocker today since that surface is already decoupled behind the Phase 2 flag in the mini-app design, but flagged for whoever reactivates it (§9.5).

---

## 7. Phase 4 — Rollout plan, BP first

1. **Phase 0 entirely on BP**, second site not involved at all yet. Convert to Kustomize, add the 6 missing services, resource limits, PDBs, delete the stray manifest.
2. **Validate the BP baseline**: load test at expected peak, kill a pod of each service and confirm k8s replaces it inside the PDB's tolerance, confirm HPA scales under synthetic load, confirm `/actuator/health/site` correctly flips to DOWN when a critical service's Deployment is scaled to 0 (staging test, not prod).
3. **Stand up DC2's k8s cluster** using the same Kustomize base + a new `overlays/site-<dc2>` (differs only in `SITE_NAME` and any DC2-specific infra values — no manifest is hand-written a second time).
4. **Data tier**: bring up DC2's Postgres as a streaming standby of BP's primary; DC2's Redis is a fresh, independent instance; DC2's services point their `DATABASE_URL`/`KAFKA_BOOTSTRAP_SERVERS` at BP (the primary) per §4.
5. **Warm standby first**: deploy DC2's full app tier, confirm `/actuator/health/site` is green there too, but do **not** add it to the GSLB's active rotation yet.
6. **Failover drill**: with DC2 still out of rotation, simulate a BP outage in a controlled window — promote DC2's Postgres standby, repoint the primary alias, confirm DC2 alone can serve full read/write traffic. Document the actual runbook from this drill (not a theoretical one).
7. **Go active-active**: only after a clean drill, add DC2 to the GSLB's live rotation (weighted or active-active per whatever mode it supports).
8. **Rollback at any step**: every step above is additive to BP (new overlay, new site) until step 7 — until the GSLB is told about DC2, BP's existing production traffic is completely unaffected, so the safe rollback at any point before step 7 is simply "stop, DC2 was never in the traffic path."

---

## 8. Testing / validation

- **Phase 0**: `kubectl apply -k overlays/site-bp` produces the *same* running system as today's flat `kubectl apply -f backend/k8s` (behavioral regression check); chaos-kill each service's pods and confirm PDB + HPA behavior; confirm every new service's `/actuator/health/liveness`/`readiness` responds correctly.
- **Phase 1**: kill the primary Postgres in a staging drill, confirm the standby has the expected replication lag, execute the promotion runbook, confirm every service reconnects once the DNS/config pointer flips.
- **Phase 2**: unit-test `SiteReadinessHealthIndicator` (mock `DiscoveryClient` returning empty for one critical service → `DOWN`); integration-test `/actuator/health/site` against a real (test) Eureka registry.
- **Phase 3**: confirm the GSLB actually stops routing to a site when `/health/site` returns 503 (this has to be verified with whichever GSLB product is in use — see open item §9.1).
- **Phase 4**: the failover drill in step 6 above *is* the end-to-end validation gate before DC2 ever sees real traffic.

---

## 9. Open items

1. **Exact GSLB product/API in use** — needed to finalize the health-check contract (URL, method, interval, failure threshold, weighted-vs-failover mode, DNS TTL). Confirm with the network/infra team before Phase 3.
2. **Postgres HA automation** — this plan proposes a manual, drilled, scripted promotion runbook for v1. Automating failover (Patroni, repmgr, or a managed Postgres service) is a real separate project; revisit once the manual runbook has been exercised for real.
3. **Kafka cross-DC strategy** — deferred until a site needs to keep producing/consuming during a primary-site outage; evaluate MirrorMaker2 vs. independent per-site topics at that point.
4. **Read/replica query routing** — there is currently no read/write datasource split anywhere in the codebase; every read still crosses to the primary today even from a secondary site. Building a routing `DataSource` (or explicitly routing specific read-heavy endpoints, e.g. the public digital-menu reads, to the local replica) is the natural next step to reduce cross-DC latency, but is out of scope for this rollout.
5. **`notification-service` WebSocket session affinity** — irrelevant while Phase 2 (live orders/kitchen) stays behind the mini-app design's flag; revisit together if/when that surface is reactivated multi-site.

## 10. Out of scope

Multi-master database rewrite or migration to a distributed DB; service mesh adoption (Istio/Linkerd) for cross-cluster routing; fully automated DR (beyond the manual runbook in §4.1); anything from the Phase 2 (Kitchen/Orders/Analytics/Waiter) surface beyond keeping it deployable behind the existing flag from `2026-08-27-mini-app-phase1-decoupling-design.md`.
