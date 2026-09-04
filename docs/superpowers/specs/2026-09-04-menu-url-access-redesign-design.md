# Menu URL & Access Redesign — Design

**Date:** 2026-09-04
**Status:** Approved for implementation planning
**Scope:** Sub-project 1 of the Digital Menu phase-1 HLD rollout — the Menu
domain model, branch-scoped publishing, and the public path-based URL family.
Content enhancements (multi-language, promotional pricing), the media/CDN
pipeline, menu templates, customer feedback, and the Back Office Service are
**separate specs** — see [Out of scope](#out-of-scope).

---

## Context

The business has handed down `Digital Menu HLD v0.1` (Biruk Abrha, business
sponsor Ikram Awol) as the authoritative scope for the **first rollout**: a
self-service digital-menu mini-app inside the M-Pesa Business App. Merchants
pick a template, brand it, manage items, and publish; customers reach the
published menu by scanning a QR code or opening a shareable link. The HLD
explicitly puts **Online Food Ordering, Table Ordering, and Merchant
Settlement out of scope** for this release.

QRServe already has a fully built ordering/kitchen/waiter/table-QR/payment
platform, addressed via per-tenant subdomains
(`{merchantSlug}.qrserve.safaricom.et/menu/{branchSlug}/{tableNumber}`, per
`docs/superpowers/specs/2026-08-18-multi-tenant-subdomain-qr-design.md`).
**Decision: that system is parked, not removed.** Nothing in this spec
modifies `TenantResolutionGlobalFilter`, `PublicMenuUrl`, `PublicMenuController`,
`QrSignatureService`'s existing signing method, or their tests. Phase 2 picks
that flow back up unchanged.

**Why path-based instead of a second subdomain scheme:** the business expects
up to **30,000 merchants** on this product. A subdomain-per-tenant model at
that scale means 30,000 DNS labels under one wildcard cert, cache-warming
concerns, and TLS/SNI overhead that a single shared domain with a path prefix
does not have. The HLD's own example
(`https://menu.safaricom.et/m/{merchant-slug}`) is path-based for exactly this
reason, and this design follows it.

### What's missing today, confirmed against the code

- **No `Menu` entity exists.** `CategoryEntity`/`ProductEntity` are scoped
  only by `merchantId` — one flat, always-live catalog per merchant, no
  draft/published state (`MenuService.getFullMenu(merchantId)`).
- **Branches carry no menu relationship.** `BranchEntity` is a pure
  name/slug/address/phone record.
- **No merchant-only or branch-only public resolution path exists.**
  `PublicMenuUrl.menuUrl()` requires merchant slug, branch slug, *and* table
  number (`require()` on all three). The only public-serving endpoint,
  `PublicMenuController`, is a 3-segment route. `QrSignatureService` signs the
  literal triple `merchantId:branchId:tableId` — no shorter overload exists.
  `TenantResolutionGlobalFilter` resolves tenants from `Host` only; it never
  parses a path.

This spec closes those gaps for exactly what phase 1 needs: one menu per
branch, reachable at a path-based URL, with nothing table-scoped.

---

## 1. Menu domain model

### `Menu` — new entity, 1:1 with `Branch`

```
Menu {
  id          UUID (PK)
  branchId    UUID (unique, FK -> branches.id)
  merchantId  UUID (denormalized, matches branchId's owner — avoids a join on every tenant check)
  status      MenuStatus { DRAFT, PUBLISHED }
  publishedAt Instant (nullable — set on first successful publish)
  createdAt / updatedAt
}
```

One menu per branch, not a separately-named, multi-variant concept — this was
confirmed directly: different branches can carry different items and prices,
including items available at only one branch, but a branch does not host
multiple concurrent named menus (no "breakfast vs. dinner" case in phase 1).

`templateId` is **not** added in this spec — menu templates are sub-project 4
and get their own migration once that spec exists. Adding a nullable column
later is a cheap follow-up; guessing its shape now is not.

### `Category` / `Product` move from `merchantId`-scoped to `menuId`-scoped

Both entities gain `menuId` (FK -> `menus.id`) and lose their direct
dependence on `merchantId` for scoping (tenant checks still resolve
`menu -> branch -> merchant` for isolation, same guarantee, one more join).
This is what actually makes "different items/prices per branch" possible —
today it is architecturally impossible, since there is one shared catalog per
merchant.

`MenuService.getFullMenu()` changes signature from `getFullMenu(merchantId)`
to `getFullMenu(menuId)`. Every caller (menu-service controllers, and
order/kitchen/analytics services that currently look up by `merchantId`) is
audited as part of implementation — this is the highest-blast-radius change
in the spec, because those callers are part of the *parked* ordering system,
not this one. They must keep working against the new shape without being
redesigned themselves; see [Migration](#5-migration-and-backfill).

### `Branch.isPrimary`

```sql
ALTER TABLE branches ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT false;
CREATE UNIQUE INDEX branches_one_primary_per_merchant
  ON branches (merchant_id) WHERE is_primary;
```

Exactly one primary branch per merchant, enforced by a Postgres partial
unique index rather than application logic alone — a race between two
concurrent "set primary" requests fails at the database, not silently. The
first branch a merchant creates is auto-primary; reassigning primary happens
through a merchant-settings endpoint that flips the old primary off and the
new one on in one transaction.

---

## 2. URL family

Single shared domain, **path-based**, configured as a new
`app.public-menu-domain` / `PUBLIC_MENU_DOMAIN` property — deliberately
**separate** from the existing `PUBLIC_BASE_DOMAIN` (which stays wired to the
parked subdomain scheme). Example: `PUBLIC_MENU_DOMAIN=menu.safaricom.et`.

| Route | Behavior |
|---|---|
| `/m/{merchant-slug}` | **302 redirect** to the merchant's primary branch's canonical URL below. This is the link/QR the HLD's publish flow hands the merchant — works as a direct-equivalent link for the common single-branch merchant, and gives multi-branch merchants one shareable default with no picker screen. |
| `/m/{merchant-slug}/{branch-slug}` | Canonical, directly-served branch menu. **This is what a branch's own QR code encodes** — scanning it never redirects, so there's no ambiguity about which branch's menu a customer standing in front of a specific QR stand gets. |
| `/m/{merchant-slug}/{branch-slug}/{table-number}` | **Reserved, not built.** Phase 2 extends this exact family for table-scoped ordering instead of requiring a third URL redesign. |

**No published menu anywhere for a merchant:** `/m/{slug}` serves a
"menu coming soon" page rather than 404 — a merchant who has started
onboarding but not yet published should not look broken to a customer who
already has their business card.

**No primary branch designated** (should not happen given the DB default,
but the merchant could theoretically delete their primary branch — deletion
flow reassigns primary to another remaining branch, or if none remain, falls
through to the same "coming soon" page).

### Two public endpoints, one new controller

A new `PublicDigitalMenuController` (menu-service) — a **separate file**
from the existing `PublicMenuController`, which stays untouched:

```
GET /api/v1/public/digital-menu/{merchantSlug}
  -> looks up the primary branch, returns 302 to
     /m/{merchantSlug}/{branchSlug}

GET /api/v1/public/digital-menu/{merchantSlug}/{branchSlug}
  -> resolves branch by slug, its Menu, returns the published
     menu content (404 if the branch's menu isn't PUBLISHED)
```

A distinct path prefix (`digital-menu` rather than reusing `menu`) makes the
two systems trivially greppable as separate, and removes any doubt about
whether a 2-segment request could ever be misrouted to the existing
3-segment table-scoped handler.

### Gateway: new path-based resolution, additive

`TenantResolutionGlobalFilter` extracts the tenant from `Host` and never
touches the path — that's correct for the subdomain scheme and must stay
that way. A new filter (or a guarded branch at the top of the same filter,
implementation's call) does the equivalent for this scheme:

1. Only activates on requests where the path starts with `/m/` or
   `/api/v1/public/digital-menu/`.
2. Extracts `{merchant-slug}` from the path (second segment).
3. Resolves it to `merchantId` via the **existing**
   `GET /api/v1/public/tenants/by-slug/{slug}` endpoint and Redis cache —
   reused as-is, no new resolution infrastructure.
4. Injects `X-Tenant-Id` / `X-Tenant-Slug` exactly as the host-based path
   does downstream.

Because both schemes live on different domains (`menu.safaricom.et` vs.
`qrserve.safaricom.et`), there is no host collision, and the path-prefix
guard means a single shared domain's `Host` header (which would otherwise
extract the nonsensical label `"menu"` from `menu.safaricom.et`) is never
consulted for these routes.

`SecurityConfig` gets a new, narrow `permitAll()` for
`/api/v1/public/digital-menu/**` — placed with the same care as every other
public rule, given `docs/codebase-review.md`'s standing finding that a broad
`permitAll()` placed above narrower authenticated rules silently shadows
them. This one is scoped to the exact prefix, nothing broader.

---

## 3. QR generation and signature

Generated automatically at publish time, per the HLD's own sequence diagram:
publish request -> validate ownership/config -> mark `Menu.status = PUBLISHED`
-> generate the branch's canonical URL -> generate a QR code encoding it ->
store both, return to the merchant.

**New signature scope**, added as a new method on `QrSignatureService`
alongside (not replacing) the existing one:

```
sign(merchantId, branchId) -> covers "merchantId:branchId"
```

Same per-tenant derived-key and rotation-overlap mechanics as the existing
scheme (`docs/superpowers/specs/2026-08-18-...:` Section 3) — those
properties are scheme-agnostic and worth keeping, not worth re-deriving.

---

## 4. Local development

This scheme is simpler to run locally than the subdomain one: no wildcard
DNS trick needed at all, since there's no per-tenant hostname to fake. Any
dev domain works — `PUBLIC_MENU_DOMAIN=localhost:3000` resolves correctly
out of the box, because tenancy is entirely in the path.

---

## 5. Migration and backfill

Structural migration (Flyway): `menus` table, `branches.is_primary` with its
partial unique index, `categories.menu_id` / `products.menu_id` added
**nullable** initially.

**Data backfill is an app-level migration job, not raw SQL.** For each
merchant with N branches:

1. The first branch becomes `is_primary`.
2. Each branch gets one `Menu` row, `status = PUBLISHED` (existing catalogs
   are already always-live today — publishing nothing new does not change
   customer-visible behavior at cutover).
3. **Every branch's `Menu` starts as an independent copy of that merchant's
   current shared catalog** — categories and products are duplicated once
   per branch with new ids, `menuId` set accordingly. A merchant with 3
   branches ends the migration with 3 independently-editable copies of what
   was, until then, one shared list.

This is the single trickiest part of the migration — duplicating a
relational tree (products -> categories) with new primary keys, per branch,
correctly. It's done as a one-off Java migration runner (the pattern already
exists: `AuthServiceApplication.seedDatabase`), not a Flyway SQL script,
because generating new UUIDs and remapping foreign keys is far easier to get
right — and to unit test — in code than in `INSERT ... SELECT`.

`menuId` becomes `NOT NULL` in a follow-up migration once the backfill job
has run and been verified, not in the same deploy.

### Callers of the old `merchantId`-scoped catalog

Order-service, kitchen-service, and analytics-service currently call
menu-service by `merchantId` for the parked ordering flow. Auditing and
updating those call sites to pass a `branchId`/`menuId` (almost certainly
their existing `branchId`, since orders are already branch-scoped) is
in-scope implementation work for this spec, but is **not a redesign of those
services** — they keep working exactly as they do today, just against the
new shape underneath.

---

## 6. Testing gate

| Test | Level | Why |
|---|---|---|
| `/m/{slug}` redirects to the primary branch's canonical URL | Controller/integration | The core UX contract |
| `/m/{slug}/{branch}` serves that branch's published menu, and only if `PUBLISHED` | Controller/integration | "Only Published menus are made available" (HLD 6.2) |
| Two branches of the same merchant have independent items/prices after backfill | Migration test, two-branch fixture | This is the entire point of the redesign — a regression here silently reverts to shared-catalog behavior |
| Existing `/menu/{merchantSlug}/{branchSlug}/{tableNumber}` route and its tests are untouched and still pass | Full existing suite, unchanged | Proves "parked, not removed" held |
| New `permitAll()` on `/api/v1/public/digital-menu/**` doesn't shadow narrower rules and doesn't leak anything outside that prefix | `SecurityConfig` test | Direct instance of the `docs/codebase-review.md` `permitAll` ordering defect class — check for it explicitly this time |
| Backfill job is idempotent (safe to re-run) | Migration test | One-off jobs that aren't idempotent become incident-response tools the first time they need to run twice |

---

## Out of scope

- **Multi-language content, promotional pricing, scheduled availability** —
  separate spec (sub-project 2), builds on the `Menu`/branch-scoped model
  established here but doesn't need it fully implemented first.
- **Object storage/CDN for images** — separate spec (sub-project 3),
  independent of this one.
- **Menu templates** (`Menu.templateId`, template picker, template CRUD) —
  separate spec (sub-project 4). This spec leaves room for it (no field added
  yet) rather than guessing its shape.
- **Customer feedback/reviews** — separate spec (sub-project 5).
- **Back Office Service** — separate spec (sub-project 6), depends on
  templates (4) and feedback (5) existing first.
- **Table-scoped phase-2 QR/ordering** — the URL family reserves space for
  it; nothing here implements it.

## Open items

1. **Final domain.** `menu.safaricom.et` is assumed from the HLD's own
   example. If the actual domain differs, the only change is
   `PUBLIC_MENU_DOMAIN` and its certificate.
2. **Branch deletion UX** when it's the primary branch and other branches
   exist — reassign automatically to another branch, or block deletion until
   the merchant picks a new primary explicitly? Leaning toward "block and
   ask" (safer, avoids a surprise primary change) but this is a product
   decision, not an engineering one.
3. **Order/kitchen/analytics call-site audit** (Section 5) is scoped as
   "update the call, don't touch the service," but the actual list of call
   sites needs enumerating during implementation planning — it wasn't fully
   inventoried in this design pass.
