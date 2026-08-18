# Multi-Tenant Subdomains and QR Generation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve every restaurant on its own subdomain (`{merchantSlug}.qrserve.safaricom.et`) and generate QR codes that actually resolve, with tenant identity established once at the gateway and never inferable from a forged header.

**Architecture:** A reactive `GlobalFilter` in the API gateway strips inbound `X-Tenant-*` headers, extracts the first DNS label from `Host`, resolves it to a `merchantId` through Redis (with negative caching), and injects a trusted `X-Tenant-Id`. Downstream, a servlet filter in `shared:security` reads that header into the existing `TenantContext` and rejects any request where the host's tenant disagrees with the JWT's. QR URLs are built by one shared builder from configuration, signed with a per-tenant key derived from a master secret.

**Tech Stack:** Java 17, Spring Boot 4.1.0, Spring Cloud 2025.1.2 (Gateway WebFlux), Spring Security 7, Spring Data JPA, Redis (reactive in the gateway), JUnit Jupiter 6.0.3, React 19 + Vite 6 + TypeScript 5.8.

**Spec:** [`docs/superpowers/specs/2026-08-18-multi-tenant-subdomain-qr-design.md`](../specs/2026-08-18-multi-tenant-subdomain-qr-design.md)

## Global Constraints

- **Host pattern:** `{merchantSlug}.qrserve.safaricom.et`. The domain is never hardcoded — it comes from `app.public-base-domain` / env `PUBLIC_BASE_DOMAIN`.
- **Public menu URL:** `https://{merchantSlug}.qrserve.safaricom.et/menu/{branchSlug}/{tableNumber}`. Branch and table stay in the path; branches are never subdomains.
- **Reserved labels** (never resolve as a tenant, rejected at merchant creation): `admin`, `api`, `app`, `www`, `static`, `assets`, `ws`, `mail`, `status`.
- **Merchant slug:** owner-supplied, required at creation, 3–40 chars, `[a-z0-9-]`, no leading/trailing hyphen, not entirely numeric, not reserved. **Renames are rejected** ("slug is permanent").
- **Branch slug:** same normalisation, but max 60 chars, minimum 1 char, entirely-numeric permitted, reserved-label check not applied. It is a path segment, not a hostname.
- **Precedence rule:** anonymous requests → the host is the tenant. Authenticated staff → the **JWT is authoritative**; a host/JWT mismatch is **403**. `SUPER_ADMIN` asserts no tenant and operates on `admin.` .
- **The host never grants authority the JWT does not already carry.**
- **Fail closed:** no defaulting to "some tenant" ever. An unresolvable label on the base domain is **404**.
- **`TenantContext` is a `ThreadLocal`** — every filter that sets it MUST clear it in a `finally` block.
- **QR signature** is computed over `{merchantId, branchId, tableId}` (ids, not slugs), with a **per-tenant derived key** `HMAC-SHA256(masterSecret, merchantId)` and a verify-only `qr.signature-secret-previous` rotation overlap.
- **No phased rollout.** The project is pre-launch; the precedence rule is enforced from the first commit.
- **Java 17 language level.** No records-in-switch, no sealed-type pattern matching.
- **Every new `@Component` in `com.qrserve.shared.common` is instantiated by all 8 component-scanning services** (everything except `discovery-service`). A required `@Value` with no default therefore fails 8 services at startup — intentional for correctness-critical config, but it must be added to all 8 `application.yml` files in the same commit.

## File Structure

| File | Responsibility |
|---|---|
| `backend/shared/common/.../Slugs.java` | **new** — the single slug normaliser/validator. Replaces 3 duplicated one-liners. |
| `backend/shared/common/.../PublicMenuUrl.java` | **new** — the only place a public menu URL or tenant host is constructed. |
| `backend/shared/common/.../QrSignatureService.java` | modify — per-tenant derived key + rotation overlap. |
| `backend/shared/common/.../TenantContext.java` | unchanged — already exists, currently unused. |
| `backend/shared/security/.../TenantContextFilter.java` | **new** — header → `TenantContext`, host/JWT mismatch → 403, clears in `finally`. |
| `backend/shared/security/.../SecurityConfig.java` | modify — register the new filter after `JwtAuthenticationFilter`. |
| `backend/api-gateway/.../filter/TenantResolutionGlobalFilter.java` | **new** — strip, extract, resolve, inject. |
| `backend/api-gateway/.../filter/TenantSlugResolver.java` | **new** — Redis-cached slug→id lookup, isolated so the filter stays testable. |
| `backend/merchant-service/.../controller/PublicTenantController.java` | **new** — `GET /api/v1/public/tenants/by-slug/{slug}`. |
| `backend/shared/common/.../TenantCacheKeys.java` | **new** — the Redis key format and TTLs, shared by the writer (gateway) and the invalidator (merchant-service). |
| `backend/merchant-service/.../service/TenantCacheInvalidator.java` | **new** — best-effort cache drop on merchant creation. |
| `backend/merchant-service/.../service/MerchantService.java` | modify — owner-supplied slug, validation, collision suffix, rename guard, cache invalidation. |
| `backend/merchant-service/.../service/BranchService.java` | modify — honour the request slug, validate via `Slugs.toPathSlug`. |
| `backend/merchant-service/.../service/TableService.java` | modify — build the signed QR URL from `PublicMenuUrl`. |
| `backend/merchant-service/.../entity/BranchEntity.java` | modify — `unique(merchant_id, slug)`, drop the derive-in-`@PrePersist`. |
| `backend/merchant-service/.../entity/MerchantEntity.java` | modify — drop the derive-in-`@PrePersist`. |
| `backend/qr-service/.../service/QrGeneratorService.java` | modify — same builder, same signature. |
| `backend/merchant-service/src/test/.../TenantIsolationIT.java` | **new** — the CI gate. Two seeded merchants, extended by later tasks. |
| `src/lib/tenant.ts` | **new** — host-label → tenant slug, and the host-wins-over-path rule. |
| `src/pages/CustomerMenuPage.tsx` | modify — resolve the merchant slug through `src/lib/tenant.ts`. |
| `vite.config.ts` | modify — preserve the `Host` header, allow `*.localtest.me`. |
| `backend/k8s/proxy-ingress.yml` | modify — wildcard host rule + TLS. |

---

### Task 1: `Slugs` — the single slug normaliser

**Why this exists:** the expression `name.toLowerCase().replaceAll("[^a-z0-9]", "-")` is duplicated in `MerchantService:22`, `MerchantEntity:56` and `BranchEntity:46`. It produces `--joe-s-diner-` for `Joe's Diner ` — leading and trailing hyphens are **illegal in a DNS label**, and this string is about to become a tenant's public hostname.

**Files:**
- Create: `backend/shared/common/src/main/java/com/qrserve/shared/common/Slugs.java`
- Test: `backend/shared/common/src/test/java/com/qrserve/shared/common/SlugsTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `Slugs.toDnsLabel(String raw) -> String` — throws `IllegalArgumentException`. Merchant rules.
  - `Slugs.toPathSlug(String raw) -> String` — throws `IllegalArgumentException`. Branch rules.
  - `Slugs.isReserved(String label) -> boolean`
  - `Slugs.RESERVED_LABELS` (`Set<String>`), `Slugs.DNS_LABEL_MIN_LENGTH` (`3`), `Slugs.DNS_LABEL_MAX_LENGTH` (`40`), `Slugs.PATH_SLUG_MAX_LENGTH` (`60`)

`IllegalArgumentException` is deliberate: `GlobalExceptionHandler` already maps it to **400** carrying the exception's own message, and `shared:common` cannot depend on `shared:exceptions` (that dependency runs the other way).

- [ ] **Step 1: Write the failing test**

Create `backend/shared/common/src/test/java/com/qrserve/shared/common/SlugsTest.java`:

```java
package com.qrserve.shared.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every case here is a row from the defect table in the design doc. The old
 * one-line expression got all six wrong, and its output is a public hostname.
 */
class SlugsTest {

    @Test
    @DisplayName("trims the leading and trailing hyphens that are illegal in a DNS label")
    void trimsEdgeHyphens() {
        // Old behaviour: "--joe-s-diner-"
        assertEquals("joe-s-diner", Slugs.toDnsLabel("Joe's Diner "));
    }

    @Test
    @DisplayName("collapses runs of hyphens")
    void collapsesHyphenRuns() {
        // Old behaviour: "sunrise-coffee---tea"
        assertEquals("sunrise-coffee-tea", Slugs.toDnsLabel("Sunrise Coffee & Tea"));
    }

    @Test
    @DisplayName("a name with no Latin characters is rejected, not silently emptied")
    void rejectsNonLatin() {
        // Old behaviour: "------". Amharic-named businesses supply a Latin slug.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Slugs.toDnsLabel("ከፈ አበባ"));
        assertTrue(e.getMessage().contains("Latin"),
                "the message must tell the owner to supply a Latin slug");
    }

    @Test
    @DisplayName("rejects a slug longer than the 40-character cap")
    void rejectsOverlongLabel() {
        assertThrows(IllegalArgumentException.class, () -> Slugs.toDnsLabel("a".repeat(41)));
        assertEquals("a".repeat(40), Slugs.toDnsLabel("a".repeat(40)), "the cap itself is allowed");
    }

    @Test
    @DisplayName("rejects a slug shorter than 3 characters")
    void rejectsTooShortLabel() {
        assertThrows(IllegalArgumentException.class, () -> Slugs.toDnsLabel("ab"));
        assertEquals("abc", Slugs.toDnsLabel("abc"));
    }

    @Test
    @DisplayName("rejects an entirely numeric label — it cannot be a hostname")
    void rejectsNumericLabel() {
        assertThrows(IllegalArgumentException.class, () -> Slugs.toDnsLabel("12345"));
    }

    @Test
    @DisplayName("rejects reserved labels so a tenant cannot claim admin.")
    void rejectsReserved() {
        assertThrows(IllegalArgumentException.class, () -> Slugs.toDnsLabel("Admin"));
        assertThrows(IllegalArgumentException.class, () -> Slugs.toDnsLabel("api"));
        assertTrue(Slugs.isReserved("www"));
        assertFalse(Slugs.isReserved("sunrise"));
    }

    @Test
    @DisplayName("rejects null and blank input")
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> Slugs.toDnsLabel(null));
        assertThrows(IllegalArgumentException.class, () -> Slugs.toDnsLabel("   "));
    }

    @Test
    @DisplayName("an already-valid slug passes through unchanged")
    void idempotent() {
        assertEquals("sunrise-coffee", Slugs.toDnsLabel("sunrise-coffee"));
        assertEquals("sunrise-coffee", Slugs.toDnsLabel(Slugs.toDnsLabel("Sunrise Coffee")));
    }

    // ---- branch (path) slugs: three rules differ ----

    @Test
    @DisplayName("a branch slug may be entirely numeric - a branch called 2 is a valid path segment")
    void pathSlugAllowsNumeric() {
        assertEquals("2", Slugs.toPathSlug("2"));
    }

    @Test
    @DisplayName("a branch slug may use a reserved label - it is a path segment, not a hostname")
    void pathSlugAllowsReserved() {
        assertEquals("admin", Slugs.toPathSlug("Admin"));
    }

    @Test
    @DisplayName("a branch slug is capped at 60, not 40")
    void pathSlugHasLongerCap() {
        assertEquals("a".repeat(60), Slugs.toPathSlug("a".repeat(60)));
        assertThrows(IllegalArgumentException.class, () -> Slugs.toPathSlug("a".repeat(61)));
    }

    @Test
    @DisplayName("a branch slug is still normalised and still cannot be empty")
    void pathSlugStillNormalises() {
        assertEquals("main-hall", Slugs.toPathSlug(" Main   Hall! "));
        assertThrows(IllegalArgumentException.class, () -> Slugs.toPathSlug("!!!"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd backend && ./gradlew :shared:common:test --tests 'com.qrserve.shared.common.SlugsTest'
```

Expected: compilation failure — `cannot find symbol: class Slugs`.

- [ ] **Step 3: Write the implementation**

Create `backend/shared/common/src/main/java/com/qrserve/shared/common/Slugs.java`:

```java
package com.qrserve.shared.common;

import java.util.Locale;
import java.util.Set;

/**
 * The single source of truth for slug normalisation.
 *
 * <p>A merchant slug becomes a DNS label — the tenant's public hostname — so it
 * is validated against DNS label rules rather than merely "cleaned". The
 * expression this replaces, duplicated in three places,
 * {@code name.toLowerCase().replaceAll("[^a-z0-9]", "-")}, produced
 * {@code --joe-s-diner-} for {@code "Joe's Diner "}: leading and trailing
 * hyphens are illegal in a DNS label, and nothing rejected them.
 *
 * <p>Rejection is by exception rather than by silent repair. A slug that cannot
 * be derived — a purely non-Latin name, for instance — must be supplied by the
 * owner rather than guessed at. Transliteration is deliberately not implemented:
 * a mangled machine transliteration would become that business's permanent
 * public address.
 *
 * <p>{@link IllegalArgumentException} is used because {@code GlobalExceptionHandler}
 * maps it to 400 carrying this message, and {@code shared:common} cannot depend
 * on {@code shared:exceptions} — that dependency runs the other way.
 */
public final class Slugs {

    /** Minimum merchant slug length. Below this, hostnames become guessable. */
    public static final int DNS_LABEL_MIN_LENGTH = 3;

    /**
     * Maximum merchant slug length. The DNS label limit is 63; 40 leaves room for
     * future prefixes without reprinting anyone's QR stands.
     */
    public static final int DNS_LABEL_MAX_LENGTH = 40;

    /** Maximum branch slug length. A path segment has far more headroom. */
    public static final int PATH_SLUG_MAX_LENGTH = 60;

    /**
     * Labels that never resolve as a tenant. {@code admin} is where SUPER_ADMIN
     * cross-tenant work lives; the rest are conventional infrastructure names
     * that a tenant claiming them could use to intercept traffic or mislead
     * operators.
     */
    public static final Set<String> RESERVED_LABELS = Set.of(
            "admin", "api", "app", "www", "static", "assets", "ws", "mail", "status");

    private Slugs() {
    }

    /**
     * Normalises and validates a merchant slug as a DNS label.
     *
     * @throws IllegalArgumentException if the result cannot be a hostname label
     */
    public static String toDnsLabel(String raw) {
        String slug = normalize(raw);

        if (slug.isEmpty()) {
            throw new IllegalArgumentException(
                    "slug cannot be derived from this name: it contains no usable Latin letters or digits. "
                            + "Please supply a slug explicitly (lowercase a-z, 0-9 and hyphens).");
        }
        if (slug.length() < DNS_LABEL_MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "slug must be at least " + DNS_LABEL_MIN_LENGTH + " characters: '" + slug + "'");
        }
        if (slug.length() > DNS_LABEL_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "slug must be at most " + DNS_LABEL_MAX_LENGTH + " characters: '" + slug + "'");
        }
        // A purely numeric hostname label is ambiguous with an IP address octet
        // and is rejected outright by some resolvers.
        if (slug.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("slug must not be entirely numeric: '" + slug + "'");
        }
        if (isReserved(slug)) {
            throw new IllegalArgumentException("slug '" + slug + "' is reserved and cannot be used");
        }
        return slug;
    }

    /**
     * Normalises and validates a branch slug as a URL path segment.
     *
     * <p>Three rules differ from {@link #toDnsLabel}: the cap is
     * {@value #PATH_SLUG_MAX_LENGTH}, a single character is enough, and an
     * entirely numeric or reserved value is allowed. A branch legitimately named
     * "2" is a perfectly good path segment, and a branch named "admin" cannot
     * collide with a subdomain because it never becomes one.
     */
    public static String toPathSlug(String raw) {
        String slug = normalize(raw);

        if (slug.isEmpty()) {
            throw new IllegalArgumentException(
                    "slug cannot be derived from this name: it contains no usable Latin letters or digits. "
                            + "Please supply a slug explicitly (lowercase a-z, 0-9 and hyphens).");
        }
        if (slug.length() > PATH_SLUG_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "slug must be at most " + PATH_SLUG_MAX_LENGTH + " characters: '" + slug + "'");
        }
        return slug;
    }

    /** True if the label is reserved for platform use. Case-insensitive. */
    public static boolean isReserved(String label) {
        return label != null && RESERVED_LABELS.contains(label.toLowerCase(Locale.ROOT));
    }

    /**
     * Lowercases, replaces every character outside {@code [a-z0-9]} with a
     * hyphen, collapses hyphen runs, and trims the edges. Returns "" when nothing
     * usable survives — callers decide whether that is fatal.
     */
    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("slug source must not be blank");
        }
        String lowered = raw.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lowered.length());
        boolean lastWasHyphen = false;
        for (int i = 0; i < lowered.length(); i++) {
            char c = lowered.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (allowed) {
                out.append(c);
                lastWasHyphen = false;
            } else if (!lastWasHyphen) {
                out.append('-');
                lastWasHyphen = true;
            }
        }
        int start = 0;
        int end = out.length();
        while (start < end && out.charAt(start) == '-') {
            start++;
        }
        while (end > start && out.charAt(end - 1) == '-') {
            end--;
        }
        return out.substring(start, end);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd backend && ./gradlew :shared:common:test --tests 'com.qrserve.shared.common.SlugsTest'
