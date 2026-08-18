# Multi-Tenant Subdomains and QR Generation — Design

**Date:** 2026-08-18
**Status:** Approved for implementation planning
**Scope:** Tenant addressing (subsystem A) + QR generation (subsystem B).
Subscriptions and entitlements (subsystem C) are a **separate spec** — see
[Out of scope](#out-of-scope).

---

## Context

QRServe will be hosted internally on Kubernetes as a **shared multi-tenant SaaS**:
one deployment, many restaurants and coffee shops, separated by `merchantId`.
Each tenant gets a subdomain serving **both** the customer QR menu and the staff
console.

**Confirmed constraints:**

| Constraint | Value |
|---|---|
| Tenancy model | Shared multi-tenant (single deployment) |
| Host pattern | `{merchantSlug}.qrserve.safaricom.et` |
| Wildcard DNS + TLS | Available; wildcard on the load balancer, public IP |
| Platform | Kubernetes, ingress-terminated TLS |
| Project stage | **In development — not launched, not in test** |

The development stage is load-bearing for this design: there are no live tenants,
no traffic to protect, and **no printed QR stands in existence**. Several
protections that a live system would need are therefore deliberately omitted (see
[Deferred](#deferred-with-a-guard-in-place)).

### Why shared rather than per-tenant deployment

The schema is already built for it — `merchantId` on every entity, tenant checks
in services, a cross-tenant `SUPER_ADMIN` role. Per-tenant deployment would also
mean nine JVMs plus PostgreSQL, Kafka, Redis and Eureka *per coffee shop*, which
on internal hardware exhausts capacity after a handful of customers, and it cannot
support the advertised free tier.

### Risk this raises

In a shared deployment a tenant-isolation defect is **one restaurant reading
another's revenue** — a breach between paying customers, not an internal
inconsistency. Cross-tenant holes were found and fixed in six places on the
`fix/codebase-review-remediation` branch (analytics, customer requests, waiter
tasks, user listing, table listing, STOMP subscriptions). That density implies
more exist. [Section 5](#5-testing-gate) therefore treats two-merchant isolation
as a CI gate rather than a manual check.

---

## Defects this design fixes

Four existing defects are load-bearing for the work, not incidental:

**1. Every generated QR code is broken.** `TableService:48` and
`QrGeneratorService:52` both build:

```java
String.format("https://qrserve.com/menu/%s/%d/%d", merchant.getSlug(), branch.getId(), saved.getId())
```

The customer route is `/menu/:merchantSlug/:branchSlug/:tableNumber` and
`PublicMenuResolutionService` resolves the branch **by slug** and the table **by
table number**. A scanned code yields `/menu/sunrise-coffee/3/5`, resolution looks
for a branch with slug `"3"`, and 404s. No printed stand would work today.

**2. The public host is hardcoded** to `https://qrserve.com` in those same two
places.

**3. `BranchEntity.slug` is globally unique** (`@Column(nullable = false, unique
= true)`). The second tenant to name a branch "Main" collides with the first.

**4. Slug generation is unfit to be a DNS label.** One expression, duplicated in
`MerchantService:22`, `MerchantEntity:56` and `BranchEntity:46`:

```java
name.toLowerCase().replaceAll("[^a-z0-9]", "-")
```

| Input | Output | Problem |
|---|---|---|
| `Joe's Diner ` | `--joe-s-diner-` | leading/trailing hyphens are **illegal** in a DNS label |
| `Sunrise Coffee & Tea` | `sunrise-coffee---tea` | valid but this is the tenant's public address |
| `ካፌ አበባ` | `------` | non-ASCII collapses; no usable slug |
| 90-character name | 90-character slug | exceeds the 63-character DNS label limit |
| second `Main Cafe` | duplicate | raw constraint violation → 500, not a usable error |
| `Admin` | `admin` | claims a reserved subdomain |

---

## 1. Tenant addressing and resolution

### Request flow

Resolution happens **once, at the gateway**. For every request the gateway:

1. **Strips any inbound `X-Tenant-*` header.** Without this a caller forges tenant
   identity and every downstream service believes it. This step is the difference
   between a trusted header and a decorative one.
2. Extracts the first DNS label from `Host`: `sunrise.qrserve.safaricom.et` →
   `sunrise`.
3. Resolves the slug to a `merchantId` via Redis (already wired into the gateway
   as `spring-boot-starter-data-redis-reactive`), falling back to a merchant-service
   lookup on a cache miss.
4. Injects `X-Tenant-Id` (the UUID) and `X-Tenant-Slug` (for log correlation) onto
   the proxied request.

**Components:**

| Component | Location | Responsibility |
|---|---|---|
| `TenantResolutionGlobalFilter` | `api-gateway` | strip, extract, resolve, inject. Reactive `GlobalFilter`. |
| `GET /api/v1/public/tenants/by-slug/{slug}` | `merchant-service` | slug → `{merchantId, slug}`. Public, cacheable, no tenant context of its own. |
| `TenantContextFilter` | `shared:security` | reads `X-Tenant-Id` into `TenantContext` |
| `TenantContext` | `shared:common` | **already exists**, currently unused — a `ThreadLocal<UUID>` |

### Caching

Redis key `tenant:slug:{slug}` → `merchantId`, TTL 10 minutes. Cache invalidated on
merchant create and update.

**Misses are cached too.** On a public wildcard, bots enumerate subdomains. Without
negative caching every `xyz.qrserve.safaricom.et` becomes a database round trip.
Unknown slugs cache as absent with a shorter TTL (60 seconds).

### Reserved labels

`admin`, `api`, `app`, `www`, `static`, `assets`, `ws`, `mail`, `status` never
resolve as tenants and are rejected at merchant creation. `admin.qrserve.safaricom.et`
is reserved for `SUPER_ADMIN` cross-tenant work, which asserts no tenant.

An unresolvable label returns **404** with a tenant-not-found response, never a
fallback to some default tenant.

### Precedence rule — the security core

Two sources of tenant identity now exist per request. Anywhere they can disagree is
an attack surface.

| Request kind | Tenant source | On host/JWT mismatch |
|---|---|---|
| Anonymous (QR menu, order placement, service call) | **Host** | n/a — the host is the only signal |
| Authenticated staff | **JWT `merchantId`** | reject **403** |
| `SUPER_ADMIN` | explicit request parameter, on the `admin.` host | no tenant asserted |

**The host never grants authority the JWT does not already carry.** A waiter at
merchant A pointing a browser at `merchant-b.qrserve.safaricom.et` receives 403,
not merchant B's data — enforced in one filter rather than per controller.

### Fail closed

A service reached directly, bypassing the gateway, sees no `X-Tenant-Id`. On
tenant-scoped paths an anonymous request with no tenant header is **rejected**, not
treated as "no tenant". Enforcement is intentionally centralised in the gateway;
the filter's job is to refuse to guess.

### ThreadLocal hygiene

`TenantContext` is a `ThreadLocal`. `TenantContextFilter` **must** clear it in a
`finally` block. Servlet containers pool request threads, so a leaked value is
served to the next request on that thread — which in a multi-tenant deployment
means one tenant's context applied to another tenant's request.

---

## 2. Slug lifecycle and data model

### Merchant slug is owner-supplied

The slug becomes a hostname, so it is **required at merchant creation** rather than
derived from the name. The UI may *suggest* one when the name is already Latin, but
the API demands it explicitly and validates it.

This resolves the Amharic case directly: `ካፌ አበባ` yields no usable slug, and rather
than maintain a transliteration table with unflattering output, the owner supplies a
Latin slug and the display name is stored separately. Transliteration is explicitly
not implemented.

### Validation

A single `Slugs.toDnsLabel(String)` utility in `shared:common` replaces all three
duplicated copies. Rules:

- lowercase ASCII, `[a-z0-9-]` only
- runs of hyphens collapsed to one
- leading and trailing hyphens trimmed
- length 3–40 characters (inside the 63-character DNS label limit)
- rejected if reserved (see above) or entirely numeric
- on collision, deterministic suffix `-2`, `-3`, … rather than a constraint violation

Branch slugs use the same utility but are not DNS labels — they are a path
segment. Two rules differ: length is capped at 60 rather than 40, and an entirely
numeric slug is permitted (a branch legitimately called "2" is fine in a path,
whereas a numeric hostname is not). Every other rule applies unchanged, so branch
slugs stay URL-safe.

### Slug renames are blocked

`PUT /api/merchants/{id}` rejects any attempt to change `slug` with a clear error:
*"slug is permanent"*. See [Deferred](#deferred-with-a-guard-in-place) for why the
alias table that would allow renames is not being built yet.

### Schema changes

| Change | Migration |
|---|---|
| `BranchEntity.slug`: drop global unique, add `unique(merchant_id, slug)` | Safe — loosening a constraint cannot be violated by existing rows |
| `MerchantEntity.slug`: keep globally unique | Unchanged; it is the hostname |

---

## 3. QR generation

### URL shape

```
https://{merchantSlug}.qrserve.safaricom.et/menu/{branchSlug}/{tableNumber}
```

The merchant moves into the host; branch and table stay in the path. Branches are
deliberately **not** subdomains: a single-label wildcard does not cover
`*.*.qrserve.safaricom.et`, and a second-level wildcard would need its own
certificate.

The host comes from configuration — `app.public-base-domain`, environment
`PUBLIC_BASE_DOMAIN`. Nothing hardcoded.

### One builder

A `PublicMenuUrl` builder in `shared:common` becomes the only place this URL is
constructed. `TableService` and `QrGeneratorService` each carry a comment claiming
consistency with the other, and **both drifted from the actual route anyway**. Two
copies of a URL format is how defect 1 happened.

### Signature stays over ids

The QR signature currently covers `{merchantId, branchId, tableId}` and validation
resolves slug → branch → table before checking it. It stays on ids for forward safety. Renames are
rejected today, so slugs are currently stable too — but ids are stable
*permanently*, and signing the slugs would mean that the moment renames are
allowed (when the alias table lands) every rename forces a physical reprint.
Choosing ids now costs nothing and removes that future coupling.

### Per-tenant derived signing key

The signing key is derived per tenant rather than shared:

```
tenantKey = HMAC-SHA256(masterSecret, merchantId)
```

Nothing extra is stored — the key is derived on demand from
`QR_SIGNATURE_SECRET` and the merchant id. A leaked or brute-forced key then
compromises **one restaurant's** QR codes rather than every tenant's.

### Rotation overlap

`QrSignatureService` verifies against the current secret **or** an optional
`qr.signature-secret-previous`, while only ever *signing* with the current one.

This exists because rotating `QR_SIGNATURE_SECRET` invalidates every printed code —
already experienced once during the secret-hardening work. For a hosted service
with physically printed table stands, "rotate the secret" must not mean "reprint
every stand in every restaurant". Rotation becomes: set previous to the old value,
current to the new, reprint at leisure, drop previous later.

---

## 4. Migration and operations

### No phased rollout

A staged log-only rollout was considered and **rejected**: it exists to avoid
403-ing live tenants, and there are none. The precedence rule is enforced from the
first commit.

### Path-based routes are retained

Not for backwards compatibility — no printed codes exist — but because the landing
page links `/menu/demo/main/1` for the demo, and the routes cost nothing to keep.
The tenant host is canonical; the path form is the demo and development door.

The frontend resolves `merchantSlug` as: **the host label when present, the path
parameter otherwise.** One rule, both forms work.

### Kubernetes

The wildcard needs an ingress host rule and the wildcard certificate bound at TLS
termination.

**Dependency:** the existing manifests in `backend/k8s/deployment.yml` used
compose's `environment:` key instead of Kubernetes' `env:`, so no environment
variable was ever applied. That was fixed in commit `2739dcb`, but the manifests
remain partial — only four of nine services are described. Ingress work should not
begin until the manifests actually deploy the services they claim to.

### Local development

Subdomains do not resolve against `localhost`, and requiring every developer to
edit `/etc/hosts` per tenant is friction that gets bypassed — leaving the subdomain
path exercised only in staging.

Use public wildcard DNS that resolves to loopback: `sunrise.localtest.me:8081` or
the `sslip.io` equivalent, with `PUBLIC_BASE_DOMAIN=localtest.me` in the dev
profile. Zero setup, and the real code path is exercised locally.

---

## 5. Testing gate

Given shared multi-tenancy, these are **CI-blocking**, not manual:

| Test | Level | Why |
|---|---|---|
| Two-merchant isolation | `@SpringBootTest`, two seeded merchants | The highest-value test in the system. Six cross-tenant holes were found on one branch; in a shared deployment each is a breach between customers. |
| Host/JWT mismatch → 403 | Filter unit test | The rule that stops hostname-based impersonation |
| Inbound `X-Tenant-*` is stripped | Gateway filter test | Invisible if untested, catastrophic if wrong |
| Unknown slug → 404, never a default tenant | Gateway filter test | Prevents a fallback becoming a cross-tenant leak |
| `Slugs.toDnsLabel` | Unit tests | Covers every row of the defect-4 table |
| `TenantContext` cleared after each request | Filter test | A leaked ThreadLocal serves one tenant's context to another |

`backend/scripts/smoke-tenant-isolation.sh` already performs the isolation checks
against a running stack but nothing runs it. Promoting its assertions into a
`@SpringBootTest` turns it into a gate.

---

## Out of scope

**Subscriptions and entitlements (subsystem C)** get their own spec. That work
covers the plan model, the limits the landing page already advertises (5/50/
unlimited tables, 1/3/unlimited branches, kitchen display as a Premium feature),
enforcement points, and lifecycle — trial, upgrade, downgrade, non-payment
suspension. **No subscription model exists in the code today**; those plans are
marketing copy. It is independent of this spec and larger.

**Payment integration** is not addressed here or assumed by subsystem C.

## Deferred with a guard in place

**Merchant slug alias table.** Would let a rebranding restaurant keep old
hostnames resolving. Not built, because no stands are printed and no rename can
break anything yet. The guard is that slug changes are **rejected** (Section 2),
so the alias table can be added the first time a real tenant needs a rename. A
table plus a two-step resolution path is not worth carrying before then.

**QR reissue tracking.** Per-table records of which URL and secret generation a
code was printed under. Not needed: slug renames are rejected outright, so a
printed URL cannot go stale, and the rotation overlap keeps signatures valid across
a secret change. Both remaining causes of a stale code are therefore closed off
rather than tracked. This becomes worth revisiting only alongside the alias table,
since permitting renames is what would reintroduce stale printed URLs.

---

## Open items

These need answers before or during implementation, and none blocks starting:

1. **Final domain.** `qrserve.safaricom.et` is assumed throughout. If the product
   gets its own domain the only change is `PUBLIC_BASE_DOMAIN` and the certificate.
2. **Staff console host.** The design puts the console on the tenant host as
   requested. `admin.qrserve.safaricom.et` is reserved for `SUPER_ADMIN`; whether
   super-admins also need per-tenant access *through* a tenant host is unresolved
   and affects only the `SUPER_ADMIN` row of the precedence table.
3. **Inter-service identity.** Independent of this spec but adjacent: order-service
   forwards the end-user token to merchant-service, so
   `PATCH /api/tables/{id}/status` fails for anonymous guest orders and table
   occupancy never updates. Tenant resolution does not fix it — the gateway header
   is not present on service-to-service calls. Tracked separately.
