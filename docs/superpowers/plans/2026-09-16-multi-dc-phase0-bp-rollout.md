# Phase 0 Rollout Runbook — BP k8s

**Companion to:** `docs/superpowers/specs/2026-09-16-multi-dc-production-deployment-design.md`
**Audience:** whoever has `kubectl` access to BP — I (the assistant) do not have cluster credentials, so every step below is written for you to run and verify yourself, in order, with a checkpoint after each one.

Everything that lives in *this git repository* is already done (see §0 below). What's left is entirely cluster-side: rebuilding images, applying to BP, verifying, and one manual database step.

---

## 0. What's already in the repo (nothing to do here — just context)

- `backend/k8s/base/` — every one of the 10 backend services now has a real Deployment + Service + HPA + PodDisruptionBudget (6 of them — `discovery-service`, `menu-service`, `qr-service`, `notification-service`, `analytics-service`, `back-office-service` — had **no manifest at all before this**), plus resource requests/limits (none existed before), Kafka, the `qrserve-config` ConfigMap, the ingress, and the Postgres/Redis `ExternalName` Services.
- `backend/k8s/overlays/site-bp/` — the thing you actually `kubectl apply -k`.
- The old flat files (`deployment.yml`, `service.yml`, `hpa.yml`, `config.yml`, `secrets.example.yml`, `proxy-ingress.yml`, `qrserve-backend-app.yaml`) are deleted — `base/` + `overlays/site-bp/` fully replace them with equivalent-or-fixed behavior. **They were untracked in git (never committed)**, so there is no `git show`-able history to fall back to for their exact prior content if you need it — §6's rollback plan uses `kubectl`'s own revision history instead, which doesn't depend on that.
- `backend/api-gateway/src/main/java/com/qrserve/gateway/health/SiteReadinessHealthIndicator.java` (new) + an `application.yml` health group — exposes `GET /actuator/health/site`, the per-site signal your GSLB should poll (see design doc §5–6). Not yet wired to your actual GSLB — that's the design doc's §9.1 open item, needs the network team.
- `backend/k8s/postgres-deployment.yaml` — fixed to include `qrserve_backoffice` in its init script and corrected `POSTGRES_USER` to match every service's `postgres` expectation. **This file is local/dev-only and is NOT what BP uses** (BP's services point at `postgres-db`, an `ExternalName` Service resolving to your real Postgres VM) — it's mentioned here only because §3 below needs you to make the equivalent change on the real VM.
- `backend/build.gradle` — `spring-boot-starter-actuator` added to `api-gateway` and `discovery-service`, both of which were completely missing it (bug #7 below).

### Bugs this fixes that were silently broken before, not just "hardening"

These are worth reading before you apply, because they explain behavior you may already be seeing:

1. **`api-gateway` never had `EUREKA_SERVER_URL` set.** Every route in the gateway is `lb://<service>`, resolved via Eureka. With no override it defaulted to `http://localhost:8761/eureka` (itself — nothing listens there), so `lb://` could never resolve a single real instance. Combined with #2 below (no `discovery-service` deployed at all), **every route through the gateway has had no working backend to route to.** If you've been seeing gateway-level errors on essentially everything, not just Super App login, this is very likely why.
2. **`discovery-service` (Eureka) had no k8s manifest at all.** Even if `EUREKA_SERVER_URL` had been set correctly everywhere, there was no pod for it to point at.
3. **`auth-service` was missing `MERCHANT_SERVICE_URL`**, which is what caused the `SERVICE_UNAVAILABLE` you originally reported on Super App login (it defaulted to calling itself instead of `merchant-service`) — this one was already fixed in a previous change, and is included here for completeness.
4. **`api-gateway` was also missing `MERCHANT_SERVICE_URL`**, used by `TenantSlugResolver` for tenant-host resolution — same class of bug as #3, different service.
5. **`merchant-service` was missing `KAFKA_BOOTSTRAP_SERVERS`**, so `MerchantEventPublisher`/`TableQrEventPublisher` events were silently never reaching Kafka (defaulted to `localhost:9092`).
6. **`qrserve_backoffice` database was never created anywhere** (not in the in-cluster Postgres init script, and — needs confirming — likely not on the real VM either), so `back-office-service` would crash-loop on startup even once it had a manifest.
7. **`api-gateway` and `discovery-service` never had `spring-boot-starter-actuator` on their classpath at all** (`backend/build.gradle` explicitly excludes both from the block that adds it to every other service). Their `application.yml` health config and their k8s liveness/readiness probes (`/actuator/health/liveness`/`readiness`) both already existed and looked correct, but neither did anything real — the endpoints didn't exist, so every probe hit would 404. For `api-gateway` specifically this means its **liveness** probe has likely been failing continuously in any real deployment of these manifests, which restarts the container on every failure threshold — i.e. the gateway may never have stabilized. This is now fixed in `backend/build.gradle`; **you must rebuild the `api-gateway` and `discovery-service` images before applying**, or this fix never reaches BP (see §1).

None of these are things this rollout *introduces* — they're gaps that existed before and that Phase 0 closes as part of giving every service a real manifest.

---

## 1. Rebuild and publish images BEFORE applying anything

`kubectl apply` only changes what's in the manifests (env vars, resources, replica counts) — it does **not** rebuild the container images the manifests reference (`qrserve-api-gateway:latest`, `qrserve-discovery-service:latest`, `qrserve-auth-service:latest`, etc.). Several of this Phase 0 pass's fixes are code/dependency changes, not manifest changes, and are invisible to the cluster until the image itself is rebuilt and pushed/loaded through whatever your existing BP image pipeline is:

- **`api-gateway`** — gained `spring-boot-starter-actuator` (was completely missing — see bug #7 above) and a new `SiteReadinessHealthIndicator` class. Without a rebuild, applying the new manifest changes nothing about the actual 404s.
- **`discovery-service`** — gained `spring-boot-starter-actuator` (same bug #7).
- **`auth-service`** — carries the earlier Super App claim rework (`merchantShortCode`/`msisdn`), the onboarding flow, and the new `/api/auth/me/credentials` endpoint, all from before this rollout — rebuild if BP hasn't already picked those up.

Use whichever build/push step your BP pipeline already uses (the same one that produced whatever image BP is running today); this runbook doesn't assume a specific registry.

---

## 2. Before you touch anything: confirm you're pointed at BP

```bash
kubectl config current-context
kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}'
```
Confirm both match BP. Everything below assumes this context is active for the rest of the runbook. If you use multiple contexts, consider `kubectl --context <bp-context-name> ...` on every command instead of relying on the current one silently staying selected.

---

## 3. One manual database step: create `qrserve_backoffice`

Run this against the real Postgres VM (not the in-cluster `postgres-deployment.yaml`, which isn't what BP uses):

```sql
CREATE DATABASE qrserve_backoffice;
```

Skip this and `back-office-service` will crash-loop after §6 — its `DATABASE_URL` points at a database that doesn't exist yet. This is the one step in this whole runbook that isn't a `kubectl` command.

---

## 4. Confirm the `qrserve-secrets` Secret — do NOT blindly recreate it

```bash
kubectl get secret qrserve-secrets -o jsonpath='{.data}' | ...  # inspect, don't print raw values into shared logs
```

- **If it already exists**, leave it alone. Rotating `jwt-secret` invalidates every issued access/refresh token — every logged-in user gets kicked out. Nothing in this rollout requires rotating it.
- **If it doesn't exist yet** (fresh cluster), create it once:
  ```bash
  kubectl create secret generic qrserve-secrets \
    --from-literal=jwt-secret="$(openssl rand -base64 48)" \
    --from-literal=qr-signature-secret="$(openssl rand -base64 32)" \
    --from-literal=db-password="<the real Postgres VM's postgres-user password>"
  ```
  `db-password` must be the actual password for the `postgres` user on your real Postgres VM — every service's `DATABASE_USERNAME` is `postgres`.

Also confirm `postgres-db` and `redis-cache` in `backend/k8s/base/data-tier-external.yaml` point at your real VM hostnames — they currently hold placeholder values (`postgres-vm-cluster.yourdomain.local` / `redis-vm-cluster.yourdomain.local`). If BP's live cluster already has these `ExternalName` Services applied with the correct real hostnames from before, **do not** let this file's placeholders overwrite them — edit the file to match what's already live before you apply, or skip `data-tier-external.yaml` from this apply if it's already correctly in place (see §5's dry-run to check before committing to a real apply).

---

## 5. Dry-run against the real API server (no changes made)

```bash
kubectl kustomize backend/k8s/overlays/site-bp | kubectl apply --dry-run=server -f -
```

This validates every manifest against BP's actual API server (CRDs, admission webhooks, schema) without changing anything. Read the output for anything unexpected — particularly whether `postgres-db`/`redis-cache` would change (see the placeholder warning above) and whether `auth-service`/`merchant-service`/`order-service`/`api-gateway-deployment` show as `configured` (expected — they already exist, we're updating their env/resources) versus `created` (expected for the 6 new services + `discovery-service`).

---

## 6. Apply

```bash
kubectl apply -k backend/k8s/overlays/site-bp
```

Every existing Deployment (`auth-service`, `merchant-service`, `order-service`, `api-gateway-deployment`) keeps its exact name, so this **updates them in place** (a normal rolling update) — it does not create duplicates or leave the old ones orphaned. The 6 previously-missing services and `discovery-service` are newly created.

**Rollback, if anything here goes wrong:** Kubernetes keeps revision history per Deployment regardless of what's in git.
```bash
kubectl rollout undo deployment/<name>          # e.g. auth-service, api-gateway-deployment
kubectl rollout history deployment/<name>       # to pick a specific earlier revision instead of just "previous"
```
This works even though the old flat YAML files are gone from the repo (they were untracked — see §0) — the rollback source of truth is the cluster's own ReplicaSet history, not a file.

---

## 7. Watch the rollout

```bash
kubectl get pods -w
```

Expect some early log noise from every service failing to register with Eureka **before** `discovery-service`'s pod is actually Ready — Kustomize applies everything at once, there's no enforced startup ordering, and Eureka clients retry on their own. This is normal and should stop within the first minute once `discovery-service` reports Ready. If it doesn't stop, `kubectl logs deployment/discovery-service` first.

Watch specifically for `back-office-service` — if you skipped §3, this is where it shows as `CrashLoopBackOff`. Also watch `api-gateway-deployment` for a clean, stable Ready state — see bug #7: if it keeps restarting, the image wasn't actually rebuilt with the actuator fix (§1).

---

## 8. Verify Eureka actually has everyone registered (the core Phase 0 fix)

```bash
kubectl port-forward svc/discovery-service 8761:8761
# in another terminal:
curl -s http://localhost:8761/eureka/apps | grep -E '<name>|<status>'
```
Confirm `AUTH-SERVICE`, `MERCHANT-SERVICE`, `MENU-SERVICE`, `ORDER-SERVICE`, `QR-SERVICE`, `NOTIFICATION-SERVICE`, `ANALYTICS-SERVICE`, `BACK-OFFICE-SERVICE` all appear with `<status>UP</status>` and the expected instance counts (e.g. 4 for auth-service, 3 for menu-service — see each service's `replicas:` in `backend/k8s/base/`). `api-gateway` itself doesn't need to appear here (it only consumes the registry, `discovery.locator` doesn't force it to register a route for itself).

---

## 9. Verify `lb://` routing actually works now

Pick any route that previously would have failed with "Unable to find instance for auth-service" or a 503 from the gateway:

```bash
kubectl port-forward svc/api-gateway-service 8081:8081
curl -i http://localhost:8081/api/auth/login -X POST -H 'Content-Type: application/json' -d '{"email":"nonexistent@example.com","password":"x"}'
```
Expect a real `401 Unauthorized` (invalid credentials) — the actual, meaningful failure — not a `503`/gateway-level "no instances available" error. A 401 here is proof the request actually reached `auth-service` through `lb://` routing, which is the thing that was broken.

---

## 10. Verify the new site-health endpoint

```bash
curl -s http://localhost:8081/actuator/health/site | jq
```
Expect `"status": "UP"` with `siteReadiness` also `UP` in the `components` detail. This is the endpoint to hand to whoever owns your GSLB (design doc §9.1) once that integration conversation happens — not wired to it yet.

---

## 11. Smoke-test the things that already worked before, to confirm no regression

- `POST /api/auth/superapp/exchange` with a dev-fake token → should succeed and provision/log in a merchant, same as before this rollout (this is the flow the original `SERVICE_UNAVAILABLE` report was about).
- `GET /api/merchants/{id}` (as SUPER_ADMIN) → confirms `merchant-service` + auth are both fine end to end through the gateway.
- Whatever your usual smoke path is for the digital menu (`GET /api/v1/public/menu/...` or the frontend's `/m/{merchantSlug}` page) → confirms `menu-service` and `qr-service`, both newly deployed here for the first time, actually work under real routing rather than just "the pod started."

---

## 12. Housekeeping (recommended, not required for this rollout to work)

`backend/k8s/` was previously untracked in git entirely (see §0) — consider `git add backend/k8s docs/superpowers` and committing this Phase 0 work once you've verified it on BP, so future changes (the second site, in Phase 1+ of the design doc) have real history to diff against and roll back through, instead of relying solely on `kubectl`'s live-cluster history. I'm not committing this myself — that's your call on message/timing.

---

## 13. What this runbook deliberately does NOT do

Everything past Phase 0 in the design doc: standing up a second site, Postgres streaming replication, the GSLB integration itself. Those come after Phase 0 is verified stable on BP alone — see the design doc's §7 rollout plan for the ordered next steps once you're ready.