```

Expected: `BUILD SUCCESSFUL`, 13 tests passing.

- [ ] **Step 5: Commit**

```bash
git add backend/shared/common/src/main/java/com/qrserve/shared/common/Slugs.java backend/shared/common/src/test/java/com/qrserve/shared/common/SlugsTest.java
git commit -m "feat(tenancy): add Slugs, the single DNS-label-safe slug normaliser"
```

---

### Task 2: `PublicMenuUrl` — the only place a public URL is built

**Why this exists:** `TableService:48` and `QrGeneratorService:52` each build the QR URL, each carries a comment claiming consistency with the other, and **both drifted from the actual customer route anyway**. Both emit `/menu/{merchantSlug}/{branchId}/{tableId}` while `PublicMenuResolutionService` resolves the branch **by slug** and the table **by table number**. Every QR code the system has ever generated 404s. Two copies of a URL format is the mechanism by which that happened.

**Files:**
- Create: `backend/shared/common/src/main/java/com/qrserve/shared/common/PublicMenuUrl.java`
- Test: `backend/shared/common/src/test/java/com/qrserve/shared/common/PublicMenuUrlTest.java`
- Modify: all 8 `application.yml` files that already declare `qr.signature-secret` — `analytics-service`, `api-gateway`, `auth-service`, `menu-service`, `merchant-service`, `notification-service`, `order-service`, `qr-service`
- Modify: `backend/.env.example`, `backend/docker-compose.yml`, `backend/k8s/deployment.yml`

**Interfaces:**
- Consumes: nothing.
- Produces (Spring `@Component`, injectable):
  - `PublicMenuUrl(String baseDomain, String scheme)` — constructor bound to `${app.public-base-domain}` and `${app.public-url-scheme:https}`
  - `tenantHost(String merchantSlug) -> String` — e.g. `sunrise.qrserve.safaricom.et`
  - `menuUrl(String merchantSlug, String branchSlug, String tableNumber) -> String`
  - `menuUrl(String merchantSlug, String branchSlug, String tableNumber, String signature) -> String`
  - `baseDomain() -> String`

**Why `app.public-base-domain` has no default:** a default would silently produce wrong QR codes, which is precisely defect 1 and 2 recurring. It follows the fail-fast convention already established for `jwt.secret` and `qr.signature-secret`. Because `PublicMenuUrl` is a `@Component` in `com.qrserve.shared.common`, all 8 component-scanning services instantiate it — so the property must land in all 8 YAML files in this same commit or those services will not start. `discovery-service` does not component-scan `shared.common` and needs nothing.

- [ ] **Step 1: Write the failing test**

Create `backend/shared/common/src/test/java/com/qrserve/shared/common/PublicMenuUrlTest.java`:

```java
package com.qrserve.shared.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicMenuUrlTest {

    private final PublicMenuUrl urls = new PublicMenuUrl("qrserve.safaricom.et", "https");

    @Test
    @DisplayName("the merchant is the host label, the branch and table are the path")
    void buildsTheCanonicalUrl() {
        assertEquals("https://sunrise.qrserve.safaricom.et/menu/main/12",
                urls.menuUrl("sunrise", "main", "12"));
    }

    @Test
    @DisplayName("the tenant host is derived from configuration, never hardcoded")
    void buildsTenantHost() {
        assertEquals("sunrise.qrserve.safaricom.et", urls.tenantHost("sunrise"));
        assertEquals("qrserve.safaricom.et", urls.baseDomain());
    }

    @Test
    @DisplayName("appends the signature as a query parameter when supplied")
    void appendsSignature() {
        assertEquals("https://sunrise.qrserve.safaricom.et/menu/main/12?signature=abc-123_x",
                urls.menuUrl("sunrise", "main", "12", "abc-123_x"));
    }

    @Test
    @DisplayName("a blank signature is omitted rather than emitted empty")
    void omitsBlankSignature() {
        assertEquals("https://sunrise.qrserve.safaricom.et/menu/main/12",
                urls.menuUrl("sunrise", "main", "12", "  "));
        assertEquals("https://sunrise.qrserve.safaricom.et/menu/main/12",
                urls.menuUrl("sunrise", "main", "12", null));
    }

    @Test
    @DisplayName("path segments are URL-encoded so an odd table number cannot break the URL")
    void encodesPathSegments() {
        // A table number is a free-text column; "A 1" must not emit a raw space.
        assertEquals("https://sunrise.qrserve.safaricom.et/menu/main/A%201",
                urls.menuUrl("sunrise", "main", "A 1"));
    }

    @Test
    @DisplayName("the scheme is configurable so local development can use http")
    void honoursScheme() {
        PublicMenuUrl dev = new PublicMenuUrl("localtest.me:3000", "http");
        assertEquals("http://sunrise.localtest.me:3000/menu/main/12",
                dev.menuUrl("sunrise", "main", "12"));
    }

    @Test
    @DisplayName("a missing base domain fails fast at construction, not at scan time")
    void requiresBaseDomain() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new PublicMenuUrl("  ", "https"));
        assertTrue(e.getMessage().contains("PUBLIC_BASE_DOMAIN"),
                "the message must name the environment variable an operator has to set");
    }

    @Test
    @DisplayName("a leading dot on the configured domain is tolerated")
    void tolerantOfLeadingDot() {
        assertEquals("https://sunrise.qrserve.safaricom.et/menu/main/12",
                new PublicMenuUrl(".qrserve.safaricom.et", "https").menuUrl("sunrise", "main", "12"));
    }

    @Test
    @DisplayName("a null or blank slug is rejected rather than producing https://.domain")
    void rejectsBlankSlug() {
        assertThrows(IllegalArgumentException.class, () -> urls.tenantHost(null));
        assertThrows(IllegalArgumentException.class, () -> urls.menuUrl("", "main", "12"));
        assertThrows(IllegalArgumentException.class, () -> urls.menuUrl("sunrise", "", "12"));
        assertThrows(IllegalArgumentException.class, () -> urls.menuUrl("sunrise", "main", ""));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd backend && ./gradlew :shared:common:test --tests 'com.qrserve.shared.common.PublicMenuUrlTest'
```

Expected: compilation failure — `cannot find symbol: class PublicMenuUrl`.

- [ ] **Step 3: Write the implementation**

Create `backend/shared/common/src/main/java/com/qrserve/shared/common/PublicMenuUrl.java`:

```java
package com.qrserve.shared.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds the public, customer-facing URLs for a tenant. This is the only place
 * either the tenant host or the menu URL is constructed.
 *
 * <p>Two copies of this format previously existed — in {@code TableService} and
 * {@code QrGeneratorService} — each with a comment asserting it matched the
 * other. Both were wrong in the same two ways: they hardcoded
 * {@code https://qrserve.com} and emitted {@code /menu/{slug}/{branchId}/{tableId}}
 * while the resolver looks up the branch by <em>slug</em> and the table by
 * <em>table number</em>. Every QR code ever generated resolved to a 404. One
 * builder means the next format change cannot half-land.
 *
 * <p>{@code app.public-base-domain} has no default on purpose. A default would
 * silently emit QR codes pointing at the wrong host — the failure mode is a
 * printed sheet of paper that does not work, discovered by a customer. Failing
 * at startup is cheaper.
 */
@Component
public class PublicMenuUrl {

    private final String baseDomain;
    private final String scheme;

    public PublicMenuUrl(
            @Value("${app.public-base-domain}") String baseDomain,
            @Value("${app.public-url-scheme:https}") String scheme) {
        if (baseDomain == null || baseDomain.isBlank()) {
            throw new IllegalStateException(
                    "app.public-base-domain must be configured via PUBLIC_BASE_DOMAIN "
                            + "(for example qrserve.safaricom.et)");
        }
        // A leading dot is how wildcard certificates are usually written
        // (".qrserve.safaricom.et"), so accept it rather than emit "https://x..domain".
        String trimmed = baseDomain.trim();
        this.baseDomain = trimmed.startsWith(".") ? trimmed.substring(1) : trimmed;
        this.scheme = (scheme == null || scheme.isBlank()) ? "https" : scheme.trim();
    }

    /** The configured base domain, without any leading dot. */
    public String baseDomain() {
        return baseDomain;
    }

    /** {@code sunrise} -> {@code sunrise.qrserve.safaricom.et}. */
    public String tenantHost(String merchantSlug) {
        require(merchantSlug, "merchantSlug");
        return merchantSlug + "." + baseDomain;
    }

    /** The canonical public menu URL, unsigned. */
    public String menuUrl(String merchantSlug, String branchSlug, String tableNumber) {
        return menuUrl(merchantSlug, branchSlug, tableNumber, null);
    }

    /**
     * The canonical public menu URL. The signature is appended only when present,
     * so an unsigned demo URL does not carry an empty parameter that the resolver
     * would then have to treat as "supplied but invalid".
     */
    public String menuUrl(String merchantSlug, String branchSlug, String tableNumber, String signature) {
        require(merchantSlug, "merchantSlug");
        require(branchSlug, "branchSlug");
        require(tableNumber, "tableNumber");

        StringBuilder url = new StringBuilder()
                .append(scheme).append("://")
                .append(tenantHost(merchantSlug))
                .append("/menu/")
                .append(encode(branchSlug))
                .append('/')
                .append(encode(tableNumber));

        if (signature != null && !signature.isBlank()) {
            url.append("?signature=").append(encode(signature));
        }
        return url.toString();
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required to build a public menu URL");
        }
    }

    /**
     * URL-encodes a path segment. {@code table_number} is a free-text column, so a
     * value like {@code "A 1"} would otherwise emit a raw space and truncate the
     * URL inside a QR code. {@code URLEncoder} is form-encoding, so the {@code +}
     * it produces for a space is converted to {@code %20} for path use.
     */
    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd backend && ./gradlew :shared:common:test --tests 'com.qrserve.shared.common.PublicMenuUrlTest'
```

Expected: `BUILD SUCCESSFUL`, 9 tests passing.

- [ ] **Step 5: Add the property to all 8 component-scanning services**

In each of these files, add the `app` block immediately above the existing `qr:` block:

- `backend/analytics-service/src/main/resources/application.yml`
- `backend/api-gateway/src/main/resources/application.yml`
- `backend/auth-service/src/main/resources/application.yml`
- `backend/menu-service/src/main/resources/application.yml`
- `backend/merchant-service/src/main/resources/application.yml`
- `backend/notification-service/src/main/resources/application.yml`
- `backend/order-service/src/main/resources/application.yml`
- `backend/qr-service/src/main/resources/application.yml`

```yaml
# The tenant base domain. Required, with no default: PublicMenuUrl is a
# @Component in com.qrserve.shared.common, which every one of these services
# component-scans, and a default would let a service start while emitting QR
# codes that point at the wrong host. A wrong-but-plausible printed QR code is
# discovered by a customer holding a phone; a startup failure is discovered by
# whoever ran the deploy.
app:
  public-base-domain: ${PUBLIC_BASE_DOMAIN}
  public-url-scheme: ${PUBLIC_URL_SCHEME:https}
```

- [ ] **Step 6: Add the variable to the three deployment surfaces**

In `backend/.env.example`, after the `QR_SIGNATURE_SECRET=` line:

```bash
# Public tenant base domain. Each merchant is served at
# {merchantSlug}.${PUBLIC_BASE_DOMAIN}. Every service fails to start without it.
# Local development: use a public wildcard that resolves to loopback, so tenant
# subdomains work with no /etc/hosts editing:
#   PUBLIC_BASE_DOMAIN=localtest.me:3000
#   PUBLIC_URL_SCHEME=http
PUBLIC_BASE_DOMAIN=
PUBLIC_URL_SCHEME=https
```

In `backend/docker-compose.yml`, add to the `environment:` list of every service that already has a `QR_SIGNATURE_SECRET` line (8 of them):

```yaml
      - PUBLIC_BASE_DOMAIN=${PUBLIC_BASE_DOMAIN:?PUBLIC_BASE_DOMAIN must be set}
      - PUBLIC_URL_SCHEME=${PUBLIC_URL_SCHEME:-https}
```

In `backend/k8s/deployment.yml`, add to the `env:` list of every container that already has a `QR_SIGNATURE_SECRET` entry:

```yaml
        - name: PUBLIC_BASE_DOMAIN
          valueFrom:
            configMapKeyRef:
              name: qrserve-config
              key: PUBLIC_BASE_DOMAIN
```

- [ ] **Step 7: Verify every YAML file still parses**

```bash
cd backend && python -c "
import glob, sys, yaml
for f in glob.glob('*/src/main/resources/application.yml') + ['docker-compose.yml', 'k8s/deployment.yml']:
    list(yaml.safe_load_all(open(f, encoding='utf-8')))
    print('ok', f)
"
```

Expected: `ok` for every file. A YAML indentation slip here is otherwise only discovered at deploy time.

- [ ] **Step 8: Confirm the property is present in exactly the services that need it**

```bash
cd backend && grep -l "public-base-domain" */src/main/resources/application.yml | wc -l
```

Expected: `8`. If it prints 9, `discovery-service` was edited unnecessarily; if fewer, a service will fail to start.

- [ ] **Step 9: Commit**

```bash
git add backend/shared/common/src/main/java/com/qrserve/shared/common/PublicMenuUrl.java \
        backend/shared/common/src/test/java/com/qrserve/shared/common/PublicMenuUrlTest.java \
        backend/*/src/main/resources/application.yml \
        backend/.env.example backend/docker-compose.yml backend/k8s/deployment.yml
git commit -m "feat(tenancy): add PublicMenuUrl as the single public URL builder"
```

---

### Task 3: Per-tenant QR signing key and rotation overlap

**Why this exists:** two reasons, both operational.

1. Today one secret signs every tenant's QR codes. A leaked or brute-forced secret lets an attacker mint valid QR payloads for **every restaurant on the platform**. Deriving a per-tenant key confines that to one restaurant, and stores nothing extra — the key is derived on demand from the master secret and the merchant id.
2. Rotating `QR_SIGNATURE_SECRET` invalidates every printed code. That was already experienced once during the secret-hardening work on this branch. For a hosted service with physically printed table stands, "rotate the secret" must not mean "reprint every stand in every restaurant." A verify-only previous secret makes rotation: set previous to the old value, current to the new, reprint at leisure, drop previous later.

**Files:**
- Modify: `backend/shared/common/src/main/java/com/qrserve/shared/common/QrSignatureService.java`
- Test: `backend/shared/common/src/test/java/com/qrserve/shared/common/QrSignatureServiceTest.java`
- Modify: the 8 `application.yml` files listed in Task 2 (add `signature-secret-previous`)
- Modify: `backend/.env.example`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces (constructor signature **changes** — it currently takes one argument):
  - `QrSignatureService(String secret, String previousSecret)` — bound to `${qr.signature-secret}` and `${qr.signature-secret-previous:}`
  - `generateSignature(UUID merchantId, Long branchId, Long tableId) -> String` — unchanged signature, different output
  - `validateSignature(String signature, UUID merchantId, Long branchId, Long tableId) -> boolean` — unchanged signature

Existing callers (`PublicMenuResolutionService:51`, `PublicCustomerRequestController:54`) need no change: only the constructor and the internals differ.

**Note on the migration:** signatures generated before this change will no longer validate. That is acceptable and costs nothing — no QR code in existence carries a signature at all. `grep -rn "generateSignature"` finds **no production caller**; the signature was validate-only, which is why the endpoints remained effectively unsigned. Task 7 is what starts emitting them.

- [ ] **Step 1: Write the failing test**

Create `backend/shared/common/src/test/java/com/qrserve/shared/common/QrSignatureServiceTest.java`:

```java
package com.qrserve.shared.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrSignatureServiceTest {

    private static final UUID MERCHANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MERCHANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final QrSignatureService service = new QrSignatureService("master-secret-value", "");

    @Test
    @DisplayName("a signature it generated validates")
    void roundTrips() {
        String sig = service.generateSignature(MERCHANT_A, 1L, 5L);
        assertTrue(service.validateSignature(sig, MERCHANT_A, 1L, 5L));
    }

    @Test
    @DisplayName("the same table triple under a different merchant produces a different signature")
    void keyIsDerivedPerTenant() {
        String sigA = service.generateSignature(MERCHANT_A, 1L, 5L);
        String sigB = service.generateSignature(MERCHANT_B, 1L, 5L);
        assertNotEquals(sigA, sigB);
    }

    @Test
    @DisplayName("merchant A's signature does not validate for merchant B")
    void signatureIsNotPortableAcrossTenants() {
        String sigA = service.generateSignature(MERCHANT_A, 1L, 5L);
        // This is the property that matters: compromising one tenant's derived key
        // must not yield a forgery for another tenant.
        assertFalse(service.validateSignature(sigA, MERCHANT_B, 1L, 5L));
    }

    @Test
    @DisplayName("changing the branch or table invalidates the signature")
    void signatureCoversAllThreeIds() {
        String sig = service.generateSignature(MERCHANT_A, 1L, 5L);
        assertFalse(service.validateSignature(sig, MERCHANT_A, 2L, 5L));
        assertFalse(service.validateSignature(sig, MERCHANT_A, 1L, 6L));
    }

    @Test
    @DisplayName("a null or blank signature is rejected, never treated as absent-and-fine")
    void rejectsMissingSignature() {
        assertFalse(service.validateSignature(null, MERCHANT_A, 1L, 5L));
        assertFalse(service.validateSignature("", MERCHANT_A, 1L, 5L));
        assertFalse(service.validateSignature("   ", MERCHANT_A, 1L, 5L));
    }

    @Test
    @DisplayName("a signature from a different master secret is rejected")
    void rejectsForeignSecret() {
        String sig = new QrSignatureService("some-other-master", "").generateSignature(MERCHANT_A, 1L, 5L);
        assertFalse(service.validateSignature(sig, MERCHANT_A, 1L, 5L));
    }

    @Test
    @DisplayName("during rotation, a code signed with the previous secret still validates")
    void rotationOverlapAcceptsPreviousSecret() {
        QrSignatureService old = new QrSignatureService("old-master", "");
        String printed = old.generateSignature(MERCHANT_A, 1L, 5L);

        QrSignatureService rotating = new QrSignatureService("new-master", "old-master");
        // A printed table stand keeps working across a secret rotation. Without
        // this, rotating the secret means reprinting every stand in every
        // restaurant on the platform.
        assertTrue(rotating.validateSignature(printed, MERCHANT_A, 1L, 5L));
    }

    @Test
    @DisplayName("rotation only ever signs with the current secret")
    void rotationSignsWithCurrentOnly() {
        QrSignatureService rotating = new QrSignatureService("new-master", "old-master");
        String fresh = rotating.generateSignature(MERCHANT_A, 1L, 5L);

        assertTrue(new QrSignatureService("new-master", "").validateSignature(fresh, MERCHANT_A, 1L, 5L));
        assertFalse(new QrSignatureService("old-master", "").validateSignature(fresh, MERCHANT_A, 1L, 5L),
                "a newly issued code must not be valid under the retired secret");
    }

    @Test
    @DisplayName("once the previous secret is dropped, old codes stop validating")
    void droppingPreviousEndsTheOverlap() {
        String printed = new QrSignatureService("old-master", "").generateSignature(MERCHANT_A, 1L, 5L);
        assertFalse(new QrSignatureService("new-master", "").validateSignature(printed, MERCHANT_A, 1L, 5L));
    }

    @Test
    @DisplayName("a blank master secret fails fast")
    void requiresSecret() {
        assertThrows(IllegalStateException.class, () -> new QrSignatureService("  ", ""));
        assertThrows(IllegalStateException.class, () -> new QrSignatureService(null, ""));
    }

    @Test
    @DisplayName("a null merchant id is rejected rather than signing the literal string null")
    void rejectsNullMerchant() {
        assertThrows(IllegalArgumentException.class, () -> service.generateSignature(null, 1L, 5L));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd backend && ./gradlew :shared:common:test --tests 'com.qrserve.shared.common.QrSignatureServiceTest'
```

Expected: compilation failure — the constructor takes one argument, not two.

- [ ] **Step 3: Rewrite `QrSignatureService`**

Replace `backend/shared/common/src/main/java/com/qrserve/shared/common/QrSignatureService.java` entirely:

```java
package com.qrserve.shared.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

/**
 * HMAC-SHA256 signature over a QR code's {@code {merchantId, branchId, tableId}}
 * triple, proving a scan came from a code this platform issued.
 *
 * <h2>Per-tenant derived key</h2>
 * The signing key is not the master secret. It is
 * {@code HMAC-SHA256(masterSecret, merchantId)}, derived on demand — nothing extra
 * is stored. In a shared multi-tenant deployment a single global key means one
 * leaked or brute-forced value lets an attacker mint valid codes for every
 * restaurant on the platform. Derivation confines the damage to one tenant.
 *
 * <h2>Rotation overlap</h2>
 * Signing always uses the current secret; validation accepts the current secret
 * <em>or</em> an optional {@code qr.signature-secret-previous}. QR codes are
 * printed onto physical table stands, so rotating the secret must not mean
 * reprinting every stand in every restaurant. Rotation becomes: set previous to
 * the old value, current to the new, reprint at leisure, then drop previous.
 *
 * <h2>Signed over ids, not slugs</h2>
 * Ids are permanently stable. Slugs are stable only because renames are currently
 * rejected; signing them would couple a future rename feature to a physical
 * reprint. Choosing ids costs nothing now and removes that coupling later.
 */
@Component
@Slf4j
public class QrSignatureService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;
    /** Empty when no rotation is in progress. Verify-only — never used to sign. */
    private final String previousSecret;

    public QrSignatureService(
            @Value("${qr.signature-secret}") String secret,
            @Value("${qr.signature-secret-previous:}") String previousSecret) {
        // Fail fast: no default secret. Deployment must set QR_SIGNATURE_SECRET;
        // otherwise Spring throws at startup instead of silently signing with a
        // known value.
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("qr.signature-secret must be configured via QR_SIGNATURE_SECRET");
        }
        this.secret = secret;
        this.previousSecret = previousSecret == null ? "" : previousSecret.trim();
        if (!this.previousSecret.isBlank()) {
            log.info("QR signature rotation overlap is active: codes signed with the previous secret "
                    + "will still validate. Drop qr.signature-secret-previous once reprinting is complete.");
        }
    }

    /** Signs with the current secret only. */
    public String generateSignature(UUID merchantId, Long branchId, Long tableId) {
        return sign(secret, merchantId, branchId, tableId);
    }

    /**
     * Validates against the current secret, then the previous one if a rotation is
     * in progress. Both candidates are compared in constant time.
     */
    public boolean validateSignature(String signature, UUID merchantId, Long branchId, Long tableId) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        boolean valid = constantTimeEquals(signature, sign(secret, merchantId, branchId, tableId));
        if (!valid && !previousSecret.isBlank()) {
            valid = constantTimeEquals(signature, sign(previousSecret, merchantId, branchId, tableId));
        }
        return valid;
    }

    private String sign(String masterSecret, UUID merchantId, Long branchId, Long tableId) {
        if (merchantId == null) {
            // Otherwise the canonical payload would contain the literal "null",
            // making every null-merchant signature interchangeable.
            throw new IllegalArgumentException("merchantId is required to sign a QR payload");
        }
        String payload = merchantId + ":" + branchId + ":" + tableId;
        return mac(deriveTenantKey(masterSecret, merchantId), payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * {@code HMAC-SHA256(masterSecret, merchantId)}. The raw MAC bytes become the
     * signing key for this tenant.
     */
    private byte[] deriveTenantKey(String masterSecret, UUID merchantId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(masterSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(merchantId.toString().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("QR tenant key derivation failed", e);
            throw new IllegalStateException("QR tenant key derivation failed", e);
        }
    }

    private String mac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(message));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("QR signature generation failed", e);
            throw new IllegalStateException("QR signature generation failed", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd backend && ./gradlew :shared:common:test
```

Expected: `BUILD SUCCESSFUL`. All three `shared:common` test classes pass (33 tests total).

- [ ] **Step 5: Add the rotation property to the 8 services and `.env.example`**

In each of the 8 `application.yml` files, extend the existing `qr:` block:

```yaml
qr:
  signature-secret: ${QR_SIGNATURE_SECRET}
  # Verify-only. Set to the OUTGOING secret during a rotation so QR codes already
  # printed onto table stands keep validating; remove it once reprinting is done.
  signature-secret-previous: ${QR_SIGNATURE_SECRET_PREVIOUS:}
```

In `backend/.env.example`:

```bash
# Set only during a secret rotation, to the value being retired. Codes signed
# with it still validate; new codes are always signed with QR_SIGNATURE_SECRET.
QR_SIGNATURE_SECRET_PREVIOUS=
```

- [ ] **Step 6: Verify the YAML and confirm the rotation path is wired**

```bash
cd backend && python -c "
import glob, yaml
for f in glob.glob('*/src/main/resources/application.yml'):
    list(yaml.safe_load_all(open(f, encoding='utf-8')))
print('yaml ok')
" && grep -c "signature-secret-previous" */src/main/resources/application.yml | grep -v ":0"
```

Expected: `yaml ok`, then 8 lines each ending `:1`.

- [ ] **Step 7: Commit**

```bash
git add backend/shared/common/src/main/java/com/qrserve/shared/common/QrSignatureService.java \
        backend/shared/common/src/test/java/com/qrserve/shared/common/QrSignatureServiceTest.java \
        backend/*/src/main/resources/application.yml backend/.env.example
git commit -m "feat(tenancy): derive QR signing keys per tenant, add rotation overlap"
```

---

### Task 4: The two-merchant isolation gate

**Why this comes fourth and not last:** in a shared multi-tenant deployment, an isolation defect is *one restaurant reading another's revenue* — a breach between paying customers. Six cross-tenant holes were found and fixed on this branch (analytics, customer requests, waiter tasks, user listing, table listing, STOMP subscriptions); that density implies more exist. This test is the gate that protects every later task in this plan, so it must exist and pass **before** the data model and the tenant filter change underneath it. `backend/scripts/smoke-tenant-isolation.sh` already performs these checks against a running stack, but nothing runs it — promoting its assertions into a `@SpringBootTest` is what turns it into a gate.

This task establishes the harness. Tasks 5, 6 and 12 extend it.

**Files:**
- Create: `backend/merchant-service/src/test/resources/application-test.yml`
- Create: `backend/merchant-service/src/test/java/com/qrserve/merchant/TenantIsolationIT.java`

**Interfaces:**
- Consumes: nothing.
- Produces, for later tasks to extend:
  - `TenantIsolationIT.MERCHANT_A_NAME` / `MERCHANT_B_NAME` constants
  - `TenantIsolationIT#seed()` — creates two merchants, one branch and one table each
  - `TenantIsolationIT#tokenFor(UUID merchantId, UserRole role) -> String` — a bearer value including the `Bearer ` prefix
  - fields `merchantA`, `merchantB` (`MerchantEntity`), `branchA`, `branchB` (`BranchEntity`), `tableA`, `tableB` (`TableEntity`)

**Boot notes for the implementer.** merchant-service pulls in Eureka, Kafka and Redis. The test profile disables Eureka, and `MerchantEventPublisher` is replaced with a mock because `KafkaTemplate.send` would otherwise block trying to reach a broker that is not running. Redis auto-configuration creates its connection factory lazily and is never touched by these endpoints, so it needs no stub.

**Why `MockMvc` is built by hand.** Spring Boot 4 split `@AutoConfigureMockMvc` out into a `spring-boot-webmvc-test-autoconfigure` module that this project does not depend on — `spring-boot-starter-security-test` brings `spring-boot-security-test`, which is not the same thing. Rather than add a dependency, build `MockMvc` from the `WebApplicationContext` with `SecurityMockMvcConfigurers.springSecurity()`, which needs only `spring-test` and `spring-security-test`. Both are already on the test path. The `springSecurity()` call is not optional: without it the filter chain is absent, every request runs unauthorized, and the cross-tenant assertions pass for the wrong reason.

- [ ] **Step 1: Write the test profile**

Create `backend/merchant-service/src/test/resources/application-test.yml`:

```yaml
spring:
  datasource:
    # H2 in PostgreSQL compatibility mode: the entities use PostgreSQL naming and
    # a UUID primary key, and "tables" is a reserved word that needs the
    # non-keyword setting to be creatable.
    url: jdbc:h2:mem:merchant-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=TABLES,USER,VALUE
    driver-class-name: org.h2.Driver
    username: sa
    password: ''
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: false
  cloud:
    discovery:
      enabled: false

eureka:
  client:
    enabled: false
    register-with-eureka: false
    fetch-registry: false

jwt:
  secret: dGVzdC1zZWNyZXQtdmFsdWUtd2l0aC1lbm91Z2gtbGVuZ3RoLWZvci1oczI1Ng==
  access-expiration-ms: 3600000
  refresh-expiration-ms: 604800000

qr:
  signature-secret: test-qr-master-secret
  signature-secret-previous: ''

app:
  public-base-domain: qrserve.test
  public-url-scheme: https

logging:
  level:
    org.springframework.web: WARN
```

- [ ] **Step 2: Write the failing test**

Create `backend/merchant-service/src/test/java/com/qrserve/merchant/TenantIsolationIT.java`:

```java
package com.qrserve.merchant;

import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.repository.BranchRepository;
import com.qrserve.merchant.repository.MerchantRepository;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.merchant.service.MerchantEventPublisher;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The tenant isolation gate.
 *
 * <p>QRServe is a shared multi-tenant deployment: one set of services, many
 * restaurants, separated only by {@code merchantId}. An isolation defect here is
 * not an internal inconsistency — it is one paying customer reading another's
 * revenue. Six such holes were found and fixed on this branch, which is reason
 * enough to treat isolation as a CI gate rather than a review habit.
 *
 * <p>Every assertion is stated as "merchant A's credential must not reach
 * merchant B's data". New tenant-scoped endpoints belong here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class TenantIsolationIT {

    static final String MERCHANT_A_NAME = "Sunrise Coffee";
    static final String MERCHANT_B_NAME = "Blue Nile Restaurant";

    /**
     * MockMvc is built by hand rather than injected via {@code @AutoConfigureMockMvc}.
     * Spring Boot 4 moved that annotation into a separate
     * {@code spring-boot-webmvc-test-autoconfigure} module which this project does
     * not depend on, and building it here needs only {@code spring-test} plus
     * {@code spring-security-test}, both already present.
     *
     * <p>It also makes the important part explicit: {@code springSecurity()} installs
     * the real filter chain. Without it every assertion below would exercise the
     * controller with no authorization at all and pass for the wrong reason.
     */
    @Autowired
    WebApplicationContext webApplicationContext;

    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;
    @Autowired
    MerchantRepository merchantRepository;
    @Autowired
    BranchRepository branchRepository;
    @Autowired
    TableRepository tableRepository;

    /**
     * Kafka is not running under test and {@code KafkaTemplate.send} would block
     * trying to reach a broker. No assertion here concerns events.
     */
    @MockitoBean
    MerchantEventPublisher merchantEventPublisher;

    MerchantEntity merchantA;
    MerchantEntity merchantB;
    BranchEntity branchA;
    BranchEntity branchB;
    TableEntity tableA;
    TableEntity tableB;

    @BeforeEach
    void seed() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        tableRepository.deleteAll();
        branchRepository.deleteAll();
        merchantRepository.deleteAll();

        merchantA = merchantRepository.save(merchant(MERCHANT_A_NAME, "sunrise"));
        merchantB = merchantRepository.save(merchant(MERCHANT_B_NAME, "blue-nile"));

        // Both tenants deliberately name their branch "Main". Under the old
        // globally-unique branch slug the second save threw a constraint
        // violation, which is the whole point of the schema change in Task 5.
        branchA = branchRepository.save(branch(merchantA.getId(), "Main", "main"));
        branchB = branchRepository.save(branch(merchantB.getId(), "Main", "main"));

        tableA = tableRepository.save(table(merchantA.getId(), branchA.getId(), "1", "qr-a-1"));
        tableB = tableRepository.save(table(merchantB.getId(), branchB.getId(), "1", "qr-b-1"));
    }

    private MerchantEntity merchant(String name, String slug) {
        return MerchantEntity.builder()
                .name(name).slug(slug).phone("+251900000000")
                .city("Addis Ababa").address("Bole").category("CAFE")
                .build();
    }

    private BranchEntity branch(UUID merchantId, String name, String slug) {
        return BranchEntity.builder()
                .merchantId(merchantId).name(name).slug(slug)
                .phone("+251900000000").address("Bole")
                .build();
    }

    private TableEntity table(UUID merchantId, Long branchId, String number, String qrToken) {
        return TableEntity.builder()
                .merchantId(merchantId).branchId(branchId).tableNumber(number)
                .capacity(4).status("AVAILABLE").qrToken(qrToken)
                .build();
    }

    /** A bearer header value, {@code Bearer } prefix included. */
    String tokenFor(UUID merchantId, UserRole role) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .merchantId(merchantId)
                .email(role.name().toLowerCase() + "@" + merchantId + ".test")
                .role(role)
                .build();
        return "Bearer " + jwtTokenProvider.generateAccessToken(principal);
    }

    // ---- GET /api/merchants/{id} ----

    @Test
    @DisplayName("an owner cannot read another merchant's profile")
    void ownerCannotReadForeignMerchant() throws Exception {
        mockMvc.perform(get("/api/merchants/" + merchantB.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an owner can read their own merchant profile")
    void ownerCanReadOwnMerchant() throws Exception {
        mockMvc.perform(get("/api/merchants/" + merchantA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(MERCHANT_A_NAME));
    }

    // ---- GET /api/branches/merchant/{merchantId} ----

    @Test
    @DisplayName("an owner cannot list another merchant's branches")
    void ownerCannotListForeignBranches() throws Exception {
        mockMvc.perform(get("/api/branches/merchant/" + merchantB.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER)))
                .andExpect(status().isForbidden());
    }

    // ---- GET /api/tables/all ----

    @Test
    @DisplayName("the table list is pinned to the caller's tenant even when it asks for another")
    void tableListIsPinnedToCallerTenant() throws Exception {
        // The merchantId query parameter is attacker-controlled. Only SUPER_ADMIN
        // may steer it; everyone else is pinned to their own tenant regardless.
        mockMvc.perform(get("/api/tables/all")
                        .param("merchantId", merchantB.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].merchantId").value(merchantA.getId().toString()));
    }

    @Test
    @DisplayName("the table list is not anonymously readable")
    void tableListRequiresAuthentication() throws Exception {
        // "/api/tables/*" is a public GET rule and it also matches "/all"; an
        // explicit authenticated rule must sit above it.
        mockMvc.perform(get("/api/tables/all"))
                .andExpect(status().is4xxClientError());
    }

    // ---- public menu resolution ----

    @Test
    @DisplayName("both tenants can have a branch called Main and each resolves to its own")
    void publicMenuResolvesPerTenant() throws Exception {
        mockMvc.perform(get("/api/v1/public/menu/sunrise/main/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantA.getId().toString()))
                .andExpect(jsonPath("$.branchId").value(branchA.getId()));

        mockMvc.perform(get("/api/v1/public/menu/blue-nile/main/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantB.getId().toString()))
                .andExpect(jsonPath("$.branchId").value(branchB.getId()));
    }

    @Test
    @DisplayName("an unknown merchant slug is 404, never a fallback to some default tenant")
    void unknownSlugIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/public/menu/no-such-tenant/main/1"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 3: Run the test**

```bash
cd backend && ./gradlew :merchant-service:test --tests 'com.qrserve.merchant.TenantIsolationIT'
```

Expected: **the seeding fails** — `branchB` violates the global unique constraint on `branches.slug`, because both tenants named their branch "Main". That failure is defect 3, reproduced. Every other assertion should pass once seeding does.

If any *other* assertion also fails, stop and report it: that is a live cross-tenant hole and it is more urgent than the rest of this plan.

- [ ] **Step 4: Commit the failing gate**

Commit it red, with the reason in the message, so the next task's fix has something to turn green.

```bash
git add backend/merchant-service/src/test/
git commit -m "test(tenancy): add two-merchant isolation gate

Fails at seeding: branches.slug is globally unique, so the second tenant
to name a branch 'Main' collides with the first. Task 5 fixes the schema."
```

---

### Task 5: Branch slug unique per merchant, and actually taken from the request

**Two defects, one commit:**

1. `BranchEntity.slug` is `@Column(nullable = false, unique = true)` — **globally** unique. The second tenant to name a branch "Main" collides with the first. In a shared deployment that is not an edge case; "Main" is the single most likely branch name on the platform.
2. `CreateBranchRequest` already carries a `@NotBlank slug` field and `BranchService.createBranch` **ignores it**, deriving the slug from the name instead. A caller supplying a slug is silently overridden.

**Files:**
- Modify: `backend/merchant-service/src/main/java/com/qrserve/merchant/entity/BranchEntity.java`
- Modify: `backend/merchant-service/src/main/java/com/qrserve/merchant/service/BranchService.java`
- Modify: `backend/merchant-service/src/test/java/com/qrserve/merchant/TenantIsolationIT.java` (add branch-slug cases)
- Create: `backend/merchant-service/src/main/resources/db/manual/001-branch-slug-per-merchant.sql`

**Interfaces:**
- Consumes: `Slugs.toPathSlug(String)` from Task 1.
- Produces: `BranchService.createBranch(CreateBranchRequest)` — unchanged signature, now honours `request.getSlug()` and normalises it; throws `IllegalArgumentException` (400) on an unusable slug and `BusinessException` (400) on a duplicate within the merchant.

- [ ] **Step 1: Add the failing tests to the isolation gate**

Append these to `TenantIsolationIT` (they need the imports `com.qrserve.merchant.dto.CreateBranchRequest`, `com.fasterxml.jackson.databind.ObjectMapper` — note merchant-service is on Jackson 2 for `spring-boot-starter-webmvc`; if the import fails, use `tools.jackson.databind.ObjectMapper`, and `org.springframework.http.MediaType`, plus `post` from `MockMvcRequestBuilders`):

```java
    @Autowired
    ObjectMapper objectMapper;

    private String branchJson(UUID merchantId, String name, String slug) throws Exception {
        CreateBranchRequest request = new CreateBranchRequest();
        request.setMerchantId(merchantId);
        request.setName(name);
        request.setSlug(slug);
        request.setPhone("+251900000000");
        request.setAddress("Bole");
        return objectMapper.writeValueAsString(request);
    }

    @Test
    @DisplayName("two tenants may each have a branch slug 'main'")
    void branchSlugIsUniquePerMerchantNotGlobally() throws Exception {
        // Seeding already created "main" for both tenants. This asserts a THIRD
        // tenant-scoped write also succeeds rather than colliding.
        mockMvc.perform(post("/api/branches")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson(merchantA.getId(), "Second Hall", "second")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/branches")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantB.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson(merchantB.getId(), "Second Hall", "second")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a duplicate branch slug within one merchant is a 400, not a 500")
    void duplicateBranchSlugWithinMerchantIsRejected() throws Exception {
        // A raw constraint violation surfaces as 500 "An unexpected server error
        // occurred", which tells the owner nothing about what to change.
        mockMvc.perform(post("/api/branches")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson(merchantA.getId(), "Main Again", "main")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the slug supplied in the request is honoured, not silently overridden by the name")
    void suppliedBranchSlugIsHonoured() throws Exception {
        mockMvc.perform(post("/api/branches")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson(merchantA.getId(), "Bole Road Terrace", "terrace")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("terrace"));
    }

    @Test
    @DisplayName("a branch slug is normalised, and an unusable one is a 400")
    void branchSlugIsNormalised() throws Exception {
        mockMvc.perform(post("/api/branches")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson(merchantA.getId(), "Upper Deck", "  Upper   Deck!  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("upper-deck"));

        mockMvc.perform(post("/api/branches")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson(merchantA.getId(), "Bad", "!!!")))
                .andExpect(status().isBadRequest());
    }
```

- [ ] **Step 2: Run to verify the new tests fail**

```bash
cd backend && ./gradlew :merchant-service:test --tests 'com.qrserve.merchant.TenantIsolationIT'
```

Expected: seeding still fails on the global unique constraint, so every test errors.

- [ ] **Step 3: Change the schema on the entity**

In `backend/merchant-service/src/main/java/com/qrserve/merchant/entity/BranchEntity.java`, replace the `@Table` annotation and the `slug` column, and remove the slug derivation from `@PrePersist`:

```java
@Entity
@Table(
        name = "branches",
        // Unique PER MERCHANT, not globally. A globally unique branch slug means
        // the second tenant to name a branch "Main" collides with the first — and
        // on a restaurant platform "Main" is the most likely branch name there is.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_branches_merchant_slug",
                columnNames = {"merchant_id", "slug"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchEntity {
```

```java
    @Column(nullable = false)
    private String slug;
```

```java
    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        // Slug derivation deliberately removed. It lived here, in MerchantEntity and
        // in MerchantService as three copies of the same broken expression. The slug
        // is now normalised and validated once, in the service, via Slugs — an entity
        // callback is the wrong place to reject caller input, because it cannot
        // produce a useful 400.
    }
```

- [ ] **Step 4: Honour and validate the supplied slug in `BranchService`**

Replace `createBranch` in `backend/merchant-service/src/main/java/com/qrserve/merchant/service/BranchService.java`, and add the imports `com.qrserve.shared.common.Slugs`, `com.qrserve.shared.exceptions.BusinessException`:

```java
    /**
     * Creates a branch under the caller's merchant.
     *
     * <p>The slug is taken from the request. It previously was not: the DTO has
     * carried a {@code @NotBlank slug} field all along and this method derived one
     * from the name instead, so a caller-supplied slug was silently discarded.
     *
     * <p>Branch slugs are path segments, not hostnames, so {@link Slugs#toPathSlug}
     * applies — a branch may legitimately be called "2".
     */
    @Transactional
    public BranchEntity createBranch(CreateBranchRequest request) {
        String slug = Slugs.toPathSlug(request.getSlug());

        // Checked rather than left to the database: a raw constraint violation
        // surfaces through the catch-all handler as 500 "An unexpected server
        // error occurred", which tells the owner nothing about what to change.
        if (branchRepository.findByMerchantIdAndSlug(request.getMerchantId(), slug).isPresent()) {
            throw new BusinessException(
                    "A branch with the slug '" + slug + "' already exists for this merchant");
        }

        BranchEntity branch = BranchEntity.builder()
                .merchantId(request.getMerchantId())
                .name(request.getName())
                .slug(slug)
                .phone(request.getPhone())
                .address(request.getAddress() != null ? request.getAddress() : "Main Address")
                .build();
        return branchRepository.save(branch);
    }
```

- [ ] **Step 5: Write the manual migration**

`spring.flyway.enabled` is `false` and `ddl-auto` is `update`, which **adds** constraints but never drops the existing global unique index. An already-deployed database therefore keeps the old constraint until this runs. (Introducing Flyway baselines is tracked separately in `docs/superpowers/plans/2026-08-18-codebase-review-remediation.md`, item 7.5.)

Create `backend/merchant-service/src/main/resources/db/manual/001-branch-slug-per-merchant.sql`:

```sql
-- Branch slug: globally unique -> unique per merchant.
--
-- Run against qrserve_merchant BEFORE deploying the code change. Loosening a
-- uniqueness constraint cannot be violated by existing rows, so this is safe to
-- run on a populated database and needs no backfill.
--
-- The old constraint name is generated by Hibernate and differs per database.
-- Find it first:
--   SELECT conname FROM pg_constraint
--   WHERE conrelid = 'branches'::regclass AND contype = 'u';

ALTER TABLE branches DROP CONSTRAINT IF EXISTS uk_branches_slug;
-- Hibernate's generated name, if the above did not match:
-- ALTER TABLE branches DROP CONSTRAINT <conname_from_the_query_above>;

ALTER TABLE branches
    ADD CONSTRAINT uk_branches_merchant_slug UNIQUE (merchant_id, slug);
```

- [ ] **Step 6: Run the full isolation gate**

```bash
cd backend && ./gradlew :merchant-service:test --tests 'com.qrserve.merchant.TenantIsolationIT'
```

Expected: `BUILD SUCCESSFUL`, every test in the class green. Seeding now succeeds because both tenants can hold `main`.

- [ ] **Step 7: Commit**

```bash
git add backend/merchant-service/src/main/java/com/qrserve/merchant/entity/BranchEntity.java \
        backend/merchant-service/src/main/java/com/qrserve/merchant/service/BranchService.java \
        backend/merchant-service/src/main/resources/db/manual/001-branch-slug-per-merchant.sql \
        backend/merchant-service/src/test/java/com/qrserve/merchant/TenantIsolationIT.java
git commit -m "fix(tenancy): scope branch slug uniqueness to the merchant, honour the supplied slug"
```

---

### Task 6: Merchant slug is owner-supplied, validated, and permanent

**Why:** the slug becomes a hostname, so it cannot be a by-product of the display name. `MerchantService:22` derives it with the broken expression; nothing rejects `Admin` (which would claim `admin.qrserve.safaricom.et`), nothing handles a collision except a raw constraint violation, and `updateMerchant` cannot change it at all today — silently, by omission rather than by rule.

The Amharic case resolves here: `ከፈ አበባ` yields no usable slug, so the owner supplies a Latin one and the display name is stored separately. Transliteration is deliberately not implemented.

**Files:**
- Modify: `backend/merchant-service/src/main/java/com/qrserve/merchant/dto/CreateMerchantRequest.java`
- Modify: `backend/merchant-service/src/main/java/com/qrserve/merchant/service/MerchantService.java`
- Modify: `backend/merchant-service/src/main/java/com/qrserve/merchant/entity/MerchantEntity.java`
- Modify: `backend/merchant-service/src/test/java/com/qrserve/merchant/TenantIsolationIT.java`

**Interfaces:**
- Consumes: `Slugs.toDnsLabel(String)`, `Slugs.isReserved(String)` from Task 1.
- Produces:
  - `CreateMerchantRequest.getSlug() / setSlug(String)` — new field, `@NotBlank` on create
  - `MerchantService.createMerchant(CreateMerchantRequest) -> MerchantEntity` — validates and de-duplicates the slug
  - `MerchantService.updateMerchant(UUID, CreateMerchantRequest) -> MerchantEntity` — throws `BusinessException` if the request's slug differs from the stored one
  - `MerchantService.getMerchantBySlug(String) -> MerchantEntity` — unchanged, consumed by Task 9

- [ ] **Step 1: Add the failing tests**

Append to `TenantIsolationIT` (needs `com.qrserve.merchant.dto.CreateMerchantRequest` and `put` from `MockMvcRequestBuilders`):

```java
    private String merchantJson(String name, String slug) throws Exception {
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setName(name);
        request.setSlug(slug);
        request.setPhone("+251900000000");
        request.setCity("Addis Ababa");
        request.setAddress("Bole");
        request.setCategory("CAFE");
        return objectMapper.writeValueAsString(request);
    }

    private String superAdminToken() {
        return tokenFor(null, UserRole.SUPER_ADMIN);
    }

    @Test
    @DisplayName("a merchant is created with the slug the owner supplied")
    void merchantSlugIsOwnerSupplied() throws Exception {
        mockMvc.perform(post("/api/merchants")
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson("Kaffa Roasters", "kaffa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("kaffa"))
                .andExpect(jsonPath("$.name").value("Kaffa Roasters"));
    }

    @Test
    @DisplayName("a reserved slug is rejected so no tenant can claim admin.")
    void reservedMerchantSlugIsRejected() throws Exception {
        mockMvc.perform(post("/api/merchants")
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson("Admin Cafe", "admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a colliding slug gets a deterministic suffix rather than a constraint violation")
    void collidingMerchantSlugGetsSuffix() throws Exception {
        // "sunrise" is taken by merchantA in seed().
        mockMvc.perform(post("/api/merchants")
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson("Sunrise Bakery", "sunrise")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("sunrise-2"));
    }

    @Test
    @DisplayName("an unusable slug is a 400 that names what to fix")
    void unusableMerchantSlugIsRejected() throws Exception {
        mockMvc.perform(post("/api/merchants")
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson("ከፈ አበባ", "ከፈ አበባ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Latin")));
    }

    @Test
    @DisplayName("the slug is permanent: an update that changes it is rejected")
    void merchantSlugCannotBeRenamed() throws Exception {
        // Renames are blocked until the alias table exists. Without this guard the
        // rename silently does nothing, which is worse than a clear refusal.
        mockMvc.perform(put("/api/merchants/" + merchantA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson(MERCHANT_A_NAME, "sunrise-rebrand")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("permanent")));
    }

    @Test
    @DisplayName("an update that keeps the slug succeeds and can still change the display name")
    void merchantUpdateKeepingSlugSucceeds() throws Exception {
        mockMvc.perform(put("/api/merchants/" + merchantA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson("ከፈ አበባ", "sunrise")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ከፈ አበባ"))
                .andExpect(jsonPath("$.slug").value("sunrise"));
    }
```

- [ ] **Step 2: Run to verify the new tests fail**

```bash
cd backend && ./gradlew :merchant-service:test --tests 'com.qrserve.merchant.TenantIsolationIT'
```

Expected: compilation failure — `CreateMerchantRequest` has no `setSlug`.

- [ ] **Step 3: Add the slug field to the DTO**

In `backend/merchant-service/src/main/java/com/qrserve/merchant/dto/CreateMerchantRequest.java`:

```java
    /**
     * The tenant's permanent public hostname label, e.g. "sunrise" for
     * sunrise.qrserve.safaricom.et.
     *
     * <p>Required, and not derived from the name: a display name may be in any
     * script, while this must be a valid DNS label. A UI may suggest a value when
     * the name is already Latin, but the API demands it explicitly.
     */
    @NotBlank
    private String slug;
```

- [ ] **Step 4: Rewrite `MerchantService`**

Replace `createMerchant` and `updateMerchant` in `backend/merchant-service/src/main/java/com/qrserve/merchant/service/MerchantService.java`, adding imports `com.qrserve.shared.common.Slugs` and `com.qrserve.shared.exceptions.BusinessException`:

```java
    /**
     * Highest suffix tried before giving up. Ten near-identical names is a strong
     * signal the owner should pick a distinctive slug rather than accept
     * "sunrise-11" as their public address.
     */
    private static final int MAX_SLUG_SUFFIX = 10;

    @Transactional
    public MerchantEntity createMerchant(CreateMerchantRequest request) {
        // Throws IllegalArgumentException -> 400 with a message naming the problem.
        String requested = Slugs.toDnsLabel(request.getSlug());
        String slug = firstAvailableSlug(requested);

        MerchantEntity merchant = MerchantEntity.builder()
                .name(request.getName())
                .slug(slug)
                .phone(request.getPhone())
                .city(request.getCity())
                .address(request.getAddress())
                .category(request.getCategory())
                .build();

        return merchantRepository.save(merchant);
    }

    /**
     * {@code sunrise}, then {@code sunrise-2}, {@code sunrise-3}, ...
     *
     * <p>A deterministic suffix rather than a raw constraint violation: the slug is
     * globally unique because it is a hostname, and two unrelated businesses
     * choosing the same name is ordinary, not exceptional.
     */
    private String firstAvailableSlug(String requested) {
        if (merchantRepository.findBySlug(requested).isEmpty()) {
            return requested;
        }
        for (int suffix = 2; suffix <= MAX_SLUG_SUFFIX; suffix++) {
            String candidate = requested + "-" + suffix;
            // Re-validate: the suffix can push a 39-character slug past the cap.
            if (candidate.length() <= Slugs.DNS_LABEL_MAX_LENGTH
                    && merchantRepository.findBySlug(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BusinessException(
                "The slug '" + requested + "' and its variants are all taken. Please choose a different one.");
    }

    @Transactional
    public MerchantEntity updateMerchant(UUID id, CreateMerchantRequest request) {
        MerchantEntity merchant = getMerchant(id);

        // The slug is this tenant's hostname and is printed onto physical QR
        // stands. Changing it would break every printed code and every bookmarked
        // staff URL, and the alias table that would let old hostnames keep
        // resolving is deliberately not built yet. Refuse clearly rather than
        // ignore the field, which is what happened before: the caller's new slug
        // was silently dropped and the response looked like a success.
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String requested = Slugs.toDnsLabel(request.getSlug());
            if (!requested.equals(merchant.getSlug())) {
                throw new BusinessException(
                        "slug is permanent and cannot be changed (current: '" + merchant.getSlug() + "')");
            }
        }

        merchant.setName(request.getName());
        merchant.setPhone(request.getPhone());
        merchant.setCity(request.getCity());
        merchant.setAddress(request.getAddress());
        merchant.setCategory(request.getCategory());
        return merchantRepository.save(merchant);
    }
```

- [ ] **Step 5: Remove the derivation from `MerchantEntity`**

In `backend/merchant-service/src/main/java/com/qrserve/merchant/entity/MerchantEntity.java`:

```java
    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        // Slug derivation deliberately removed — see BranchEntity for the same
        // change. A callback cannot reject bad input with a useful 400, and the
        // derived value here was the tenant's public hostname.
    }
```

- [ ] **Step 6: Run the gate**

```bash
cd backend && ./gradlew :merchant-service:test --tests 'com.qrserve.merchant.TenantIsolationIT'
```

Expected: `BUILD SUCCESSFUL`, every test in the class green.

- [ ] **Step 7: Confirm the broken expression is gone from the codebase**

```bash
cd backend && grep -rn 'replaceAll("\[\^a-z0-9\]"' --include=*.java . | grep -v /build/ | grep -v /bin/
```

Expected: **no output**. All three duplicated copies are now `Slugs`.

- [ ] **Step 8: Commit**

```bash
git add backend/merchant-service/src/main/java/com/qrserve/merchant/dto/CreateMerchantRequest.java \
        backend/merchant-service/src/main/java/com/qrserve/merchant/service/MerchantService.java \
        backend/merchant-service/src/main/java/com/qrserve/merchant/entity/MerchantEntity.java \
        backend/merchant-service/src/test/java/com/qrserve/merchant/TenantIsolationIT.java
git commit -m "feat(tenancy): owner-supplied, validated, permanent merchant slugs"
```

---

### Task 7: `TableService` emits a QR URL that actually resolves

**Why:** `TableService:48` builds `https://qrserve.com/menu/{merchantSlug}/{branchId}/{tableId}`. The customer route is `/menu/:merchantSlug/:branchSlug/:tableNumber` and `PublicMenuResolutionService` looks the branch up **by slug** and the table **by table number**. A scanned code therefore yields `/menu/sunrise/3/5`, resolution looks for a branch whose slug is `"3"`, and 404s. No printed stand would have worked. It also emits no signature at all, which is why the signature check has been effectively dead code.

**Files:**
- Modify: `backend/merchant-service/src/main/java/com/qrserve/merchant/service/TableService.java`
- Create: `backend/merchant-service/src/test/java/com/qrserve/merchant/service/TableQrUrlTest.java`

**Interfaces:**
- Consumes: `PublicMenuUrl.menuUrl(String, String, String, String)` (Task 2), `QrSignatureService.generateSignature(UUID, Long, Long)` (Task 3).
- Produces: `TableService.createTable(CreateTableRequest) -> CreateTableResponse` with a `qrUrl` of the form `https://{merchantSlug}.{baseDomain}/menu/{branchSlug}/{tableNumber}?signature={sig}`.

- [ ] **Step 1: Write the failing test**

Create `backend/merchant-service/src/test/java/com/qrserve/merchant/service/TableQrUrlTest.java`:

```java
package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.CreateTableRequest;
import com.qrserve.merchant.dto.CreateTableResponse;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.repository.BranchRepository;
import com.qrserve.merchant.repository.MerchantRepository;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.shared.common.PublicMenuUrl;
import com.qrserve.shared.common.QrSignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The QR URL is the product. If it is wrong, a restaurant prints table stands
 * that do not work and discovers it from a confused customer — so this is a unit
 * test on the exact string, not a smoke test.
 */
class TableQrUrlTest {

    private static final UUID MERCHANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private TableRepository tableRepository;
    private TableService tableService;
    private QrSignatureService signatures;

    @BeforeEach
    void setUp() {
        tableRepository = mock(TableRepository.class);
        BranchRepository branchRepository = mock(BranchRepository.class);
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        signatures = new QrSignatureService("master-secret-value", "");
        PublicMenuUrl urls = new PublicMenuUrl("qrserve.safaricom.et", "https");

        when(branchRepository.findById(7L)).thenReturn(Optional.of(BranchEntity.builder()
                .id(7L).merchantId(MERCHANT_ID).name("Main").slug("main")
                .phone("+251900000000").address("Bole").build()));
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(MerchantEntity.builder()
                .id(MERCHANT_ID).name("Sunrise Coffee").slug("sunrise")
                .phone("+251900000000").city("Addis Ababa").address("Bole").category("CAFE").build()));
        // save() assigns the id the database would have assigned.
        when(tableRepository.save(any(TableEntity.class))).thenAnswer(inv -> {
            TableEntity t = inv.getArgument(0);
            t.setId(42L);
            return t;
        });

        tableService = new TableService(tableRepository, branchRepository, merchantRepository, urls, signatures);
    }

    private CreateTableResponse createTable(String tableNumber) {
        CreateTableRequest request = new CreateTableRequest();
        request.setBranchId(7L);
        request.setTableNumber(tableNumber);
        request.setCapacity(4);
        return tableService.createTable(request);
    }

    @Test
    @DisplayName("the merchant is the host label and the branch SLUG is the path")
    void urlUsesHostAndBranchSlug() {
        String qrUrl = createTable("12").getQrUrl();
        URI uri = URI.create(qrUrl);

        // The old output was https://qrserve.com/menu/sunrise/7/42 — hardcoded host,
        // branch ID where the resolver expects a slug, table ID where it expects a
        // table number. Three mismatches, one 404.
        assertEquals("sunrise.qrserve.safaricom.et", uri.getHost());
        assertEquals("/menu/main/12", uri.getPath());
        assertTrue(qrUrl.startsWith("https://"), "the scheme comes from configuration");
    }

    @Test
    @DisplayName("the table NUMBER is in the path, not the table id")
    void urlUsesTableNumberNotId() {
        // save() assigns id 42; the URL must carry "12".
        assertTrue(createTable("12").getQrUrl().endsWith("/menu/main/12")
                        || createTable("12").getQrUrl().contains("/menu/main/12?"),
                "the resolver looks the table up by table_number");
    }

    @Test
    @DisplayName("the URL carries a signature that validates for this table")
    void urlIsSigned() {
        String qrUrl = createTable("12").getQrUrl();

        int idx = qrUrl.indexOf("?signature=");
        assertTrue(idx > 0, "a printed code must be signed, otherwise the signature check is dead code");

        String signature = qrUrl.substring(idx + "?signature=".length());
        assertTrue(signatures.validateSignature(signature, MERCHANT_ID, 7L, 42L),
                "the signature must cover this merchant, branch and table");
    }

    @Test
    @DisplayName("the signature does not validate for a different table")
    void signatureIsTableSpecific() {
        String qrUrl = createTable("12").getQrUrl();
        String signature = qrUrl.substring(qrUrl.indexOf("?signature=") + "?signature=".length());

        assertTrue(!signatures.validateSignature(signature, MERCHANT_ID, 7L, 99L),
                "moving a signed sticker to another table must not work");
    }

    @Test
    @DisplayName("a table number with a space is encoded rather than breaking the URL")
    void encodesTableNumber() {
        assertTrue(createTable("A 1").getQrUrl().contains("/menu/main/A%201"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./gradlew :merchant-service:test --tests 'com.qrserve.merchant.service.TableQrUrlTest'
```

Expected: compilation failure — `TableService` has a three-argument constructor.

- [ ] **Step 3: Rewrite the URL construction in `TableService`**

In `backend/merchant-service/src/main/java/com/qrserve/merchant/service/TableService.java`, add the two dependencies (`@RequiredArgsConstructor` generates the constructor in field order, so declare them **after** the three repositories to match the test), the imports `com.qrserve.shared.common.PublicMenuUrl` and `com.qrserve.shared.common.QrSignatureService`, and replace the URL line:

```java
    private final TableRepository tableRepository;
    private final BranchRepository branchRepository;
    private final MerchantRepository merchantRepository;
    private final PublicMenuUrl publicMenuUrl;
    private final QrSignatureService qrSignatureService;
```

```java
        TableEntity saved = tableRepository.save(table);

        // The URL is built by PublicMenuUrl and nowhere else. The previous format,
        //   https://qrserve.com/menu/{merchantSlug}/{branchId}/{tableId}
        // was wrong three ways at once: the host was hardcoded, the branch was
        // identified by id where PublicMenuResolutionService resolves it by slug,
        // and the table by id where the resolver uses table_number. Every code ever
        // generated resolved to a 404.
        //
        // The signature is emitted here for the first time. It was validated in two
        // places but generated in none, which made the tamper check dead code and
        // left the public service-call endpoint reachable by anyone who could guess
        // a table id.
        String signature = qrSignatureService.generateSignature(
                merchant.getId(), branch.getId(), saved.getId());
        String qrUrl = publicMenuUrl.menuUrl(
                merchant.getSlug(), branch.getSlug(), saved.getTableNumber(), signature);
```

- [ ] **Step 4: Run to verify it passes**

```bash
cd backend && ./gradlew :merchant-service:test
```

Expected: `BUILD SUCCESSFUL` — `TableQrUrlTest` and `TenantIsolationIT` both fully green.

- [ ] **Step 5: Prove the generated URL round-trips through the resolver**

Add to `TenantIsolationIT` — this is the assertion that would have caught defect 1, and it is worth more than the unit test because it crosses the generator/resolver boundary:

```java
    @Test
    @DisplayName("a generated QR URL resolves through the public endpoint it points at")
    void generatedQrUrlResolves() throws Exception {
        CreateTableRequest req = new CreateTableRequest();
        req.setBranchId(branchA.getId());
        req.setTableNumber("9");
        req.setCapacity(2);

        String body = mockMvc.perform(post("/api/tables")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String qrUrl = objectMapper.readTree(body).get("qrUrl").asText();
        java.net.URI uri = java.net.URI.create(qrUrl);

        // Feed the generated URL's own path and signature straight back into the
        // resolver. If the generator and the resolver ever drift again, this fails.
        String signature = uri.getQuery().substring("signature=".length());

        mockMvc.perform(get("/api/v1/public/menu/" + merchantA.getSlug()
                        + uri.getPath().substring("/menu".length()))
                        .param("signature", signature))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantA.getId().toString()));
    }
```

Needs the import `com.qrserve.merchant.dto.CreateTableRequest`.

- [ ] **Step 6: Run and commit**

```bash
cd backend && ./gradlew :merchant-service:test
```

```bash
git add backend/merchant-service/src/main/java/com/qrserve/merchant/service/TableService.java \
        backend/merchant-service/src/test/
git commit -m "fix(qr): emit a signed QR URL that resolves - host, branch slug, table number"
```

---

### Task 8: `qr-service` uses the same builder

**Why:** `QrGeneratorService` carries the *same* wrong format in two methods (`getQrForTable:52`, `exportPng:73`), each commented "Consistent URL format with TableService". They were consistent with each other and both wrong. It also cannot build the correct URL from what it currently fetches — it has the branch **id** but needs the branch **slug** — so a branch lookup has to be added.

**Files:**
- Modify: `backend/qr-service/src/main/java/com/qrserve/qr/service/QrGeneratorService.java`
- Create: `backend/qr-service/src/test/java/com/qrserve/qr/service/QrTargetUrlTest.java`

**Interfaces:**
- Consumes: `PublicMenuUrl` (Task 2), `QrSignatureService` (Task 3).
- Produces: `QrGeneratorService.targetUrlFor(TableInfo, MerchantInfo, BranchInfo, PublicMenuUrl, QrSignatureService) -> String` — extracted as a **package-private static** method so the URL contract is testable without HTTP; `TableInfo`, `MerchantInfo` and the new `BranchInfo` are promoted from `private static class` to `static class`.

- [ ] **Step 1: Write the failing test**

Create `backend/qr-service/src/test/java/com/qrserve/qr/service/QrTargetUrlTest.java`:

```java
package com.qrserve.qr.service;

import com.qrserve.shared.common.PublicMenuUrl;
import com.qrserve.shared.common.QrSignatureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * qr-service and merchant-service must emit byte-identical URLs for the same
 * table. They previously carried two copies of the format, each commented as
 * consistent with the other, and both wrong in the same way.
 */
class QrTargetUrlTest {

    private static final UUID MERCHANT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final PublicMenuUrl urls = new PublicMenuUrl("qrserve.safaricom.et", "https");
    private final QrSignatureService signatures = new QrSignatureService("master-secret-value", "");

    @Test
    @DisplayName("builds the same signed URL merchant-service would build")
    void matchesMerchantServiceFormat() {
        String url = QrGeneratorService.targetUrlFor(
                new QrGeneratorService.TableInfo(42L, 7L, MERCHANT_ID, "12"),
                new QrGeneratorService.MerchantInfo(MERCHANT_ID, "sunrise"),
                new QrGeneratorService.BranchInfo(7L, "main"),
                urls, signatures);

        String expected = urls.menuUrl("sunrise", "main", "12",
                signatures.generateSignature(MERCHANT_ID, 7L, 42L));
        assertEquals(expected, url);
    }

    @Test
    @DisplayName("the URL is signed and validates for this table")
    void urlIsSigned() {
        String url = QrGeneratorService.targetUrlFor(
                new QrGeneratorService.TableInfo(42L, 7L, MERCHANT_ID, "12"),
                new QrGeneratorService.MerchantInfo(MERCHANT_ID, "sunrise"),
                new QrGeneratorService.BranchInfo(7L, "main"),
                urls, signatures);

        String signature = url.substring(url.indexOf("?signature=") + "?signature=".length());
        assertTrue(signatures.validateSignature(signature, MERCHANT_ID, 7L, 42L));
    }

    @Test
    @DisplayName("no hardcoded qrserve.com survives")
    void hostComesFromConfiguration() {
        String url = QrGeneratorService.targetUrlFor(
                new QrGeneratorService.TableInfo(1L, 1L, MERCHANT_ID, "1"),
                new QrGeneratorService.MerchantInfo(MERCHANT_ID, "tenant"),
                new QrGeneratorService.BranchInfo(1L, "main"),
                new PublicMenuUrl("example.test", "http"), signatures);

        assertTrue(url.startsWith("http://tenant.example.test/menu/main/1"), url);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./gradlew :qr-service:test --tests 'com.qrserve.qr.service.QrTargetUrlTest'
```

Expected: compilation failure — `targetUrlFor` and `BranchInfo` do not exist and the info classes are private.

- [ ] **Step 3: Modify `QrGeneratorService`**

In `backend/qr-service/src/main/java/com/qrserve/qr/service/QrGeneratorService.java`:

Add the two injected dependencies and the imports:

```java
import com.qrserve.shared.common.PublicMenuUrl;
import com.qrserve.shared.common.QrSignatureService;
```

```java
    private final RestTemplate restTemplate;
    private final PublicMenuUrl publicMenuUrl;
    private final QrSignatureService qrSignatureService;
```

Replace the body of `getQrForTable` and `exportPng`:

```java
    public QrMetadataResponse getQrForTable(Long tableId) {
        TableInfo table = fetchTable(tableId);
        MerchantInfo merchant = fetchMerchant(table.getMerchantId());
        BranchInfo branch = fetchBranch(table.getBranchId());

        String targetUrl = targetUrlFor(table, merchant, branch, publicMenuUrl, qrSignatureService);
        String base64Png = generateQrBase64(targetUrl);

        return QrMetadataResponse.builder()
                .tableId(table.getId())
                .qrUrl(targetUrl)
                .format("PNG")
                .mimeType("image/png")
                .base64Content("data:image/png;base64," + base64Png)
                .build();
    }

    public byte[] exportPng(QrExportRequest request) {
        TableInfo table = fetchTable(request.getTableId());
        MerchantInfo merchant = fetchMerchant(table.getMerchantId());
        BranchInfo branch = fetchBranch(table.getBranchId());

        return generateQrPng(targetUrlFor(table, merchant, branch, publicMenuUrl, qrSignatureService));
    }

    /**
     * The single URL contract, shared with merchant-service through
     * {@link PublicMenuUrl}. Static and package-private so the exact output can be
     * asserted without standing up HTTP: this service and merchant-service must
     * emit byte-identical URLs for the same table, and the two previously drifted
     * while both claiming to match the other.
     */
    static String targetUrlFor(TableInfo table, MerchantInfo merchant, BranchInfo branch,
                               PublicMenuUrl publicMenuUrl, QrSignatureService signatures) {
        String signature = signatures.generateSignature(
                merchant.getId(), branch.getId(), table.getId());
        return publicMenuUrl.menuUrl(
                merchant.getSlug(), branch.getSlug(), table.getTableNumber(), signature);
    }
```

Add the branch fetch alongside the existing ones:

```java
    private BranchInfo fetchBranch(Long branchId) {
        try {
            String url = merchantServiceUrl + "/api/branches/" + branchId;
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new ResourceNotFoundException("Branch not found ID: " + branchId);
            }
            return new BranchInfo(
                    ((Number) body.get("id")).longValue(),
                    (String) body.get("slug")
            );
        } catch (Exception e) {
            log.error("Failed to fetch branch {} from merchant-service", branchId, e);
            throw new ResourceNotFoundException("Branch not found ID: " + branchId);
        }
    }
```

Widen the info holders to package-private, add `tableNumber` to `TableInfo`, and add `BranchInfo`:

```java
    /** Package-private so {@link #targetUrlFor} can be unit-tested. */
    static class TableInfo {
        private final Long id;
        private final Long branchId;
        private final UUID merchantId;
        private final String tableNumber;

        TableInfo(Long id, Long branchId, UUID merchantId, String tableNumber) {
            this.id = id;
            this.branchId = branchId;
            this.merchantId = merchantId;
            this.tableNumber = tableNumber;
        }

        public Long getId() { return id; }
        public Long getBranchId() { return branchId; }
        public UUID getMerchantId() { return merchantId; }
        public String getTableNumber() { return tableNumber; }
    }

    static class MerchantInfo {
        private final UUID id;
        private final String slug;

        MerchantInfo(UUID id, String slug) {
            this.id = id;
            this.slug = slug;
        }

        public UUID getId() { return id; }
        public String getSlug() { return slug; }
    }

    /**
     * The branch SLUG, which is what the public route needs. This lookup did not
     * exist before because the old URL used the branch id — the id was available
     * and the slug was not, which is very likely how the wrong format was written
     * in the first place.
     */
    static class BranchInfo {
        private final Long id;
        private final String slug;

        BranchInfo(Long id, String slug) {
            this.id = id;
            this.slug = slug;
        }

        public Long getId() { return id; }
        public String getSlug() { return slug; }
    }
```

Finally, update `fetchTable` to read the table number:

```java
            return new TableInfo(
                    ((Number) body.get("id")).longValue(),
                    ((Number) body.get("branchId")).longValue(),
                    UUID.fromString((String) body.get("merchantId")),
                    (String) body.get("tableNumber")
            );
```

- [ ] **Step 4: Add the `GET /api/branches/{id}` endpoint `fetchBranch` depends on**

`BranchController` exposes only `POST /api/branches` and `GET /api/branches/merchant/{merchantId}`. `BranchService.getBranch(Long)` already exists but has no route. Add to `backend/merchant-service/src/main/java/com/qrserve/merchant/controller/BranchController.java`:

```java
    /**
     * Consumed by qr-service, which needs the branch SLUG to build a public menu
     * URL. Tenant scope is checked against the loaded branch rather than a path
     * parameter, because the caller does not supply a merchant id here.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER','WAITER','KITCHEN','CASHIER')")
    @Operation(summary = "Get a branch by ID")
    public ResponseEntity<BranchEntity> getBranch(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        BranchEntity branch = branchService.getBranch(id);
        if (principal != null && principal.getRole() != UserRole.SUPER_ADMIN
                && !branch.getMerchantId().equals(principal.getMerchantId())) {
            throw new AccessDeniedException("Branch belongs to another merchant");
        }
        return ResponseEntity.ok(branch);
    }
```

Imports to add: `org.springframework.security.access.AccessDeniedException`, `org.springframework.security.core.annotation.AuthenticationPrincipal`, `com.qrserve.shared.security.UserPrincipal`, `com.qrserve.shared.security.UserRole`.

- [ ] **Step 5: Add the cross-tenant test for the new endpoint**

Append to `TenantIsolationIT`:

```java
    @Test
    @DisplayName("an owner cannot read another merchant's branch by id")
    void ownerCannotReadForeignBranchById() throws Exception {
        mockMvc.perform(get("/api/branches/" + branchB.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an owner can read their own branch by id")
    void ownerCanReadOwnBranchById() throws Exception {
        mockMvc.perform(get("/api/branches/" + branchA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(merchantA.getId(), UserRole.MERCHANT_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("main"));
    }
```

- [ ] **Step 6: Run and confirm no hardcoded host remains**

```bash
cd backend && ./gradlew :qr-service:test :merchant-service:test
```

```bash
cd backend && grep -rn "qrserve\.com" --include=*.java . | grep -v /build/ | grep -v /bin/
```

Expected: `BUILD SUCCESSFUL`, then **no output** from the grep.

- [ ] **Step 7: Commit**

```bash
git add backend/qr-service/src/ backend/merchant-service/src/
git commit -m "fix(qr): route qr-service through the shared URL builder and signer"
```

---

### Task 9: `GET /api/v1/public/tenants/by-slug/{slug}`

**Why:** the gateway needs to turn a host label into a `merchantId` before it knows which tenant a request belongs to, and it cannot do that with a JWT it has not yet seen. The endpoint is public, cacheable, and returns nothing beyond what the hostname already reveals — the id and the display name — so exposing it leaks nothing a scanned QR code does not.

**Files:**
- Create: `backend/shared/common/src/main/java/com/qrserve/shared/common/dto/TenantResolutionResponse.java`
- Create: `backend/shared/common/src/main/java/com/qrserve/shared/common/TenantCacheKeys.java`
- Create: `backend/merchant-service/src/main/java/com/qrserve/merchant/controller/PublicTenantController.java`
- Create: `backend/merchant-service/src/main/java/com/qrserve/merchant/service/TenantCacheInvalidator.java`
- Modify: `backend/merchant-service/src/main/java/com/qrserve/merchant/service/MerchantService.java`
- Modify: `backend/merchant-service/src/test/java/com/qrserve/merchant/TenantIsolationIT.java`

**Interfaces:**
- Consumes: `MerchantService.getMerchantBySlug(String)` (exists), `Slugs.isReserved(String)` (Task 1).
- Produces:
  - `TenantResolutionResponse` — a Lombok `@Data @Builder` DTO with `UUID merchantId`, `String slug`, `String name`
  - `TenantCacheKeys.slugKey(String slug) -> String` — the one place the Redis key format lives; consumed by Task 10
  - `TenantCacheKeys.NEGATIVE` (`"-"`), `TenantCacheKeys.HIT_TTL_SECONDS` (`600`), `TenantCacheKeys.MISS_TTL_SECONDS` (`60`)
  - `TenantCacheInvalidator.invalidate(String slug) -> void` — best-effort, never throws
  - `GET /api/v1/public/tenants/by-slug/{slug}` → 200 with that body, or 404

The route already falls under `SecurityConfig`'s `.requestMatchers("/api/v1/public/**").permitAll()` and the gateway's `/api/v1/**` → merchant-service predicate, so no routing or security change is needed.

**Why invalidation is needed at all, given renames are blocked.** The hit TTL never matters — a merchant's slug cannot change, so a cached hit cannot go stale. The *miss* TTL does: if anything probed `kaffa.qrserve.safaricom.et` in the 60 seconds before that tenant was created, the negative entry outlives the creation and the new tenant's own owner gets a 404 on their first visit to their own site. That is a bad first impression for a one-line fix, and it is the case the spec calls out.

- [ ] **Step 1: Write the failing test**

Append to `TenantIsolationIT`:

```java
    @Test
    @DisplayName("a known slug resolves to its merchant id without a token")
    void slugResolvesAnonymously() throws Exception {
        mockMvc.perform(get("/api/v1/public/tenants/by-slug/sunrise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantA.getId().toString()))
                .andExpect(jsonPath("$.slug").value("sunrise"))
                .andExpect(jsonPath("$.name").value(MERCHANT_A_NAME));
    }

    @Test
    @DisplayName("an unknown slug is 404 and never falls back to a default tenant")
    void unknownSlugResolutionIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/public/tenants/by-slug/no-such-tenant"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a reserved label is 404, so admin. can never resolve as a tenant")
    void reservedLabelDoesNotResolve() throws Exception {
        // Belt and braces: Task 6 stops a merchant being CREATED with slug "admin",
        // and this stops the label resolving even if such a row existed already.
        mockMvc.perform(get("/api/v1/public/tenants/by-slug/admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("resolution is case-insensitive, because hostnames are")
    void resolutionIsCaseInsensitive() throws Exception {
        mockMvc.perform(get("/api/v1/public/tenants/by-slug/SUNRISE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("sunrise"));
    }
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./gradlew :merchant-service:test --tests 'com.qrserve.merchant.TenantIsolationIT'
```

Expected: the four new tests fail with 404 on the route itself (no handler mapped).

- [ ] **Step 3: Create the response DTO**

Create `backend/shared/common/src/main/java/com/qrserve/shared/common/dto/TenantResolutionResponse.java`:

```java
package com.qrserve.shared.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * The answer to "which tenant is {@code sunrise.qrserve.safaricom.et}?".
 *
 * <p>Deliberately minimal. This endpoint is public and heavily cached, and it
 * discloses only what the hostname already discloses: that this tenant exists,
 * its id and its display name. Nothing about plans, staff, branches or revenue
 * belongs here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResolutionResponse {
    private UUID merchantId;
    private String slug;
    private String name;
}
```

- [ ] **Step 4: Create the controller**

Create `backend/merchant-service/src/main/java/com/qrserve/merchant/controller/PublicTenantController.java`:

```java
package com.qrserve.merchant.controller;

import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.merchant.service.MerchantService;
import com.qrserve.shared.common.Slugs;
import com.qrserve.shared.common.dto.TenantResolutionResponse;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Locale;

/**
 * Resolves a subdomain label to a tenant.
 *
 * <p>Called by the API gateway's {@code TenantResolutionGlobalFilter} on a cache
 * miss, before any JWT has been inspected — which is why it must be public. It
 * carries no tenant context of its own.
 */
@RestController
@RequestMapping("/api/v1/public/tenants")
@RequiredArgsConstructor
@Tag(name = "Public Tenant Resolution", description = "Subdomain label to merchant id")
public class PublicTenantController {

    private final MerchantService merchantService;

    @GetMapping("/by-slug/{slug}")
    @Operation(summary = "Resolve a subdomain label to a merchant id")
    public ResponseEntity<TenantResolutionResponse> bySlug(@PathVariable String slug) {
        // Hostnames are case-insensitive; slugs are stored lowercase.
        String normalized = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);

        // A reserved label must never resolve, even if a row somehow exists with
        // that slug — for instance a merchant created before the creation-time
        // check in MerchantService. Fail here too rather than trust one gate.
        if (normalized.isEmpty() || Slugs.isReserved(normalized)) {
            throw new ResourceNotFoundException("No tenant for '" + slug + "'");
        }

        MerchantEntity merchant = merchantService.getMerchantBySlug(normalized);

        return ResponseEntity.ok()
                // The gateway caches in Redis; this header helps any intermediate
                // proxy do the same. Short, because a newly created tenant should
                // start resolving quickly.
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(TenantResolutionResponse.builder()
                        .merchantId(merchant.getId())
                        .slug(merchant.getSlug())
                        .name(merchant.getName())
                        .build());
    }
}
```

- [ ] **Step 5: Put the cache key format in one place**

The gateway writes these keys and merchant-service deletes them. Two hand-written prefixes stop matching the first time either changes, and the failure mode is invisible: the cache keeps working while invalidation silently stops.

Create `backend/shared/common/src/main/java/com/qrserve/shared/common/TenantCacheKeys.java`:

```java
package com.qrserve.shared.common;

/**
 * The tenant cache contract, shared between the gateway (which writes) and
 * merchant-service (which invalidates).
 *
 * <p>Two hand-written copies of a Redis key prefix stop matching the moment
 * either changes, and the failure is invisible: the cache keeps working, the
 * invalidation silently stops. One constant removes that.
 */
public final class TenantCacheKeys {

    private static final String SLUG_PREFIX = "tenant:slug:";

    /** Sentinel for "this slug names no tenant". Not a valid UUID, so it cannot be mistaken for one. */
    public static final String NEGATIVE = "-";

    /** A resolved slug is cached for 10 minutes. Slugs are permanent, so this cannot go stale. */
    public static final long HIT_TTL_SECONDS = 600;

    /**
     * A miss is cached for 60 seconds. Much shorter than a hit, because on a public
     * wildcard domain bots enumerate subdomains and each un-cached probe would
     * otherwise cost a gateway hop plus a database round trip - a free
     * amplification primitive. Short enough that a newly created tenant starts
     * resolving almost immediately even if its slug was probed just beforehand.
     */
    public static final long MISS_TTL_SECONDS = 60;

    private TenantCacheKeys() {
    }

    public static String slugKey(String slug) {
        return SLUG_PREFIX + slug;
    }
}
```

- [ ] **Step 6: Invalidate on merchant create**

Create `backend/merchant-service/src/main/java/com/qrserve/merchant/service/TenantCacheInvalidator.java`:

```java
package com.qrserve.merchant.service;

import com.qrserve.shared.common.TenantCacheKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Drops the gateway's cached answer for one slug.
 *
 * <p>The case this exists for is narrow but real: a negative cache entry created
 * by a bot probing {@code kaffa.qrserve.safaricom.et} outlives the creation of a
 * tenant with that slug, and the new owner gets a 404 on their first visit to
 * their own site.
 *
 * <p>Best-effort by design. Redis being unreachable must not fail a merchant
 * registration - the worst consequence of a missed invalidation is up to
 * {@link TenantCacheKeys#MISS_TTL_SECONDS} seconds of 404, which is not worth
 * rolling back a registration for.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantCacheInvalidator {

    private final StringRedisTemplate redis;

    public void invalidate(String slug) {
        if (slug == null || slug.isBlank()) {
            return;
        }
        try {
            redis.delete(TenantCacheKeys.slugKey(slug));
        } catch (Exception e) {
            log.warn("Could not invalidate tenant cache for '{}': {}. It will expire within {}s.",
                    slug, e.getMessage(), TenantCacheKeys.MISS_TTL_SECONDS);
        }
    }
}
```

In `backend/merchant-service/src/main/java/com/qrserve/merchant/service/MerchantService.java`, add the dependency and the call. `@RequiredArgsConstructor` generates the constructor in field order:

```java
    private final MerchantRepository merchantRepository;
    private final TenantCacheInvalidator tenantCacheInvalidator;
```

```java
        MerchantEntity saved = merchantRepository.save(merchant);

        // A bot probing this subdomain before the tenant existed leaves a negative
        // cache entry that would otherwise 404 the new owner's first visit.
        tenantCacheInvalidator.invalidate(saved.getSlug());
        return saved;
```

`updateMerchant` needs no invalidation: the slug is the only cached field and Task 6 rejects any attempt to change it. Add that as a comment there, so the asymmetry does not read as an oversight:

```java
        // No cache invalidation here: the slug is the only field the gateway caches
        // and it is immutable (see the guard above). If renames are ever allowed,
        // this is one of the two places that has to change.
        return merchantRepository.save(merchant);
```

- [ ] **Step 7: Keep Redis out of the test**

Add to `TenantIsolationIT`, beside the existing `MerchantEventPublisher` mock:

```java
    /**
     * Redis is not running under test. The invalidator swallows the failure, but
     * without this mock every merchant creation still pays a socket timeout first.
     */
    @MockitoBean
    TenantCacheInvalidator tenantCacheInvalidator;
```

Needs the import `com.qrserve.merchant.service.TenantCacheInvalidator`.

- [ ] **Step 8: Run and commit**

```bash
cd backend && ./gradlew :merchant-service:test --tests 'com.qrserve.merchant.TenantIsolationIT'
```

Expected: `BUILD SUCCESSFUL`, every test in the class green.

```bash
git add backend/shared/common/src/main/java/com/qrserve/shared/common/dto/TenantResolutionResponse.java \
        backend/shared/common/src/main/java/com/qrserve/shared/common/TenantCacheKeys.java \
        backend/merchant-service/src/main/java/com/qrserve/merchant/controller/PublicTenantController.java \
        backend/merchant-service/src/main/java/com/qrserve/merchant/service/TenantCacheInvalidator.java \
        backend/merchant-service/src/main/java/com/qrserve/merchant/service/MerchantService.java \
        backend/merchant-service/src/test/java/com/qrserve/merchant/TenantIsolationIT.java
git commit -m "feat(tenancy): add public slug-to-merchant resolution and cache invalidation"
```

---

### Task 10: Gateway tenant resolution — strip, extract, resolve, inject

**The security core of this plan.** Downstream services will trust `X-Tenant-Id`. That trust is only justified if the gateway **unconditionally removes any inbound copy** of the header first. Without that step a caller sets `X-Tenant-Id: <victim>` and every service believes it — the difference between a trusted header and a decorative one.

**Files:**
- Create: `backend/api-gateway/src/main/java/com/qrserve/gateway/tenant/TenantHost.java`
- Create: `backend/api-gateway/src/main/java/com/qrserve/gateway/tenant/TenantSlugResolver.java`
- Create: `backend/api-gateway/src/main/java/com/qrserve/gateway/tenant/TenantResolutionGlobalFilter.java`
- Create: `backend/api-gateway/src/test/java/com/qrserve/gateway/tenant/TenantHostTest.java`
- Create: `backend/api-gateway/src/test/java/com/qrserve/gateway/tenant/TenantResolutionGlobalFilterTest.java`
- Modify: `backend/api-gateway/src/main/resources/application.yml`
- Modify: `backend/build.gradle` (gateway test dependency)

**Interfaces:**
- Consumes: `Slugs.isReserved(String)` (Task 1), `TenantResolutionResponse` (Task 9).
- Produces:
  - `TenantHost.labelFrom(String hostHeader, String baseDomain) -> String` — static, returns `null` when the host carries no tenant label. Pure; no I/O.
  - `TenantSlugResolver.resolve(String slug) -> Mono<UUID>` — empty `Mono` when the slug does not exist
  - `TenantResolutionGlobalFilter.TENANT_ID_HEADER` = `"X-Tenant-Id"`, `TENANT_SLUG_HEADER` = `"X-Tenant-Slug"`

**Why `TenantHost` is separate:** host parsing has the most edge cases (ports, IPv6 brackets, the bare apex, multi-level labels, reserved labels) and needs none of Redis, WebFlux or Spring. Keeping it a pure static function makes those cases cheap to test exhaustively.

- [ ] **Step 1: Write the failing host-parsing test**

Create `backend/api-gateway/src/test/java/com/qrserve/gateway/tenant/TenantHostTest.java`:

```java
package com.qrserve.gateway.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantHostTest {

    private static final String BASE = "qrserve.safaricom.et";

    @Test
    @DisplayName("extracts the first label under the base domain")
    void extractsLabel() {
        assertEquals("sunrise", TenantHost.labelFrom("sunrise.qrserve.safaricom.et", BASE));
    }

    @Test
    @DisplayName("ignores the port")
    void ignoresPort() {
        assertEquals("sunrise", TenantHost.labelFrom("sunrise.qrserve.safaricom.et:8081", BASE));
    }

    @Test
    @DisplayName("is case-insensitive, because hostnames are")
    void lowercases() {
        assertEquals("sunrise", TenantHost.labelFrom("SunRise.QRServe.Safaricom.ET", BASE));
    }

    @Test
    @DisplayName("the bare apex carries no tenant")
    void apexHasNoTenant() {
        assertNull(TenantHost.labelFrom("qrserve.safaricom.et", BASE));
    }

    @Test
    @DisplayName("a host outside the base domain carries no tenant")
    void foreignHostHasNoTenant() {
        // localhost, cluster-internal service names and health probes all land here.
        // They must pass through untouched rather than 404, or local development and
        // kubelet probes break.
        assertNull(TenantHost.labelFrom("localhost:8081", BASE));
        assertNull(TenantHost.labelFrom("10.0.0.5", BASE));
        assertNull(TenantHost.labelFrom("api-gateway-service", BASE));
        assertNull(TenantHost.labelFrom("evil.example.com", BASE));
    }

    @Test
    @DisplayName("a multi-level label carries no tenant")
    void multiLevelHasNoTenant() {
        // A single-label wildcard certificate does not cover *.*.domain, so this is
        // not a shape we can serve; treating it as tenant "a" would be a guess.
        assertNull(TenantHost.labelFrom("a.b.qrserve.safaricom.et", BASE));
    }

    @Test
    @DisplayName("reserved labels carry no tenant")
    void reservedHasNoTenant() {
        assertNull(TenantHost.labelFrom("admin.qrserve.safaricom.et", BASE));
        assertNull(TenantHost.labelFrom("api.qrserve.safaricom.et", BASE));
        assertNull(TenantHost.labelFrom("www.qrserve.safaricom.et", BASE));
    }

    @Test
    @DisplayName("null, blank and malformed hosts carry no tenant")
    void malformedHasNoTenant() {
        assertNull(TenantHost.labelFrom(null, BASE));
        assertNull(TenantHost.labelFrom("", BASE));
        assertNull(TenantHost.labelFrom(".qrserve.safaricom.et", BASE));
        assertNull(TenantHost.labelFrom("-bad.qrserve.safaricom.et", BASE));
        assertNull(TenantHost.labelFrom("has_underscore.qrserve.safaricom.et", BASE));
    }

    @Test
    @DisplayName("an IPv6 literal carries no tenant")
    void ipv6HasNoTenant() {
        assertNull(TenantHost.labelFrom("[::1]:8081", BASE));
    }

    @Test
    @DisplayName("a base domain with a port is handled, for local development")
    void devBaseDomainWithPort() {
        // PUBLIC_BASE_DOMAIN=localtest.me:3000 in the dev profile.
        assertEquals("sunrise", TenantHost.labelFrom("sunrise.localtest.me:3000", "localtest.me:3000"));
        assertEquals("sunrise", TenantHost.labelFrom("sunrise.localtest.me", "localtest.me:3000"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./gradlew :api-gateway:test --tests 'com.qrserve.gateway.tenant.TenantHostTest'
```

Expected: compilation failure — `cannot find symbol: class TenantHost`.

- [ ] **Step 3: Implement `TenantHost`**

Create `backend/api-gateway/src/main/java/com/qrserve/gateway/tenant/TenantHost.java`:

```java
package com.qrserve.gateway.tenant;

import com.qrserve.shared.common.Slugs;

import java.util.Locale;

/**
 * Extracts a tenant label from a {@code Host} header.
 *
 * <p>Pure and static on purpose. Host parsing is where the edge cases live —
 * ports, IPv6 literals, the bare apex, multi-level labels, cluster-internal
 * names, reserved labels — and none of them need Redis, WebFlux or a Spring
 * context to test.
 *
 * <p>Returns {@code null} rather than throwing for "this host has no tenant".
 * That is a normal condition, not an error: localhost during development,
 * cluster-internal service names, kubelet probes and the admin console all
 * legitimately carry no tenant, and they must pass through rather than 404.
 */
public final class TenantHost {

    private TenantHost() {
    }

    /**
     * @param hostHeader the raw {@code Host} header, possibly with a port
     * @param baseDomain the configured tenant base domain, possibly with a port
     * @return the tenant label, or {@code null} if this host names no tenant
     */
    public static String labelFrom(String hostHeader, String baseDomain) {
        if (hostHeader == null || hostHeader.isBlank() || baseDomain == null || baseDomain.isBlank()) {
            return null;
        }
        String host = stripPort(hostHeader.trim().toLowerCase(Locale.ROOT));
        String base = stripPort(baseDomain.trim().toLowerCase(Locale.ROOT));
        if (base.startsWith(".")) {
            base = base.substring(1);
        }
        if (host.isEmpty() || base.isEmpty()) {
            return null;
        }

        String suffix = "." + base;
        if (!host.endsWith(suffix)) {
            // The apex itself, localhost, an IP, or a cluster-internal name.
            return null;
        }

        String label = host.substring(0, host.length() - suffix.length());
        if (label.isEmpty() || label.contains(".")) {
            // Empty means the host was exactly ".base". A dot means a multi-level
            // label, which a single-label wildcard certificate cannot serve — so
            // picking the first segment would be a guess, not a resolution.
            return null;
        }
        if (!isValidLabel(label) || Slugs.isReserved(label)) {
            return null;
        }
        return label;
    }

    /**
     * Strips a trailing {@code :port}. Bracketed IPv6 literals are rejected
     * outright: they cannot carry a tenant label, and treating the text inside the
     * brackets as one would be nonsense.
     */
    private static String stripPort(String value) {
        if (value.startsWith("[")) {
            return "";
        }
        int colon = value.lastIndexOf(':');
        return colon >= 0 ? value.substring(0, colon) : value;
    }

    /** DNS label characters only. Notably excludes '_', which is legal in a Host header. */
    private static boolean isValidLabel(String label) {
        if (label.startsWith("-") || label.endsWith("-")) {
            return false;
        }
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
```

- [ ] **Step 4: Run to verify it passes**

```bash
cd backend && ./gradlew :api-gateway:test --tests 'com.qrserve.gateway.tenant.TenantHostTest'
```

Expected: `BUILD SUCCESSFUL`, 10 tests passing.

- [ ] **Step 5: Implement `TenantSlugResolver`**

Create `backend/api-gateway/src/main/java/com/qrserve/gateway/tenant/TenantSlugResolver.java`:

```java
package com.qrserve.gateway.tenant;

import com.qrserve.shared.common.TenantCacheKeys;
import com.qrserve.shared.common.dto.TenantResolutionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Turns a subdomain label into a merchant id, cached in Redis.
 *
 * <h2>Misses are cached too</h2>
 * On a public wildcard domain, bots enumerate subdomains. Without negative
 * caching every {@code xyz.qrserve.safaricom.et} probe becomes a gateway hop plus
 * a database round trip, which is a free amplification primitive. A miss is
 * cached as a sentinel with a much shorter TTL, so a newly created tenant still
 * starts resolving quickly.
 *
 * <h2>Why a configured URL rather than {@code lb://}</h2>
 * The lookup runs on every cache-missing request, ahead of routing. A plain
 * {@code WebClient} against a configured URL keeps this filter independent of the
 * load balancer's own state, and matches the pattern qr-service already uses for
 * its merchant-service calls.
 */
@Component
@Slf4j
public class TenantSlugResolver {

    // Key format and TTLs come from TenantCacheKeys so the gateway (which writes)
    // and merchant-service (which invalidates) cannot drift apart.
    private static final Duration HIT_TTL = Duration.ofSeconds(TenantCacheKeys.HIT_TTL_SECONDS);
    private static final Duration MISS_TTL = Duration.ofSeconds(TenantCacheKeys.MISS_TTL_SECONDS);

    private final ReactiveStringRedisTemplate redis;
    private final WebClient webClient;

    public TenantSlugResolver(
            ReactiveStringRedisTemplate redis,
            WebClient.Builder webClientBuilder,
            @Value("${services.merchant-service-url:http://localhost:8085}") String merchantServiceUrl) {
        this.redis = redis;
        this.webClient = webClientBuilder.baseUrl(merchantServiceUrl).build();
    }

    /** @return the merchant id, or an empty {@code Mono} if the slug names no tenant */
    public Mono<UUID> resolve(String slug) {
        String key = TenantCacheKeys.slugKey(slug);
        return redis.opsForValue().get(key)
                .flatMap(cached -> TenantCacheKeys.NEGATIVE.equals(cached)
                        ? Mono.empty()
                        : Mono.just(UUID.fromString(cached)))
                .switchIfEmpty(Mono.defer(() -> lookupAndCache(slug, key)))
                // A Redis outage must not take the whole platform down: fall back to
                // the direct lookup rather than failing the request.
                .onErrorResume(e -> {
                    log.warn("Tenant cache unavailable for '{}', falling back to direct lookup: {}",
                            slug, e.getMessage());
                    return lookup(slug);
                });
    }

    private Mono<UUID> lookupAndCache(String slug, String key) {
        return lookup(slug)
                .flatMap(id -> redis.opsForValue()
                        .set(key, id.toString(), HIT_TTL)
                        .thenReturn(id))
                .switchIfEmpty(Mono.defer(() -> redis.opsForValue()
                        .set(key, TenantCacheKeys.NEGATIVE, MISS_TTL)
                        .then(Mono.empty())))
                .onErrorResume(e -> {
                    log.warn("Failed to cache tenant '{}': {}", slug, e.getMessage());
                    return lookup(slug);
                });
    }

    private Mono<UUID> lookup(String slug) {
        return webClient.get()
                .uri("/api/v1/public/tenants/by-slug/{slug}", slug)
                .retrieve()
                .body(TenantResolutionResponse.class)
                .map(TenantResolutionResponse::getMerchantId)
                .onErrorResume(e -> {
                    // A 404 is the ordinary "no such tenant" answer, not a failure.
                    log.debug("Tenant lookup for '{}' returned no result: {}", slug, e.getMessage());
                    return Mono.empty();
                });
    }
}
```

**Implementer note:** if `.retrieve()` is not available on this Spring version's `WebClient`, use the classic form — `.retrieve()` was introduced alongside the `RestClient`-style API in Spring Framework 7. The equivalent is:

```java
        return webClient.get()
                .uri("/api/v1/public/tenants/by-slug/{slug}", slug)
                .retrieve()
                .body(TenantResolutionResponse.class)
```
becomes
```java
        return webClient.get()
                .uri("/api/v1/public/tenants/by-slug/{slug}", slug)
                .retrieve()
                .bodyToMono(TenantResolutionResponse.class)
```
Try `bodyToMono` on `.retrieve()` first; if that does not compile, `.exchangeToMono(r -> r.bodyToMono(TenantResolutionResponse.class))` works on every 6.x and 7.x version. Confirm which compiles before moving on — do not leave both.

- [ ] **Step 6: Write the failing filter test**

Create `backend/api-gateway/src/test/java/com/qrserve/gateway/tenant/TenantResolutionGlobalFilterTest.java`:

```java
package com.qrserve.gateway.tenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantResolutionGlobalFilterTest {

    private static final UUID MERCHANT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private TenantSlugResolver resolver;
    private TenantResolutionGlobalFilter filter;
    /** Captures the exchange the chain actually received, i.e. post-mutation. */
    private AtomicReference<ServerWebExchange> forwarded;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        resolver = mock(TenantSlugResolver.class);
        when(resolver.resolve(anyString())).thenReturn(Mono.empty());
        when(resolver.resolve("sunrise")).thenReturn(Mono.just(MERCHANT_ID));

        filter = new TenantResolutionGlobalFilter(resolver, "qrserve.safaricom.et");

        forwarded = new AtomicReference<>();
        chain = ex -> {
            forwarded.set(ex);
            return Mono.empty();
        };
    }

    private MockServerWebExchange exchange(String host, HttpHeaders extra) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest
                .get("/api/v1/public/menu/sunrise/main/1")
                .header(HttpHeaders.HOST, host);
        extra.forEach((name, values) -> values.forEach(v -> builder.header(name, v)));
        return MockServerWebExchange.from(builder.build());
    }

    private HttpHeaders forwardedHeaders() {
        assertNotNull(forwarded.get(), "the chain was not invoked");
        return forwarded.get().getRequest().getHeaders();
    }

    @Test
    @DisplayName("a known tenant host injects X-Tenant-Id and X-Tenant-Slug")
    void injectsTenantHeaders() {
        filter.filter(exchange("sunrise.qrserve.safaricom.et", new HttpHeaders()), chain).block();

        assertEquals(MERCHANT_ID.toString(),
                forwardedHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_ID_HEADER));
        assertEquals("sunrise",
                forwardedHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_SLUG_HEADER));
    }

    @Test
    @DisplayName("an INBOUND X-Tenant-Id is stripped, never trusted")
    void stripsInboundTenantHeader() {
        // Without this the header is decorative: any caller sets it and every
        // downstream service believes it. This is the single most important
        // assertion in the tenancy work.
        HttpHeaders forged = new HttpHeaders();
        forged.add(TenantResolutionGlobalFilter.TENANT_ID_HEADER, UUID.randomUUID().toString());
        forged.add(TenantResolutionGlobalFilter.TENANT_SLUG_HEADER, "victim");

        filter.filter(exchange("sunrise.qrserve.safaricom.et", forged), chain).block();

        assertEquals(MERCHANT_ID.toString(),
                forwardedHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_ID_HEADER),
                "the forged value must be replaced by the host-resolved one");
        assertEquals(1, forwardedHeaders().get(TenantResolutionGlobalFilter.TENANT_ID_HEADER).size(),
                "the forged value must not survive alongside the real one");
    }

    @Test
    @DisplayName("a forged header on a NON-tenant host is stripped and not replaced")
    void stripsForgedHeaderOnNonTenantHost() {
        // The dangerous case: no host label means nothing overwrites the forgery,
        // so removal has to happen unconditionally rather than as a side effect of
        // injection.
        HttpHeaders forged = new HttpHeaders();
        forged.add(TenantResolutionGlobalFilter.TENANT_ID_HEADER, UUID.randomUUID().toString());

        filter.filter(exchange("localhost:8081", forged), chain).block();

        assertNull(forwardedHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_ID_HEADER));
    }

    @Test
    @DisplayName("an unknown tenant label is 404 and the chain is never invoked")
    void unknownTenantIsNotFound() {
        MockServerWebExchange ex = exchange("no-such-tenant.qrserve.safaricom.et", new HttpHeaders());

        filter.filter(ex, chain).block();

        assertEquals(HttpStatus.NOT_FOUND, ex.getResponse().getStatusCode());
        assertNull(forwarded.get(), "an unresolvable tenant must not reach a service");
    }

    @Test
    @DisplayName("a non-tenant host passes through with no tenant header")
    void nonTenantHostPassesThrough() {
        filter.filter(exchange("localhost:8081", new HttpHeaders()), chain).block();

        assertNotNull(forwarded.get(), "localhost must keep working for development and probes");
        assertNull(forwardedHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_ID_HEADER));
    }

    @Test
    @DisplayName("the admin host asserts no tenant")
    void adminHostAssertsNoTenant() {
        filter.filter(exchange("admin.qrserve.safaricom.et", new HttpHeaders()), chain).block();

        assertNotNull(forwarded.get());
        assertNull(forwardedHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_ID_HEADER),
                "SUPER_ADMIN cross-tenant work must not be pinned to a tenant");
    }

    @Test
    @DisplayName("the filter runs before routing")
    void runsBeforeRouting() {
        // A tenant header injected after the routing filter has already copied the
        // request would never reach the service.
        org.junit.jupiter.api.Assertions.assertTrue(filter.getOrder() < 0,
                "must be ordered ahead of the routing filter");
    }
}
```

- [ ] **Step 7: Add the gateway test dependency**

`api-gateway` is excluded from `standardBusinessServices`, so it has no test dependencies beyond the base `spring-boot-starter-test`. `MockServerWebExchange` lives in `spring-test`, which that starter provides, but Mockito's `mock` also needs to be on the path — it is, via the same starter. In `backend/build.gradle`, inside `project(':api-gateway')`, add:

```groovy
        // MockServerWebExchange and the reactive mocks used by the tenant filter
        // tests live in spring-test, which spring-boot-starter-test already brings
        // in from the subprojects block. WebFlux is needed at test compile time for
        // the exchange types themselves.
        testImplementation 'org.springframework.boot:spring-boot-starter-webflux'
```

- [ ] **Step 8: Implement the filter**

Create `backend/api-gateway/src/main/java/com/qrserve/gateway/tenant/TenantResolutionGlobalFilter.java`:

```java
package com.qrserve.gateway.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Establishes tenant identity once, at the edge.
 *
 * <p>Four steps, in this order, for every request:
 * <ol>
 *   <li><b>Strip</b> any inbound {@code X-Tenant-*} header. Unconditionally —
 *       including on hosts that carry no tenant, where nothing would otherwise
 *       overwrite a forgery. Downstream services trust this header; that trust is
 *       only earned by this step.</li>
 *   <li><b>Extract</b> the first DNS label from {@code Host}.</li>
 *   <li><b>Resolve</b> it to a merchant id through Redis, or 404.</li>
 *   <li><b>Inject</b> {@code X-Tenant-Id} and {@code X-Tenant-Slug}.</li>
 * </ol>
 *
 * <p>A host with no tenant label is not an error: localhost, cluster-internal
 * names, kubelet probes and {@code admin.} all pass through carrying no tenant.
 * What is an error is a label that looks like a tenant and is not — that is a
 * 404, never a fallback to some default tenant.
 */
@Component
@Slf4j
public class TenantResolutionGlobalFilter implements GlobalFilter, Ordered {

    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String TENANT_SLUG_HEADER = "X-Tenant-Slug";

    private final TenantSlugResolver resolver;
    private final String baseDomain;

    public TenantResolutionGlobalFilter(
            TenantSlugResolver resolver,
            @Value("${app.public-base-domain}") String baseDomain) {
        this.resolver = resolver;
        this.baseDomain = baseDomain;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String hostHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST);
        if (hostHeader == null) {
            hostHeader = exchange.getRequest().getURI().getHost();
        }

        String label = TenantHost.labelFrom(hostHeader, baseDomain);

        if (label == null) {
            // No tenant in this host. Strip anyway — this is the branch where a
            // forged header would otherwise survive untouched.
            return chain.filter(withoutTenantHeaders(exchange));
        }

        return resolver.resolve(label)
                .flatMap(merchantId -> chain.filter(withTenant(exchange, merchantId, label)))
                .switchIfEmpty(Mono.defer(() -> {
                    // Never fall back to a default tenant: on a wildcard domain that
                    // would turn every mistyped subdomain into a cross-tenant read.
                    log.debug("No tenant for host label '{}'", label);
                    exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
                    return exchange.getResponse().setComplete();
                }));
    }

    private ServerWebExchange withoutTenantHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(TENANT_ID_HEADER);
                    headers.remove(TENANT_SLUG_HEADER);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private ServerWebExchange withTenant(ServerWebExchange exchange, UUID merchantId, String slug) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    // set(), not add(): a forged value must be replaced, not joined.
                    headers.set(TENANT_ID_HEADER, merchantId.toString());
                    headers.set(TENANT_SLUG_HEADER, slug);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    /**
     * Ahead of the routing filter (order 10000) and of
     * {@code NettyRoutingFilter}. A header injected after routing has copied the
     * request would never reach the service.
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
```

- [ ] **Step 9: Configure the merchant-service URL for the gateway**

In `backend/api-gateway/src/main/resources/application.yml`, add near the `app:` block from Task 2:

```yaml
# Used by TenantSlugResolver for slug -> merchantId lookups on a cache miss.
# A configured URL rather than lb:// so tenant resolution does not depend on
# load-balancer state on the request path.
services:
  merchant-service-url: ${MERCHANT_SERVICE_URL:http://localhost:8085}
```

And in `backend/docker-compose.yml` under the `api-gateway` service, plus `backend/k8s/deployment.yml` for the gateway container:

```yaml
      - MERCHANT_SERVICE_URL=http://merchant-service:8085
```

- [ ] **Step 10: Run the gateway tests**

```bash
cd backend && ./gradlew :api-gateway:test
```

Expected: `BUILD SUCCESSFUL`, 17 tests passing across the two classes.

- [ ] **Step 11: Prove the strip test is not vacuous**

Temporarily comment out the two `headers.remove(...)` lines in `withoutTenantHeaders`, re-run, and confirm `stripsForgedHeaderOnNonTenantHost` **fails**. Restore the lines. A test that passes with the protection removed is worse than no test, and this is the one assertion that must not be decorative.

```bash
cd backend && ./gradlew :api-gateway:test --tests '*TenantResolutionGlobalFilterTest'
```

- [ ] **Step 12: Commit**

```bash
git add backend/api-gateway/src/ backend/build.gradle backend/docker-compose.yml backend/k8s/deployment.yml
git commit -m "feat(tenancy): resolve the tenant from the host at the gateway

Strips any inbound X-Tenant-* header unconditionally before injecting the
resolved one, so downstream trust in the header is actually earned."
```

---

### Task 11: `TenantContextFilter` — the precedence rule

**Why:** two sources of tenant identity now exist per request, and anywhere they can disagree is an attack surface. The rule: **the host never grants authority the JWT does not already carry.** A waiter at merchant A pointing a browser at `merchant-b.qrserve.safaricom.et` gets 403, not merchant B's data — enforced in one filter rather than remembered in every controller.

`TenantContext` already exists in `shared:common` and is currently unused; `JwtAuthenticationFilter` even imports it without reading it. This task is what finally populates it.

**Files:**
- Create: `backend/shared/security/src/main/java/com/qrserve/shared/security/TenantContextFilter.java`
- Modify: `backend/shared/security/src/main/java/com/qrserve/shared/security/SecurityConfig.java`
- Create: `backend/shared/security/src/test/java/com/qrserve/shared/security/TenantContextFilterTest.java`

**Interfaces:**
- Consumes: `TenantContext.setCurrentTenant(UUID)` / `getCurrentTenant()` / `clear()` (exists), `TenantResolutionGlobalFilter.TENANT_ID_HEADER` — **re-declared** here as a local constant, because `shared:security` must not depend on `api-gateway`.
- Produces: `TenantContextFilter.TENANT_ID_HEADER` = `"X-Tenant-Id"`, `TENANT_SLUG_HEADER` = `"X-Tenant-Slug"`.

- [ ] **Step 1: Write the failing test**

Create `backend/shared/security/src/test/java/com/qrserve/shared/security/TenantContextFilterTest.java`:

```java
package com.qrserve.shared.security;

import com.qrserve.shared.common.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantContextFilterTest {

    private static final UUID MERCHANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MERCHANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticateAs(UUID merchantId, UserRole role) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID()).merchantId(merchantId)
                .email("staff@test").role(role).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "token",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    /** Captures the tenant as it was DURING the chain, since the filter clears it after. */
    private AtomicReference<UUID> runFilter(MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        AtomicReference<UUID> seen = new AtomicReference<>();
        AtomicReference<Boolean> chainRan = new AtomicReference<>(false);
        FilterChain chain = (req, res) -> {
            chainRan.set(true);
            seen.set(TenantContext.getCurrentTenant());
        };
        filter.doFilter(request, response, chain);
        request.setAttribute("chainRan", chainRan.get());
        return seen;
    }

    private MockHttpServletRequest request(UUID tenantHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        if (tenantHeader != null) {
            request.addHeader(TenantContextFilter.TENANT_ID_HEADER, tenantHeader.toString());
        }
        return request;
    }

    @Test
    @DisplayName("an anonymous request takes its tenant from the host header")
    void anonymousTakesTenantFromHost() throws Exception {
        MockHttpServletRequest req = request(MERCHANT_A);
        assertEquals(MERCHANT_A, runFilter(req, new MockHttpServletResponse()).get());
    }

    @Test
    @DisplayName("a matching host and JWT proceed normally")
    void matchingHostAndJwtProceed() throws Exception {
        authenticateAs(MERCHANT_A, UserRole.WAITER);
        MockHttpServletRequest req = request(MERCHANT_A);
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertEquals(MERCHANT_A, runFilter(req, res).get());
        assertEquals(200, res.getStatus());
    }

    @Test
    @DisplayName("a host that disagrees with the JWT is 403 and the chain does not run")
    void mismatchIsForbidden() throws Exception {
        // A waiter at merchant A visiting merchant-b.qrserve.safaricom.et.
        authenticateAs(MERCHANT_A, UserRole.WAITER);
        MockHttpServletRequest req = request(MERCHANT_B);
        MockHttpServletResponse res = new MockHttpServletResponse();

        runFilter(req, res);

        assertEquals(403, res.getStatus());
        assertFalse((Boolean) req.getAttribute("chainRan"),
                "the request must not reach the controller");
        assertTrue(res.getContentAsString().contains("403"),
                "the body must be the standard error shape, not empty");
    }

    @Test
    @DisplayName("the JWT wins: staff on a non-tenant host still carry their own tenant")
    void jwtIsAuthoritativeWhenNoHostTenant() throws Exception {
        // Direct service access, or localhost during development.
        authenticateAs(MERCHANT_A, UserRole.WAITER);
        assertEquals(MERCHANT_A, runFilter(request(null), new MockHttpServletResponse()).get());
    }

    @Test
    @DisplayName("SUPER_ADMIN asserts no tenant even on a tenant host")
    void superAdminAssertsNoTenant() throws Exception {
        authenticateAs(null, UserRole.SUPER_ADMIN);
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertNull(runFilter(request(MERCHANT_A), res).get(),
                "cross-tenant work must not be silently pinned to whichever host was used");
        assertEquals(200, res.getStatus());
    }

    @Test
    @DisplayName("an unparseable tenant header is ignored rather than crashing the request")
    void malformedHeaderIsIgnored() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader(TenantContextFilter.TENANT_ID_HEADER, "not-a-uuid");

        assertNull(runFilter(req, new MockHttpServletResponse()).get());
    }

    @Test
    @DisplayName("the ThreadLocal is cleared after the request, even when the chain throws")
    void clearsThreadLocalOnException() {
        // Servlet containers pool request threads. A leaked value is served to the
        // NEXT request on that thread, which in a shared multi-tenant deployment
        // means one tenant's context applied to another tenant's request.
        MockHttpServletRequest req = request(MERCHANT_A);
        FilterChain boom = (request, response) -> {
            throw new java.io.IOException("downstream failure");
        };

        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> filter.doFilter(req, new MockHttpServletResponse(), boom));
        assertNull(TenantContext.getCurrentTenant(), "the ThreadLocal leaked");
    }

    @Test
    @DisplayName("the ThreadLocal is cleared after a normal request too")
    void clearsThreadLocalOnSuccess() throws Exception {
        runFilter(request(MERCHANT_A), new MockHttpServletResponse());
        assertNull(TenantContext.getCurrentTenant());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./gradlew :shared:security:test --tests 'com.qrserve.shared.security.TenantContextFilterTest'
```

Expected: compilation failure — `cannot find symbol: class TenantContextFilter`.

- [ ] **Step 3: Implement the filter**

Create `backend/shared/security/src/main/java/com/qrserve/shared/security/TenantContextFilter.java`:

```java
package com.qrserve.shared.security;

import com.qrserve.shared.common.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Populates {@link TenantContext} from the gateway-injected {@code X-Tenant-Id}
 * and enforces the precedence rule between the two sources of tenant identity.
 *
 * <h2>Precedence</h2>
 * <table>
 *   <tr><th>Request kind</th><th>Tenant source</th><th>On mismatch</th></tr>
 *   <tr><td>Anonymous</td><td>the host</td><td>n/a — the host is the only signal</td></tr>
 *   <tr><td>Authenticated staff</td><td>the <b>JWT</b></td><td><b>403</b></td></tr>
 *   <tr><td>SUPER_ADMIN</td><td>none asserted</td><td>n/a</td></tr>
 * </table>
 *
 * <p>The host never grants authority the JWT does not already carry. A waiter at
 * merchant A who points a browser at {@code merchant-b.qrserve.safaricom.et}
 * receives 403 rather than merchant B's data. Enforcing that here, once, is the
 * point: as a per-controller convention it would be forgotten on the next
 * endpoint someone adds.
 *
 * <p>Absence of the header is <b>not</b> an error. It means the request did not
 * arrive through a tenant host — direct service access, a kubelet probe, or
 * localhost during development — and the JWT then supplies the tenant. What is
 * never allowed is inventing one.
 *
 * <p>The header itself is only trustworthy because
 * {@code TenantResolutionGlobalFilter} strips any inbound copy at the gateway
 * before injecting the resolved value.
 */
@Component
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    /**
     * Duplicated from the gateway's constant rather than imported: {@code
     * shared:security} is consumed by every servlet service and must not depend on
     * {@code api-gateway}. The two must stay in step; the filter test and the
     * gateway test both name the literal.
     */
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String TENANT_SLUG_HEADER = "X-Tenant-Slug";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        UUID hostTenant = parseTenantHeader(request);
        UUID jwtTenant = authenticatedMerchantId();
        boolean superAdmin = isSuperAdmin();

        if (!superAdmin && hostTenant != null && jwtTenant != null && !hostTenant.equals(jwtTenant)) {
            log.warn("Tenant mismatch: host asserts {}, token carries {} on {} {}",
                    hostTenant, jwtTenant, request.getMethod(), request.getRequestURI());
            writeForbidden(response);
            return;
        }

        // SUPER_ADMIN asserts no tenant: cross-tenant work must not be silently
        // pinned to whichever hostname happened to be used to reach it.
        UUID effective = superAdmin ? null : (jwtTenant != null ? jwtTenant : hostTenant);

        try {
            if (effective != null) {
                TenantContext.setCurrentTenant(effective);
            }
            filterChain.doFilter(request, response);
        } finally {
            // MUST be in a finally block. Servlet containers pool request threads,
            // so a value left behind here is served to the next request on this
            // thread — one tenant's context applied to another tenant's request.
            TenantContext.clear();
        }
    }

    private UUID parseTenantHeader(HttpServletRequest request) {
        String raw = request.getHeader(TENANT_ID_HEADER);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            // Only the gateway sets this header, so a malformed value means a
            // misconfiguration rather than an attack — but treating it as "no
            // tenant" is still safer than failing the request, because the JWT
            // check below is what actually protects the data.
            log.warn("Ignoring malformed {} header", TENANT_ID_HEADER);
            return null;
        }
    }

    private UUID authenticatedMerchantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getMerchantId();
        }
        return null;
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal principal
                && principal.getRole() == UserRole.SUPER_ADMIN;
    }

    /**
     * Written directly rather than thrown as {@code AccessDeniedException}: an
     * exception raised in a filter never reaches {@code GlobalExceptionHandler},
     * which is a {@code @RestControllerAdvice} and only covers dispatched
     * requests. The body matches {@code GlobalExceptionHandler.ErrorResponse} so
     * clients see one error shape.
     */
    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":403,"
                        + "\"message\":\"This account does not belong to the tenant in the address bar.\","
                        + "\"timestamp\":\"" + LocalDateTime.now() + "\"}");
    }
}
```

- [ ] **Step 4: Register the filter in `SecurityConfig`**

In `backend/shared/security/src/main/java/com/qrserve/shared/security/SecurityConfig.java`, take the filter as a constructor dependency and add it **after** `JwtAuthenticationFilter` — it reads the principal that filter establishes, so the order matters:

```java
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final TenantContextFilter tenantContextFilter;

    // Explicit constructor injection instead of Lombok @RequiredArgsConstructor
    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, TenantContextFilter tenantContextFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.tenantContextFilter = tenantContextFilter;
    }
```

```java
            // Add JWT Filter before Spring's default username/password filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // AFTER the JWT filter: the tenant precedence check compares the
            // gateway's host-derived tenant against the authenticated principal's,
            // so the principal has to exist by the time it runs.
            .addFilterAfter(tenantContextFilter, JwtAuthenticationFilter.class);
```

Add the import `org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer` is already present; no new imports are needed.

- [ ] **Step 5: Run the tests**

```bash
cd backend && ./gradlew :shared:security:test
```

Expected: `BUILD SUCCESSFUL`. `TenantContextFilterTest` 8 tests plus the existing `JwtTokenProviderTokenTypeTest` 10.

- [ ] **Step 6: Prove the mismatch test is not vacuous**

Temporarily change the mismatch condition to `if (false)`, re-run, and confirm `mismatchIsForbidden` **fails**. Restore it. Also temporarily remove the `finally` and confirm `clearsThreadLocalOnException` fails. Both protections are invisible in normal operation and catastrophic if absent, so each needs to be shown to be doing work.

- [ ] **Step 7: Commit**

```bash
git add backend/shared/security/src/
git commit -m "feat(tenancy): enforce host/JWT tenant precedence in one filter

The JWT is authoritative for authenticated staff; a host that disagrees is
403. TenantContext is cleared in a finally block because request threads are
pooled and a leak crosses tenants."
```

---

### Task 12: Anonymous host-tenant enforcement on the public endpoints

**A spec clarification, made explicit here.** Section 1 of the design says an anonymous request on a tenant-scoped path with no tenant header is "rejected, not treated as no tenant". Taken literally that would break the demo route the spec also says to keep (`/menu/demo/main/1` on a non-tenant host) and every direct-to-service call.

The resolution: both public endpoints **name their tenant explicitly in the path or in the resource** — `/api/v1/public/menu/{merchantSlug}/...` and `POST /api/v1/tables/{tableId}/requests`. They do not need an implicit tenant, so absence of the header is harmless. What *is* dangerous is **disagreement**: a guest on `sunrise.qrserve.safaricom.et` resolving `blue-nile`'s menu, or firing service calls at `blue-nile`'s tables. That is the anonymous analogue of the JWT mismatch rule, and it is what gets enforced.

**Files:**
- Modify: `backend/merchant-service/src/main/java/com/qrserve/merchant/service/PublicMenuResolutionService.java`
- Modify: `backend/merchant-service/src/main/java/com/qrserve/merchant/controller/PublicCustomerRequestController.java`
- Modify: `backend/merchant-service/src/test/java/com/qrserve/merchant/TenantIsolationIT.java`

**Interfaces:**
- Consumes: `TenantContext.getCurrentTenant()` (populated by Task 11).
- Produces: no new public API. Both endpoints now throw `AccessDeniedException` (→ 403 via `GlobalExceptionHandler`) when the host tenant disagrees with the addressed resource.

- [ ] **Step 1: Write the failing tests**

Append to `TenantIsolationIT`:

```java
    @Test
    @DisplayName("a guest on tenant A's host cannot resolve tenant B's menu")
    void hostTenantMustMatchResolvedMerchant() throws Exception {
        // The anonymous mirror of the JWT mismatch rule. Without it the subdomain
        // is decoration: anyone could read any tenant's menu from any host.
        mockMvc.perform(get("/api/v1/public/menu/blue-nile/main/1")
                        .header("X-Tenant-Id", merchantA.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a guest on the matching host resolves normally")
    void matchingHostResolvesNormally() throws Exception {
        mockMvc.perform(get("/api/v1/public/menu/sunrise/main/1")
                        .header("X-Tenant-Id", merchantA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantA.getId().toString()));
    }

    @Test
    @DisplayName("no host tenant still resolves, because the path names the merchant")
    void noHostTenantStillResolves() throws Exception {
        // The demo route and direct-to-service access. The path is self-identifying,
        // so the absence of a host tenant grants nothing.
        mockMvc.perform(get("/api/v1/public/menu/sunrise/main/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a guest on tenant A's host cannot call a waiter at tenant B's table")
    void hostTenantMustMatchRequestedTable() throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("requestType", "CALL_WAITER", "note", "test"));

        mockMvc.perform(post("/api/v1/tables/" + tableB.getId() + "/requests")
                        .header("X-Tenant-Id", merchantA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a guest on the matching host can call a waiter")
    void matchingHostCanCallWaiter() throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("requestType", "CALL_WAITER", "note", "test"));

        mockMvc.perform(post("/api/v1/tables/" + tableA.getId() + "/requests")
                        .header("X-Tenant-Id", merchantA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
```

- [ ] **Step 2: Run to verify the two mismatch tests fail**

```bash
cd backend && ./gradlew :merchant-service:test --tests 'com.qrserve.merchant.TenantIsolationIT'
```

Expected: `hostTenantMustMatchResolvedMerchant` and `hostTenantMustMatchRequestedTable` return 200 instead of 403.

- [ ] **Step 3: Enforce it in `PublicMenuResolutionService`**

In `backend/merchant-service/src/main/java/com/qrserve/merchant/service/PublicMenuResolutionService.java`, add the imports `com.qrserve.shared.common.TenantContext` and `org.springframework.security.access.AccessDeniedException`, and insert the check immediately after the merchant is resolved:

```java
        // 1. Resolve merchant by slug
        MerchantEntity merchant = merchantService.getMerchantBySlug(merchantSlug);

        // 1b. If the request arrived through a tenant host, that host must agree
        //     with the merchant named in the path. Without this the subdomain is
        //     decoration: a guest on sunrise.qrserve.safaricom.et could read any
        //     other tenant's menu just by editing the path.
        //
        //     Absence of a host tenant is NOT an error here. This path names its
        //     own merchant, so it needs no implicit tenant — that is what keeps the
        //     demo route and direct-to-service access working.
        UUID hostTenant = TenantContext.getCurrentTenant();
        if (hostTenant != null && !hostTenant.equals(merchant.getId())) {
            throw new AccessDeniedException("This menu belongs to a different tenant");
        }
```

- [ ] **Step 4: Enforce it in `PublicCustomerRequestController`**

In `backend/merchant-service/src/main/java/com/qrserve/merchant/controller/PublicCustomerRequestController.java`, add the imports `com.qrserve.shared.common.TenantContext`, `org.springframework.security.access.AccessDeniedException`, `java.util.UUID`, and insert after the table lookup:

```java
        // 1. Resolve the table and scope lookups by merchantId/branchId
        TableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found ID: " + tableId));

        // 1b. A guest on one tenant's host must not be able to fire service calls
        //     at another tenant's tables. Table ids are sequential and therefore
        //     trivially enumerable, so without this the only thing standing between
        //     a bored guest and every kitchen on the platform is the signature check
        //     below — which is optional.
        UUID hostTenant = TenantContext.getCurrentTenant();
        if (hostTenant != null && !hostTenant.equals(table.getMerchantId())) {
            throw new AccessDeniedException("This table belongs to a different tenant");
        }
```

- [ ] **Step 5: Run the gate**

```bash
cd backend && ./gradlew :merchant-service:test
```

Expected: `BUILD SUCCESSFUL` — every test in `TenantIsolationIT` and `TableQrUrlTest` green.

**Note on why these tests can set `X-Tenant-Id` directly:** `MockMvc` bypasses the gateway, so the header is simulated. That is exactly what makes Task 10's strip test load-bearing — it is the only thing that stops a real client doing the same. The two tests are complements, not duplicates.

- [ ] **Step 6: Commit**

```bash
git add backend/merchant-service/src/
git commit -m "feat(tenancy): reject anonymous cross-tenant access on the public endpoints"
```

---

### Task 13: Frontend — the host wins over the path

**Why:** on a tenant host the canonical URL is `/menu/{branchSlug}/{tableNumber}` — two segments. The existing router already has a two-segment route, `/menu/:merchantSlug/:tableNumber`, so on `sunrise.qrserve.safaricom.et/menu/main/1` React Router would bind `merchantSlug="main"` and `tableNumber="1"`, and the page would ask the backend to resolve a merchant called "main". It fails in a confusing way rather than an obvious one, which is worse.

The rule from the spec: **the host label when present, the path parameter otherwise.** One rule, both URL forms work.

**Files:**
- Create: `src/lib/tenant.ts`
- Create: `src/lib/tenant.test.ts`
- Modify: `src/pages/CustomerMenuPage.tsx`
- Modify: `vite.config.ts`
- Modify: `package.json` (add the `test:unit` script)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `tenantSlugFromHost(hostname: string, baseDomain: string): string | null`
  - `resolveMenuTarget(params: MenuRouteParams, hostSlug: string | null): MenuTarget`
  - `type MenuRouteParams = { merchantSlug?: string; branchSlug?: string; tableNumber?: string }`
  - `type MenuTarget = { merchantSlug: string; branchSlug: string; tableNumber: string; hostMismatch: boolean } | null`
  - `PUBLIC_BASE_DOMAIN: string` — from `import.meta.env.VITE_PUBLIC_BASE_DOMAIN`
  - `currentTenantSlug(): string | null` — reads `window.location.hostname`

- [ ] **Step 1: Add a test script**

There is no test runner in this project. Rather than add vitest for two pure functions, use `tsx` (already a devDependency) with Node's assertion module. In `package.json`:

```json
    "test:unit": "tsx src/lib/tenant.test.ts",
```

- [ ] **Step 2: Write the failing test**

Create `src/lib/tenant.test.ts`:

```ts
/**
 * Pure-function tests for tenant host resolution. Run with `npm run test:unit`.
 *
 * These two functions decide which restaurant's menu a scanned QR code shows.
 * Getting them wrong means a customer at one cafe sees another cafe's menu, so
 * they are worth testing even though the project has no test runner.
 */
import assert from 'node:assert/strict';
import { resolveMenuTarget, tenantSlugFromHost } from './tenant';

let failures = 0;
function test(name: string, fn: () => void) {
  try {
    fn();
    console.log(`  ok  ${name}`);
  } catch (error) {
    failures += 1;
    console.error(`FAIL  ${name}`);
    console.error(`      ${(error as Error).message}`);
  }
}

const BASE = 'qrserve.safaricom.et';

// ---- tenantSlugFromHost ----

test('extracts the tenant label from a subdomain', () => {
  assert.equal(tenantSlugFromHost('sunrise.qrserve.safaricom.et', BASE), 'sunrise');
});

test('the apex carries no tenant', () => {
  assert.equal(tenantSlugFromHost('qrserve.safaricom.et', BASE), null);
});

test('localhost carries no tenant, so path-based development keeps working', () => {
  assert.equal(tenantSlugFromHost('localhost', BASE), null);
  assert.equal(tenantSlugFromHost('127.0.0.1', BASE), null);
});

test('reserved labels carry no tenant', () => {
  assert.equal(tenantSlugFromHost('admin.qrserve.safaricom.et', BASE), null);
  assert.equal(tenantSlugFromHost('www.qrserve.safaricom.et', BASE), null);
});

test('a multi-level label carries no tenant', () => {
  assert.equal(tenantSlugFromHost('a.b.qrserve.safaricom.et', BASE), null);
});

test('is case-insensitive', () => {
  assert.equal(tenantSlugFromHost('SunRise.QRServe.Safaricom.ET', BASE), 'sunrise');
});

test('an unconfigured base domain yields no tenant rather than throwing', () => {
  // A build without VITE_PUBLIC_BASE_DOMAIN must degrade to path-based URLs, not
  // white-screen.
  assert.equal(tenantSlugFromHost('sunrise.qrserve.safaricom.et', ''), null);
});

test('the dev wildcard works', () => {
  assert.equal(tenantSlugFromHost('sunrise.localtest.me', 'localtest.me'), 'sunrise');
});

// ---- resolveMenuTarget ----

test('path form, three segments: the path supplies everything', () => {
  assert.deepEqual(
    resolveMenuTarget({ merchantSlug: 'sunrise', branchSlug: 'main', tableNumber: '1' }, null),
    { merchantSlug: 'sunrise', branchSlug: 'main', tableNumber: '1', hostMismatch: false },
  );
});

test('path form, two segments: the branch defaults to main', () => {
  // The legacy /menu/:merchantSlug/:tableNumber shape.
  assert.deepEqual(
    resolveMenuTarget({ merchantSlug: 'sunrise', tableNumber: '1' }, null),
    { merchantSlug: 'sunrise', branchSlug: 'main', tableNumber: '1', hostMismatch: false },
  );
});

test('host form: two path segments are the branch and the table, not the merchant', () => {
  // This is the collision the router would otherwise get wrong: on a tenant host
  // /menu/main/1 binds merchantSlug="main" in React Router.
  assert.deepEqual(
    resolveMenuTarget({ merchantSlug: 'main', tableNumber: '1' }, 'sunrise'),
    { merchantSlug: 'sunrise', branchSlug: 'main', tableNumber: '1', hostMismatch: false },
  );
});

test('host form, three segments: the path merchant must match the host', () => {
  assert.deepEqual(
    resolveMenuTarget({ merchantSlug: 'sunrise', branchSlug: 'main', tableNumber: '1' }, 'sunrise'),
    { merchantSlug: 'sunrise', branchSlug: 'main', tableNumber: '1', hostMismatch: false },
  );
});

test('host form, three segments: a mismatched path merchant is flagged, not silently overridden', () => {
  // The frontend mirror of the backend 403. Silently trusting the host would hide
  // a link that is genuinely wrong; silently trusting the path would let the host
  // be bypassed.
  const target = resolveMenuTarget(
    { merchantSlug: 'blue-nile', branchSlug: 'main', tableNumber: '1' },
    'sunrise',
  );
  assert.equal(target?.hostMismatch, true);
  assert.equal(target?.merchantSlug, 'sunrise', 'the host wins for the actual request');
});

test('a missing table number yields null rather than a request for table undefined', () => {
  assert.equal(resolveMenuTarget({ merchantSlug: 'sunrise' }, null), null);
  assert.equal(resolveMenuTarget({}, 'sunrise'), null);
});

if (failures > 0) {
  console.error(`\n${failures} test(s) failed`);
  process.exit(1);
}
console.log('\nall tenant tests passed');
```

- [ ] **Step 3: Run to verify it fails**

```bash
npm run test:unit
```

Expected: `Cannot find module './tenant'`.

- [ ] **Step 4: Implement `src/lib/tenant.ts`**

```ts
/**
 * Tenant addressing on the client.
 *
 * Each restaurant is served at {merchantSlug}.{PUBLIC_BASE_DOMAIN}, and the same
 * app also has to keep working on a bare host — the landing page's demo link
 * (/menu/demo/main/1) and local development both use the path form.
 *
 * The rule, and it is one rule: the host label when present, the path parameter
 * otherwise.
 */

/**
 * Kept in step with the backend's PUBLIC_BASE_DOMAIN. An empty value disables
 * host-based tenancy and everything falls back to the path form, which is the
 * correct behaviour for a build that has not been told its domain.
 */
export const PUBLIC_BASE_DOMAIN: string = import.meta.env.VITE_PUBLIC_BASE_DOMAIN || '';

/** Labels that are never a tenant. Must match Slugs.RESERVED_LABELS on the backend. */
const RESERVED_LABELS = new Set([
  'admin', 'api', 'app', 'www', 'static', 'assets', 'ws', 'mail', 'status',
]);

const LABEL_PATTERN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/;

/**
 * The tenant label in a hostname, or null if there is none.
 *
 * Null is the ordinary answer for localhost, an IP, the apex, a reserved label
 * and a multi-level label — all of which must fall back to the path form rather
 * than error.
 */
export function tenantSlugFromHost(hostname: string, baseDomain: string = PUBLIC_BASE_DOMAIN): string | null {
  if (!hostname || !baseDomain) return null;

  const host = stripPort(hostname.trim().toLowerCase());
  const base = stripPort(baseDomain.trim().toLowerCase()).replace(/^\./, '');
  if (!host || !base) return null;

  const suffix = `.${base}`;
  if (!host.endsWith(suffix)) return null;

  const label = host.slice(0, -suffix.length);
  // A dot means a multi-level label, which a single-label wildcard certificate
  // cannot serve, so treating the first segment as the tenant would be a guess.
  if (!label || label.includes('.')) return null;
  if (!LABEL_PATTERN.test(label) || RESERVED_LABELS.has(label)) return null;

  return label;
}

function stripPort(value: string): string {
  if (value.startsWith('[')) return '';
  const colon = value.lastIndexOf(':');
  return colon >= 0 ? value.slice(0, colon) : value;
}

/** The tenant for the page currently being served, if any. */
export function currentTenantSlug(): string | null {
  if (typeof window === 'undefined') return null;
  return tenantSlugFromHost(window.location.hostname);
}

export type MenuRouteParams = {
  merchantSlug?: string;
  branchSlug?: string;
  tableNumber?: string;
};

export type MenuTarget = {
  merchantSlug: string;
  branchSlug: string;
  tableNumber: string;
  /**
   * True when the URL named a merchant that is not the one in the address bar.
   * The host wins for the actual request, but the page should say so rather than
   * quietly serve a different restaurant than the link promised.
   */
  hostMismatch: boolean;
};

/** Default branch for the legacy two-segment path form, which omits it. */
const DEFAULT_BRANCH_SLUG = 'main';

/**
 * Turns the router's params plus the host label into the merchant, branch and
 * table to resolve.
 *
 * The interesting case is a tenant host with a two-segment path. React Router
 * matches /menu/main/1 against /menu/:merchantSlug/:tableNumber and binds
 * merchantSlug="main" — so on a tenant host the first param has to be
 * reinterpreted as the branch, or the page asks the backend for a restaurant
 * called "main".
 */
export function resolveMenuTarget(params: MenuRouteParams, hostSlug: string | null): MenuTarget | null {
  // Read the params positionally, because the route parameter NAMES lie on a
  // tenant host: React Router matches /menu/main/1 against
  // /menu/:merchantSlug/:tableNumber and binds merchantSlug="main", which on
  // sunrise.qrserve.safaricom.et is the branch, not the merchant. `branchSlug` is
  // bound only by the three-segment route, so its presence is what distinguishes
  // the two URL shapes.
  const { merchantSlug: first, branchSlug: second, tableNumber: third } = params;

  if (hostSlug) {
    if (second !== undefined) {
      // Three-segment route: the path also names a merchant, so check it agrees.
      if (!third) return null;
      return {
        merchantSlug: hostSlug,
        branchSlug: second || DEFAULT_BRANCH_SLUG,
        tableNumber: third,
        hostMismatch: Boolean(first) && first !== hostSlug,
      };
    }
    // Two-segment route: (first, third) is really (branchSlug, tableNumber).
    if (!third) return null;
    return {
      merchantSlug: hostSlug,
      branchSlug: first || DEFAULT_BRANCH_SLUG,
      tableNumber: third,
      hostMismatch: false,
    };
  }

  // No tenant host: the path is authoritative, exactly as before this change.
  if (!first || !third) return null;
  return {
    merchantSlug: first,
    branchSlug: second || DEFAULT_BRANCH_SLUG,
    tableNumber: third,
    hostMismatch: false,
  };
}
```

- [ ] **Step 5: Run to verify it passes**

```bash
npm run test:unit
```

Expected: 14 `ok` lines and `all tenant tests passed`.

- [ ] **Step 6: Use it in `CustomerMenuPage`**

In `src/pages/CustomerMenuPage.tsx`, replace the param destructuring and the `effectiveBranchSlug` line:

```tsx
import { currentTenantSlug, resolveMenuTarget } from '../lib/tenant';
```

```tsx
  const params = useParams<{ merchantSlug?: string; branchSlug?: string; tableNumber?: string }>();
  const [searchParams] = useSearchParams();
  const signature = searchParams.get('signature') || undefined;
  const isQrDemo = searchParams.get('demo') === 'qr';
  const [showQrOverlay, setShowQrOverlay] = useState(isQrDemo);

  /**
   * The host label wins over the path parameter. On a tenant host the canonical
   * URL is /menu/{branchSlug}/{tableNumber}, which the router matches against
   * /menu/:merchantSlug/:tableNumber — so without this the page would ask the
   * backend to resolve a merchant named after the branch.
   */
  const hostSlug = currentTenantSlug();
  const target = resolveMenuTarget(params, hostSlug);

  const {
    data: resolution,
    isLoading: resolving,
    error: resolveError,
    refetch: refetchResolution,
  } = usePublicMenuResolution(target?.merchantSlug, target?.branchSlug, target?.tableNumber, signature);
```

Then, immediately before the existing loading/error rendering, add the mismatch notice:

```tsx
  if (target?.hostMismatch) {
    // The link named one restaurant and the address bar another. Serving the host
    // silently would show the guest a different menu than the link promised, and
    // the backend will 403 the request anyway.
    return (
      <div className="flex min-h-screen items-center justify-center p-6 text-center">
        <div className="max-w-sm space-y-2">
          <h1 className="text-lg font-semibold">This link is for a different restaurant</h1>
          <p className="text-sm text-muted">
            Please scan the QR code on your table again.
          </p>
        </div>
      </div>
    );
  }
```

Verify the `text-muted` utility exists in `src/index.css`; if not, use `text-slate-500`.

- [ ] **Step 7: Preserve the `Host` header through the dev proxy**

This is the step that makes local subdomain development actually work. In `vite.config.ts`:

```ts
    server: {
      // Tenant subdomains resolve against a public wildcard that points at
      // loopback, so sunrise.localtest.me:3000 works with no /etc/hosts editing.
      // Vite 6 rejects Host headers it does not recognise, hence allowedHosts.
      allowedHosts: ['localhost', '.localtest.me'],
      hmr: process.env.DISABLE_HMR !== 'true',
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
      proxy: {
        '/api': {
          target: process.env.VITE_PROXY_TARGET || 'http://localhost:8081',
          // MUST stay false. changeOrigin:true rewrites the Host header to the
          // proxy target, which erases the tenant label before the gateway ever
          // sees it — tenant resolution would silently never fire in development.
          changeOrigin: false,
        },
        '/ws': {
          target: process.env.VITE_PROXY_TARGET || 'http://localhost:8081',
          ws: true,
          changeOrigin: false,
        },
      },
    },
```

- [ ] **Step 8: Verify the frontend compiles and builds**

```bash
npm run lint && npm run build
```

Expected: `tsc --noEmit` clean, then a successful production build.

- [ ] **Step 9: Commit**

```bash
git add src/lib/tenant.ts src/lib/tenant.test.ts src/pages/CustomerMenuPage.tsx vite.config.ts package.json
git commit -m "feat(tenancy): resolve the merchant from the host on the client

changeOrigin must stay false on the dev proxy: rewriting Host would erase the
tenant label before the gateway sees it."
```

---

### Task 14: Kubernetes wildcard ingress, and the developer instructions

**Files:**
- Modify: `backend/k8s/proxy-ingress.yml`
- Modify: `backend/README.md`
- Modify: `docs/superpowers/plans/2026-08-18-multi-tenant-subdomains-and-qr.md` (tick the boxes)

**Dependency, stated plainly:** `backend/k8s/deployment.yml` describes only four of nine services. The `environment:`-instead-of-`env:` bug was fixed in `2739dcb`, so what is there now works, but the manifests are incomplete. **The ingress change below is correct and cannot be exercised until the manifests deploy the services they claim to.** Completing them is out of scope for this plan; do not treat a green `kubectl apply` on the ingress as evidence the routing works.

- [ ] **Step 1: Add the wildcard host rule**

Replace `backend/k8s/proxy-ingress.yml`:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: qrserve-ingress
  namespace: default
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-connect-timeout: "15"
    # Long read timeout for the STOMP WebSocket on /ws/**; the default would cut
    # an idle kitchen display's connection.
    nginx.ingress.kubernetes.io/proxy-read-timeout: "600"
    # The gateway derives the tenant from the Host header, so it must arrive
    # unmodified. nginx-ingress preserves it by default; this makes the dependency
    # explicit so a future edit does not quietly break tenant resolution.
    nginx.ingress.kubernetes.io/upstream-vhost: "$host"
spec:
  ingressClassName: nginx
  tls:
    # One wildcard certificate covers every tenant. Single-label only: a
    # certificate for *.qrserve.safaricom.et does NOT cover
    # a.b.qrserve.safaricom.et, which is why branches are path segments rather
    # than second-level subdomains.
    - hosts:
        - "*.qrserve.safaricom.et"
        - "qrserve.safaricom.et"
      secretName: qrserve-wildcard-tls
  rules:
    # Every tenant subdomain, plus the reserved admin. host. All of them route to
    # the same gateway; the gateway decides which tenant the request belongs to.
    - host: "*.qrserve.safaricom.et"
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: api-gateway-service
                port:
                  number: 8081
    # The apex, for the landing page and the path-based demo route.
    - host: "qrserve.safaricom.et"
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: api-gateway-service
                port:
                  number: 8081
```

- [ ] **Step 2: Validate the manifest**

```bash
cd backend && python -c "
import yaml
list(yaml.safe_load_all(open('k8s/proxy-ingress.yml', encoding='utf-8')))
print('yaml ok')
"
```

If a cluster is reachable, also:

```bash
kubectl apply --dry-run=client -f backend/k8s/proxy-ingress.yml
```

- [ ] **Step 3: Document how to run this locally**

Append to `backend/README.md`:

```markdown
## Tenant subdomains in local development

Each merchant is served at `{merchantSlug}.{PUBLIC_BASE_DOMAIN}`. Subdomains do
not resolve against `localhost`, and asking every developer to edit
`/etc/hosts` per tenant is friction that gets bypassed — which leaves the
subdomain code path exercised only in staging.

Instead use a public wildcard that resolves to loopback. `localtest.me` and
`sslip.io` both do this, with no setup at all:

    PUBLIC_BASE_DOMAIN=localtest.me:3000
    PUBLIC_URL_SCHEME=http

Then `http://sunrise.localtest.me:3000/menu/main/1` reaches the Vite dev server,
which proxies to the gateway with the `Host` header intact, and the real tenant
resolution path runs locally.

Two settings make this work and are easy to break:

- `vite.config.ts` sets `changeOrigin: false` on both proxies. With
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

### Rotating the QR signing secret

QR codes are printed onto physical table stands, so rotating
`QR_SIGNATURE_SECRET` must not invalidate them all at once:

1. Set `QR_SIGNATURE_SECRET_PREVIOUS` to the current value.
2. Set `QR_SIGNATURE_SECRET` to the new value.
3. Deploy. Existing printed codes still validate; new ones are signed with the
   new secret.
4. Reprint at leisure, then clear `QR_SIGNATURE_SECRET_PREVIOUS`.

The signing key is derived per tenant as `HMAC-SHA256(masterSecret, merchantId)`,
so a compromise is confined to one restaurant's codes rather than the whole
platform's.
```

- [ ] **Step 4: Run the whole backend and frontend build**

```bash
cd backend && ./gradlew build
```

```bash
npm run lint && npm run test:unit && npm run build
```

Expected: both green. If `./gradlew build` fails on a module untouched by this
plan, report it rather than fixing it here.

- [ ] **Step 5: Commit**

```bash
git add backend/k8s/proxy-ingress.yml backend/README.md docs/superpowers/plans/2026-08-18-multi-tenant-subdomains-and-qr.md
git commit -m "docs(tenancy): wildcard ingress, local subdomain setup, secret rotation"
```

---

## Verification summary

Run all of this before claiming the plan is done. Report actual output, not expectations.

```bash
cd backend && ./gradlew build
```

```bash
npm run lint && npm run test:unit && npm run build
```

```bash
# No hardcoded public host survives anywhere.
cd backend && grep -rn "qrserve\.com" --include=*.java . | grep -v /build/ | grep -v /bin/
```

```bash
# All three copies of the broken slug expression are gone.
cd backend && grep -rn 'replaceAll("\[\^a-z0-9\]"' --include=*.java . | grep -v /build/ | grep -v /bin/
```

```bash
# The base domain reached exactly the 8 component-scanning services.
cd backend && grep -l "public-base-domain" */src/main/resources/application.yml | wc -l
```

Both greps must print nothing; the count must be `8`.

**Non-vacuity checks** — each of these protections is invisible when working and
serious when absent, so each must be shown to be doing work. Break it, watch the
named test fail, restore it:

| Break this | Expect this to fail |
|---|---|
| the two `headers.remove(...)` in `TenantResolutionGlobalFilter.withoutTenantHeaders` | `stripsForgedHeaderOnNonTenantHost` |
| the mismatch condition in `TenantContextFilter` (`if (false)`) | `mismatchIsForbidden` |
| the `finally` block in `TenantContextFilter` | `clearsThreadLocalOnException` |
| the `hostTenant` check in `PublicMenuResolutionService` | `hostTenantMustMatchResolvedMerchant` |

---

## What this plan deliberately does not do

Stated so a reviewer does not assume they were forgotten:

- **Subscriptions and entitlements (subsystem C).** Its own spec. Plans, the
  limits the landing page advertises (5/50/unlimited tables, 1/3/unlimited
  branches, kitchen display as Premium), enforcement points, and lifecycle. No
  subscription model exists in the code today; those tiers are marketing copy.
- **The merchant slug alias table.** Deferred, with the guard that slug changes
  are rejected (Task 6). Add it the first time a real tenant needs a rename.
- **Making the QR signature mandatory.** Task 7 makes printed codes carry a
  signature for the first time, so requiring it becomes *possible* — but doing so
  would break the path-based demo route the spec says to keep. It is a product
  decision, not a mechanical one. Tracked as an open item.
- **Inter-service identity.** `PATCH /api/tables/{id}/status` still fails for
  anonymous guest orders, so table occupancy never updates. Tenant resolution does
  not fix it: the gateway header is absent on service-to-service calls. Open item
  3 in the spec.
- **Completing `backend/k8s/deployment.yml`.** Four of nine services are
  described. The ingress in Task 14 is correct but cannot be exercised until the
  rest are.
- **Flyway baselines.** `spring.flyway.enabled` is `false` and `ddl-auto` is
  `update`, so Task 5's constraint change ships as a manual SQL file. Tracked as
  item 7.5 of the remediation plan.

---

## Open items carried from the spec

1. **Final domain.** `qrserve.safaricom.et` is assumed. If the product gets its
   own domain, the only changes are `PUBLIC_BASE_DOMAIN` and the certificate.
2. **`SUPER_ADMIN` through a tenant host.** Task 11 implements the spec as
   written: a super-admin asserts no tenant, on any host. If they turn out to need
   to operate *through* a tenant host to debug one restaurant, that is an
   exemption to the mismatch rule and should be designed rather than patched in.
3. **Whether blocking slug renames is commercially acceptable.** It is the right
   engineering call pre-launch, but a rebranding restaurant has to be told "no"
   until the alias table exists.
