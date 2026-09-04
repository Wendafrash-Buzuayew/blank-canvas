# Menu URL & Access Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every merchant branch its own independently-editable, publishable
menu, reachable at a new path-based public URL (`/m/{merchant-slug}[/{branch-slug}]`)
that scales to ~30,000 merchants on one shared domain — without touching the
existing subdomain+table-scoped system, which stays parked for phase 2.

**Architecture:** A new `MenuEntity` (menu-service) becomes the 1:1 container
for each branch's categories/products and carries DRAFT/PUBLISHED status. A
new `Branch.isPrimary` flag (merchant-service) resolves the merchant-only
short link to a specific branch. A new public URL family, a new QR signature
scope, and a new gateway path-resolution mode are added alongside the
existing subdomain/table code — never modifying it. The frontend gets a new,
lean, read-only menu-viewing route (no cart, no ordering — that stays parked
with `CustomerMenuPage`).

**Tech Stack:** Spring Boot 4 / Java, JPA with `ddl-auto=update` (not
Flyway — see Global Constraints), JUnit 5 + Mockito (plain mocks, no Spring
context in unit tests), React 19 / TypeScript / React Router 7 / TanStack
Query.

**Spec:** `docs/superpowers/specs/2026-09-04-menu-url-access-redesign-design.md`

## Global Constraints

- **Additive only.** Never modify: `PublicMenuUrl`, `PublicMenuController`,
  `PublicMenuResolutionService`, `TenantResolutionGlobalFilter`'s existing
  host-based logic, `QrSignatureService`'s existing 3-arg methods,
  `CustomerMenuPage.tsx`, or any test covering them. All are exercised by
  the existing suite; that suite must still pass unmodified at the end of
  this plan.
- **No Flyway.** This codebase uses `spring.jpa.hibernate.ddl-auto=update`
  (new tables/columns are created automatically on boot) plus hand-written
  SQL under `backend/<service>/src/main/resources/db/manual/NNN-<slug>.sql`
  for anything `ddl-auto` cannot express (partial indexes, uniqueness
  involving nullable columns) — run manually against the database after
  deploy, never by the application itself (`spring.flyway.enabled=false`
  throughout). See `backend/merchant-service/.../db/manual/002-...sql` for
  the house style (every statement `IF NOT EXISTS`, a comment explaining
  *why* `ddl-auto` can't do it).
- **`PUBLIC_MENU_DOMAIN` is a new, separate config value** from the existing
  `PUBLIC_BASE_DOMAIN` (used by the parked subdomain scheme). Never conflate
  them.
- **No cross-service JPA/FK.** menu-service and merchant-service have
  separate databases (`qrserve_menu`, `qrserve_merchant`). A `branchId` on
  `MenuEntity` is a plain `Long`, unenforced across the service boundary —
  same pattern as `OrderEntity.branchId`, `TableEntity`, etc. Cross-service
  reads use `RestTemplate` + `Map<String,Object>` parsing (see
  `QrGeneratorService.fetchBranch` for the house pattern), never a shared
  entity.
- **Test style:** plain `mock()`/`when()` from Mockito, direct
  constructor/instantiation, JUnit 5 assertions — no `@SpringBootTest`, no
  MockMvc, unless a task explicitly says so. This matches
  `ProductControllerTest.java` (house style) and is required so
  `verify-backend`-style compilation works without a running Spring context.
- **Every new `@PreAuthorize`** on a mutating endpoint follows the existing
  `hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')` convention
  used throughout `menu-service`'s controllers.

---

### Task 1: `MenuEntity` and `MenuRepository`

**Files:**
- Create: `backend/menu-service/src/main/java/com/qrserve/menu/entity/MenuEntity.java`
- Create: `backend/menu-service/src/main/java/com/qrserve/menu/repository/MenuRepository.java`
- Test: `backend/menu-service/src/test/java/com/qrserve/menu/entity/MenuEntityTest.java`

**Interfaces:**
- Produces: `MenuEntity` with fields `id (UUID)`, `branchId (Long)`,
  `merchantId (UUID)`, `status (MenuEntity.Status)`, `publishedAt (LocalDateTime, nullable)`,
  `createdAt`, `updatedAt`. `MenuEntity.Status` enum: `DRAFT`, `PUBLISHED`.
  `MenuRepository.findByBranchId(Long branchId): Optional<MenuEntity>`.

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.menu.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A menu is created DRAFT and carries no publishedAt until an explicit
 * publish — this is the invariant the whole "only Published menus are
 * public" contract rests on (HLD 6.2).
 */
class MenuEntityTest {

    @Test
    void newMenuDefaultsToDraftWithNoPublishedAt() {
        MenuEntity menu = MenuEntity.builder()
                .branchId(5L)
                .merchantId(UUID.randomUUID())
                .build();
        menu.prePersist();

        assertEquals(MenuEntity.Status.DRAFT, menu.getStatus());
        assertNull(menu.getPublishedAt());
        assertNotNull(menu.getCreatedAt());
    }

    @Test
    void explicitStatusIsNotOverwrittenByPrePersist() {
        MenuEntity menu = MenuEntity.builder()
                .branchId(5L)
                .merchantId(UUID.randomUUID())
                .status(MenuEntity.Status.PUBLISHED)
                .build();
        menu.prePersist();

        assertEquals(MenuEntity.Status.PUBLISHED, menu.getStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Compile error expected — `MenuEntity` does not exist yet. Follow the
`verify-backend` skill (`.claude/skills/verify-backend/SKILL.md`) to compile
and run: `javac` against the resolved classpath will report
`cannot find symbol: class MenuEntity`.

- [ ] **Step 3: Write `MenuEntity`**

```java
package com.qrserve.menu.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One menu per branch (1:1), not a separately-named multi-variant concept —
 * different branches can carry different items/prices, but a branch does not
 * host multiple concurrent named menus. See
 * docs/superpowers/specs/2026-09-04-menu-url-access-redesign-design.md.
 */
@Entity
@Table(name = "menus")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuEntity {

    public enum Status { DRAFT, PUBLISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    // Denormalized from the owning branch's merchant, so tenant checks on
    // Category/Product (which will carry this same denormalized field) do
    // not require a join through Branch (a different service's database).
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (status == null) status = Status.DRAFT;
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 4: Write `MenuRepository`**

```java
package com.qrserve.menu.repository;

import com.qrserve.menu.entity.MenuEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuRepository extends JpaRepository<MenuEntity, UUID> {
    Optional<MenuEntity> findByBranchId(Long branchId);
}
```

- [ ] **Step 5: Run test to verify it passes**

Use `.claude/skills/verify-backend/SKILL.md`'s recipe: compile
`shared:common`, `shared:security`, `shared:exceptions` (menu-service's
dependencies) then `menu-service` main + test sources with `-processorpath`
pointing at Lombok, then run `MenuEntityTest` via `JUnitRunner`. Expect
`2 tests successful`.

- [ ] **Step 6: Commit**

```bash
git add backend/menu-service/src/main/java/com/qrserve/menu/entity/MenuEntity.java \
        backend/menu-service/src/main/java/com/qrserve/menu/repository/MenuRepository.java \
        backend/menu-service/src/test/java/com/qrserve/menu/entity/MenuEntityTest.java
git commit -m "feat(menu-service): add MenuEntity, one menu per branch"
```

---

### Task 2: `Branch.isPrimary`

**Files:**
- Modify: `backend/merchant-service/src/main/java/com/qrserve/merchant/entity/BranchEntity.java`
- Modify: `backend/merchant-service/src/main/java/com/qrserve/merchant/service/BranchService.java`
- Modify: `backend/merchant-service/src/main/java/com/qrserve/merchant/controller/BranchController.java`
- Create: `backend/merchant-service/src/main/resources/db/manual/003-branch-primary-flag.sql`
- Test: `backend/merchant-service/src/test/java/com/qrserve/merchant/service/BranchServiceTest.java`

**Interfaces:**
- Produces: `BranchEntity.isPrimary(): boolean`.
  `BranchService.setPrimaryBranch(UUID merchantId, Long branchId): BranchEntity`
  — throws `ResourceNotFoundException` if the branch doesn't belong to
  `merchantId`.
- Consumes: existing `BranchRepository.findByMerchantId`,
  `findByMerchantIdAndSlug` (Task 1 context, `BranchRepository.java` read
  during planning).

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.CreateBranchRequest;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.repository.BranchRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BranchServiceTest {

    private BranchRepository repository;
    private BranchService service;
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(BranchRepository.class);
        service = new BranchService(repository);
    }

    @Test
    void firstBranchForAMerchantIsAutoPrimary() {
        when(repository.findByMerchantIdAndSlug(MERCHANT, "main")).thenReturn(Optional.empty());
        when(repository.findByMerchantId(MERCHANT)).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BranchEntity created = service.createBranch(CreateBranchRequest.builder()
                .merchantId(MERCHANT).name("Main").slug("main")
                .phone("0700000000").address("Addis Ababa").build());

        assertTrue(created.isPrimary(), "the merchant's first branch has nothing to be secondary to");
    }

    @Test
    void secondBranchIsNotAutoPrimary() {
        when(repository.findByMerchantIdAndSlug(MERCHANT, "annex")).thenReturn(Optional.empty());
        when(repository.findByMerchantId(MERCHANT)).thenReturn(
                List.of(BranchEntity.builder().id(1L).merchantId(MERCHANT).isPrimary(true).build()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BranchEntity created = service.createBranch(CreateBranchRequest.builder()
                .merchantId(MERCHANT).name("Annex").slug("annex")
                .phone("0700000001").address("Bole").build());

        assertFalse(created.isPrimary());
    }

    @Test
    void setPrimaryBranchSwapsTheFlag() {
        BranchEntity oldPrimary = BranchEntity.builder().id(1L).merchantId(MERCHANT).isPrimary(true).build();
        BranchEntity newPrimary = BranchEntity.builder().id(2L).merchantId(MERCHANT).isPrimary(false).build();
        when(repository.findByMerchantId(MERCHANT)).thenReturn(List.of(oldPrimary, newPrimary));
        when(repository.findById(2L)).thenReturn(Optional.of(newPrimary));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BranchEntity result = service.setPrimaryBranch(MERCHANT, 2L);

        assertTrue(result.isPrimary());
        assertFalse(oldPrimary.isPrimary(), "exactly one primary branch per merchant");
        verify(repository, times(2)).save(any());
    }

    @Test
    void setPrimaryBranchRefusesABranchFromAnotherMerchant() {
        BranchEntity foreign = BranchEntity.builder().id(9L).merchantId(UUID.randomUUID()).build();
        when(repository.findById(9L)).thenReturn(Optional.of(foreign));

        assertThrows(ResourceNotFoundException.class, () -> service.setPrimaryBranch(MERCHANT, 9L));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Expect compile failures: `isPrimary()` / `.isPrimary(true)` builder field
and `setPrimaryBranch` don't exist yet.

- [ ] **Step 3: Add `isPrimary` to `BranchEntity`**

Add this field to the existing class (after `address`):

```java
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean isPrimary = false;
```

- [ ] **Step 4: Update `BranchService`**

Replace the whole file with:

```java
package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.CreateBranchRequest;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.repository.BranchRepository;
import com.qrserve.shared.common.Slugs;
import com.qrserve.shared.exceptions.BusinessException;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    @Transactional
    public BranchEntity createBranch(CreateBranchRequest request) {
        String slug = Slugs.toPathSlug(request.getSlug());

        if (branchRepository.findByMerchantIdAndSlug(request.getMerchantId(), slug).isPresent()) {
            throw new BusinessException(
                    "A branch with the slug '" + slug + "' already exists for this merchant");
        }

        // The first branch a merchant creates has nothing to be secondary to,
        // so it becomes primary automatically. See setPrimaryBranch for how a
        // merchant reassigns it later.
        boolean isFirstBranch = branchRepository.findByMerchantId(request.getMerchantId()).isEmpty();

        BranchEntity branch = BranchEntity.builder()
                .merchantId(request.getMerchantId())
                .name(request.getName())
                .slug(slug)
                .phone(request.getPhone())
                .address(request.getAddress() != null ? request.getAddress() : "Main Address")
                .isPrimary(isFirstBranch)
                .build();
        return branchRepository.save(branch);
    }

    /**
     * Reassigns which branch is primary for a merchant — the branch
     * {@code /m/{merchant-slug}} redirects to. Exactly one primary branch per
     * merchant at all times; a database partial unique index
     * (db/manual/003-branch-primary-flag.sql) backs this up against a race
     * between two concurrent calls.
     */
    @Transactional
    public BranchEntity setPrimaryBranch(UUID merchantId, Long branchId) {
        BranchEntity target = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + branchId));
        if (!merchantId.equals(target.getMerchantId())) {
            throw new ResourceNotFoundException("Branch not found with ID: " + branchId);
        }

        for (BranchEntity branch : branchRepository.findByMerchantId(merchantId)) {
            if (branch.isPrimary() && !branch.getId().equals(branchId)) {
                branch.setPrimary(false);
                branchRepository.save(branch);
            }
        }
        target.setPrimary(true);
        return branchRepository.save(target);
    }

    public BranchEntity getBranchByMerchantAndSlug(UUID merchantId, String slug) {
        return branchRepository.findByMerchantIdAndSlug(merchantId, slug)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for merchant " + merchantId + " and slug: " + slug));
    }

    public List<BranchEntity> getBranchesByMerchant(UUID merchantId) {
        return branchRepository.findByMerchantId(merchantId);
    }

    public BranchEntity getBranch(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + id));
    }
}
```

- [ ] **Step 5: Add the endpoint to `BranchController`**

Add this method (mirrors the existing `getBranch` tenant-check pattern):

```java
    @PatchMapping("/{id}/primary")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('MERCHANT_OWNER')")
    @Operation(summary = "Designate a branch as the merchant's primary branch")
    public ResponseEntity<BranchEntity> setPrimaryBranch(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID merchantId = principal.getRole() == UserRole.SUPER_ADMIN
                ? branchService.getBranch(id).getMerchantId()
                : principal.getMerchantId();
        return ResponseEntity.ok(branchService.setPrimaryBranch(merchantId, id));
    }
```

- [ ] **Step 6: Write the manual SQL migration**

```sql
-- Branch primary flag: exactly one primary branch per merchant.
--
-- Run against qrserve_merchant AFTER deploying the code change (the column
-- is added by spring.jpa.hibernate.ddl-auto=update on startup; this index
-- is not — see backend/merchant-service/.../db/manual/002-...sql for why).
--
-- Safe on a populated database: additive, IF NOT EXISTS. Existing branches
-- default is_primary=false; run the app-level backfill (Task 12) to set the
-- first branch per merchant to true before relying on this index in
-- production traffic.
CREATE UNIQUE INDEX IF NOT EXISTS branches_one_primary_per_merchant
    ON branches (merchant_id)
    WHERE is_primary;
```

- [ ] **Step 7: Run tests to verify they pass**

Compile `shared:common`, `shared:exceptions`, `shared:security`, then
merchant-service main + test sources (per `verify-backend` skill), run
`BranchServiceTest` via `JUnitRunner`. Expect `4 tests successful`.

- [ ] **Step 8: Commit**

```bash
git add backend/merchant-service/src/main/java/com/qrserve/merchant/entity/BranchEntity.java \
        backend/merchant-service/src/main/java/com/qrserve/merchant/service/BranchService.java \
        backend/merchant-service/src/main/java/com/qrserve/merchant/controller/BranchController.java \
        backend/merchant-service/src/main/resources/db/manual/003-branch-primary-flag.sql \
        backend/merchant-service/src/test/java/com/qrserve/merchant/service/BranchServiceTest.java
git commit -m "feat(merchant-service): add Branch.isPrimary and set-primary endpoint"
```

---

### Task 3: `Category` becomes menu-scoped

**Files:**
- Modify: `backend/menu-service/src/main/java/com/qrserve/menu/entity/CategoryEntity.java`
- Modify: `backend/menu-service/src/main/java/com/qrserve/menu/dto/CreateCategoryRequest.java`
- Modify: `backend/menu-service/src/main/java/com/qrserve/menu/repository/CategoryRepository.java`
- Modify: `backend/menu-service/src/main/java/com/qrserve/menu/service/MenuService.java`
- Test: `backend/menu-service/src/test/java/com/qrserve/menu/service/MenuServiceCategoryTest.java`

**Interfaces:**
- Consumes: `MenuRepository.findByBranchId` (Task 1).
- Produces: `CategoryEntity.getMenuId(): UUID`.
  `MenuService.getOrCreateMenuForBranch(Long branchId, UUID merchantId): MenuEntity`
  — idempotent; creates a DRAFT `MenuEntity` the first time a branch is
  referenced, since "a branch without a menu is a branch nobody can publish."
  `CategoryRepository.findByMenuIdOrderByDisplayOrderAsc(UUID menuId): List<CategoryEntity>`.

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.menu.service;

import com.qrserve.menu.dto.CreateCategoryRequest;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MenuServiceCategoryTest {

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private MenuRepository menuRepository;
    private MenuService service;
    private static final Long BRANCH = 5L;
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        menuRepository = mock(MenuRepository.class);
        service = new MenuService(categoryRepository, productRepository, menuRepository);
    }

    @Test
    void getOrCreateMenuForBranchCreatesADraftMenuOnFirstUse() {
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.empty());
        when(menuRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuEntity menu = service.getOrCreateMenuForBranch(BRANCH, MERCHANT);

        assertEquals(BRANCH, menu.getBranchId());
        assertEquals(MERCHANT, menu.getMerchantId());
        assertEquals(MenuEntity.Status.DRAFT, menu.getStatus());
    }

    @Test
    void getOrCreateMenuForBranchIsIdempotent() {
        MenuEntity existing = MenuEntity.builder().branchId(BRANCH).merchantId(MERCHANT)
                .status(MenuEntity.Status.PUBLISHED).build();
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.of(existing));

        MenuEntity menu = service.getOrCreateMenuForBranch(BRANCH, MERCHANT);

        assertSame(existing, menu);
        verify(menuRepository, never()).save(any());
    }

    @Test
    void createCategoryAttachesItToTheBranchsMenu() {
        MenuEntity menu = MenuEntity.builder().id(UUID.randomUUID()).branchId(BRANCH).merchantId(MERCHANT)
                .status(MenuEntity.Status.DRAFT).build();
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.of(menu));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryEntity category = service.createCategory(CreateCategoryRequest.builder()
                .branchId(BRANCH).merchantId(MERCHANT).name("Drinks").build());

        assertEquals(menu.getId(), category.getMenuId());
        assertEquals(MERCHANT, category.getMerchantId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Compile failures expected: `CreateCategoryRequest.builder().branchId(...)`,
`MenuService` constructor arity, `CategoryEntity.getMenuId()`,
`MenuService.getOrCreateMenuForBranch` all don't exist yet.

- [ ] **Step 3: Add `menuId` to `CategoryEntity`**

Add this field (after `merchantId`) and switch the class to also carry
`@Builder`-compatible construction (it already has `@Builder`):

```java
    // The real scoping key going forward — a branch's menu owns its own
    // categories. merchantId is kept, denormalized from the owning Menu, so
    // existing tenant-check code (CategoryController.resolveScope etc.)
    // keeps working unchanged.
    @Column(name = "menu_id", nullable = false)
    private UUID menuId;
```

- [ ] **Step 4: Add `branchId` to `CreateCategoryRequest`**

```java
package com.qrserve.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CreateCategoryRequest {
    @NotNull
    private UUID merchantId;

    @NotNull
    private Long branchId;

    @NotBlank
    private String name;

    private Integer displayOrder;
}
```

(`@Builder` is added because the test constructs this DTO with the builder
pattern used throughout this codebase's other request DTOs and tests; the
existing no-arg `@Data` class had no builder because nothing needed one
before.)

- [ ] **Step 5: Add the repository method**

```java
    List<CategoryEntity> findByMenuIdOrderByDisplayOrderAsc(UUID menuId);
```

(add to `CategoryRepository`, alongside the existing
`findByMerchantIdOrderByDisplayOrderAsc` — which stays, still used by
nothing after this task but left in place; nothing in this plan removes it,
consistent with "additive, don't break what's not being redesigned.")

- [ ] **Step 6: Update `MenuService`**

Add the `MenuRepository` dependency and these two methods (the constructor
gains a third parameter — `@RequiredArgsConstructor` regenerates it from the
field list, so just add the field):

```java
    private final MenuRepository menuRepository;
```

```java
    /**
     * A branch without a menu is a branch nobody can publish, so this is
     * called both when a category is first added to a branch and by the
     * publish endpoint (Task 5) — idempotent either way.
     */
    @Transactional
    public MenuEntity getOrCreateMenuForBranch(Long branchId, UUID merchantId) {
        return menuRepository.findByBranchId(branchId)
                .orElseGet(() -> menuRepository.save(MenuEntity.builder()
                        .branchId(branchId)
                        .merchantId(merchantId)
                        .build()));
    }
```

Replace the existing `createCategory` method body:

```java
    @Transactional
    @CacheEvict(value = "menus", key = "#request.merchantId")
    public CategoryEntity createCategory(CreateCategoryRequest request) {
        MenuEntity menu = getOrCreateMenuForBranch(request.getBranchId(), request.getMerchantId());
        CategoryEntity category = CategoryEntity.builder()
                .menuId(menu.getId())
                .merchantId(request.getMerchantId())
                .name(request.getName())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();
        return categoryRepository.save(category);
    }
```

- [ ] **Step 7: Run tests to verify they pass**

Recompile menu-service (main + test) and run
`MenuServiceCategoryTest` via `JUnitRunner`. Expect `3 tests successful`.
Also re-run `CategoryControllerTest`/`ProductControllerTest` (existing) to
confirm this change did not disturb them — they construct `CategoryEntity`/
`ProductEntity` via their own builders and don't touch `createCategory`, so
they should be unaffected; verifying this is the point of the run.

- [ ] **Step 8: Commit**

```bash
git add backend/menu-service/src/main/java/com/qrserve/menu/entity/CategoryEntity.java \
        backend/menu-service/src/main/java/com/qrserve/menu/dto/CreateCategoryRequest.java \
        backend/menu-service/src/main/java/com/qrserve/menu/repository/CategoryRepository.java \
        backend/menu-service/src/main/java/com/qrserve/menu/service/MenuService.java \
        backend/menu-service/src/test/java/com/qrserve/menu/service/MenuServiceCategoryTest.java
git commit -m "feat(menu-service): categories become menu-scoped (one menu per branch)"
```

---

### Task 4: `Product` inherits `menuId` from its category

**Files:**
- Modify: `backend/menu-service/src/main/java/com/qrserve/menu/entity/ProductEntity.java`
- Modify: `backend/menu-service/src/main/java/com/qrserve/menu/repository/ProductRepository.java`
- Modify: `backend/menu-service/src/main/java/com/qrserve/menu/service/MenuService.java`
- Test: `backend/menu-service/src/test/java/com/qrserve/menu/service/MenuServiceProductTest.java`

**Interfaces:**
- Consumes: `CategoryEntity.getMenuId()` (Task 3).
- Produces: `ProductEntity.getMenuId(): UUID`.
  `ProductRepository.findByMenuId(UUID menuId): List<ProductEntity>`.

No DTO change needed here — `CreateProductRequest` already carries only
`categoryId`; `menuId` is derived server-side from the category, exactly the
way `merchantId` already is today.

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.menu.service;

import com.qrserve.menu.dto.CreateProductRequest;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MenuServiceProductTest {

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private MenuService service;
    private static final Long CATEGORY_ID = 3L;
    private static final UUID MENU_ID = UUID.randomUUID();
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        service = new MenuService(categoryRepository, productRepository, mock(MenuRepository.class));
    }

    @Test
    void createProductInheritsMenuIdFromItsCategory() {
        CategoryEntity category = CategoryEntity.builder()
                .id(CATEGORY_ID).menuId(MENU_ID).merchantId(MERCHANT).name("Drinks").build();
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductEntity product = service.createProduct(CreateProductRequest.builder()
                .categoryId(CATEGORY_ID).name("Espresso").price(BigDecimal.TEN).build());

        assertEquals(MENU_ID, product.getMenuId());
        assertEquals(MERCHANT, product.getMerchantId());
    }
}
```

(`CreateProductRequest` gains `@Builder` for the same reason as
`CreateCategoryRequest` in Task 3 — add `@Builder` to its class declaration.)

- [ ] **Step 2: Run test to verify it fails**

Compile failure: `ProductEntity.getMenuId()` doesn't exist, `CreateProductRequest.builder()` doesn't exist yet.

- [ ] **Step 3: Add `menuId` to `ProductEntity`**

```java
    @Column(name = "menu_id", nullable = false)
    private UUID menuId;
```

- [ ] **Step 4: Add `@Builder` to `CreateProductRequest`**

Add `import lombok.Builder;` and `@Builder` above `@Data` on the class.

- [ ] **Step 5: Add the repository method**

```java
    List<ProductEntity> findByMenuId(UUID menuId);
```

(add to `ProductRepository`)

- [ ] **Step 6: Update `MenuService.createProduct`**

```java
    @Transactional
    @CacheEvict(value = "menus", key = "#result.merchantId")
    public ProductEntity createProduct(CreateProductRequest request) {
        CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));

        ProductEntity product = ProductEntity.builder()
                .menuId(category.getMenuId())
                .merchantId(category.getMerchantId())
                .categoryId(category.getId())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .image(request.getImage())
                .available(true)
                .preparationTime(request.getPreparationTime() != null ? request.getPreparationTime() : 15)
                .build();

        return productRepository.save(product);
    }
```

- [ ] **Step 7: Run tests to verify they pass**

Recompile and run `MenuServiceProductTest` via `JUnitRunner`. Expect
`1 test successful`. Re-run `ProductControllerTest` too, for the same
regression-guard reason as Task 3 Step 7.

- [ ] **Step 8: Commit**

```bash
git add backend/menu-service/src/main/java/com/qrserve/menu/entity/ProductEntity.java \
        backend/menu-service/src/main/java/com/qrserve/menu/dto/CreateProductRequest.java \
        backend/menu-service/src/main/java/com/qrserve/menu/repository/ProductRepository.java \
        backend/menu-service/src/main/java/com/qrserve/menu/service/MenuService.java \
        backend/menu-service/src/test/java/com/qrserve/menu/service/MenuServiceProductTest.java
git commit -m "feat(menu-service): products inherit menuId from their category"
```

---

### Task 5: `getFullMenuByMenuId(menuId)` and `publish(branchId)`

**Files:**
- Modify: `backend/menu-service/src/main/java/com/qrserve/menu/service/MenuService.java`
- Test: `backend/menu-service/src/test/java/com/qrserve/menu/service/MenuPublishTest.java`

**Interfaces:**
- Consumes: `MenuRepository`, `CategoryRepository.findByMenuIdOrderByDisplayOrderAsc`
  (Task 3), `ProductRepository.findByMenuId` (Task 4).
- Produces: `MenuService.getFullMenuByMenuId(UUID menuId): MenuResponse` —
  a new, separately-named method, not an overload of the existing
  `getFullMenu(UUID merchantId)`: both parameters are `UUID`, so an overload
  would have an identical erased signature and cannot coexist with it in
  Java. `getFullMenu(UUID merchantId)` is untouched. `MenuService.publish(Long branchId): MenuEntity`
  — throws `ResourceNotFoundException` if the branch has no menu yet (must
  create it first by adding at least one category — publishing an empty menu
  is a merchant mistake worth surfacing, not silently allowing).

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.menu.service;

import com.qrserve.menu.dto.MenuResponse;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.ProductRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MenuPublishTest {

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private MenuRepository menuRepository;
    private MenuService service;
    private static final Long BRANCH = 5L;
    private static final UUID MENU_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        menuRepository = mock(MenuRepository.class);
        service = new MenuService(categoryRepository, productRepository, menuRepository);
    }

    @Test
    void getFullMenuByMenuIdReturnsItsCategoriesAndProducts() {
        CategoryEntity drinks = CategoryEntity.builder().id(1L).menuId(MENU_ID).name("Drinks").build();
        when(categoryRepository.findByMenuIdOrderByDisplayOrderAsc(MENU_ID)).thenReturn(List.of(drinks));
        when(productRepository.findByCategoryId(1L)).thenReturn(List.of(
                ProductEntity.builder().id(1L).menuId(MENU_ID).categoryId(1L).name("Espresso")
                        .price(BigDecimal.TEN).available(true).preparationTime(3).build()));

        MenuResponse response = service.getFullMenuByMenuId(MENU_ID);

        assertEquals(1, response.getCategories().size());
        assertEquals("Espresso", response.getCategories().get(0).getItems().get(0).getName());
    }

    @Test
    void publishMarksTheMenuPublishedWithATimestamp() {
        MenuEntity menu = MenuEntity.builder().id(MENU_ID).branchId(BRANCH)
                .status(MenuEntity.Status.DRAFT).build();
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.of(menu));
        when(menuRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuEntity published = service.publish(BRANCH);

        assertEquals(MenuEntity.Status.PUBLISHED, published.getStatus());
        assertNotNull(published.getPublishedAt());
    }

    @Test
    void publishRefusesABranchWithNoMenuYet() {
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.publish(BRANCH));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Compile failure: `getFullMenuByMenuId` and `publish` don't exist yet.

- [ ] **Step 3: Add `getFullMenuByMenuId` and `publish`**

Add both alongside the existing `getFullMenu(UUID merchantId)` (do not
remove it — nothing in this plan retires the merchant-wide endpoint that
still backs it):

```java
    /** Menu-scoped variant, used by the new branch-level public endpoint (Task 8). */
    public MenuResponse getFullMenuByMenuId(UUID menuId) {
        List<CategoryEntity> categories = categoryRepository.findByMenuIdOrderByDisplayOrderAsc(menuId);

        List<MenuResponse.CategoryDto> categoryDtos = categories.stream().map(cat -> {
            List<ProductEntity> products = productRepository.findByCategoryId(cat.getId());

            List<MenuResponse.ProductDto> productDtos = products.stream().map(prod ->
                    MenuResponse.ProductDto.builder()
                            .id(prod.getId())
                            .name(prod.getName())
                            .description(prod.getDescription())
                            .price(prod.getPrice())
                            .image(prod.getImage())
                            .available(prod.isAvailable())
                            .preparationTime(prod.getPreparationTime())
                            .build()
            ).collect(Collectors.toList());

            return MenuResponse.CategoryDto.builder()
                    .id(cat.getId())
                    .name(cat.getName())
                    .items(productDtos)
                    .build();
        }).collect(Collectors.toList());

        return MenuResponse.builder().categories(categoryDtos).build();
    }

    /**
     * Publish is the HLD's own gate: "Only menus with a Published status are
     * made available through the public menu interface" (6.2). Requires a
     * menu to already exist for the branch — call getOrCreateMenuForBranch
     * (via adding a category) before this.
     */
    @Transactional
    public MenuEntity publish(Long branchId) {
        MenuEntity menu = menuRepository.findByBranchId(branchId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No menu to publish for branch " + branchId + " — add at least one category first"));
        menu.setStatus(MenuEntity.Status.PUBLISHED);
        menu.setPublishedAt(java.time.LocalDateTime.now());
        return menuRepository.save(menu);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Recompile and run `MenuPublishTest` via `JUnitRunner`. Expect
`3 tests successful`.

- [ ] **Step 5: Commit**

```bash
git add backend/menu-service/src/main/java/com/qrserve/menu/service/MenuService.java \
        backend/menu-service/src/test/java/com/qrserve/menu/service/MenuPublishTest.java
git commit -m "feat(menu-service): add getFullMenuByMenuId and publish(branchId)"
```

---

### Task 6: `QrSignatureService` merchant+branch signature scope

**Files:**
- Modify: `backend/shared/common/src/main/java/com/qrserve/shared/common/QrSignatureService.java`
- Test: `backend/shared/common/src/test/java/com/qrserve/shared/common/QrSignatureServiceDigitalMenuTest.java`

**Interfaces:**
- Produces: `QrSignatureService.generateSignature(UUID merchantId, Long branchId): String`,
  `QrSignatureService.validateSignature(String signature, UUID merchantId, Long branchId): boolean`.
  Additive overloads — the existing 3-arg methods are untouched.

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.shared.common;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrSignatureServiceDigitalMenuTest {

    private final QrSignatureService service = new QrSignatureService("test-master-secret", "");
    private static final UUID MERCHANT = UUID.randomUUID();

    @Test
    void roundTripsWithoutATable() {
        String signature = service.generateSignature(MERCHANT, 5L);
        assertTrue(service.validateSignature(signature, MERCHANT, 5L));
    }

    @Test
    void aTableScopedSignatureDoesNotValidateTheBranchOnlyScope() {
        // Signing different fewer fields must not be confusable with the
        // existing 3-arg scheme, even for the same merchant/branch.
        String tableScoped = service.generateSignature(MERCHANT, 5L, 42L);
        assertFalse(service.validateSignature(tableScoped, MERCHANT, 5L));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Compile failure: the 2-arg overloads don't exist yet.

- [ ] **Step 3: Add the overloads**

Add these two public methods and one private helper to `QrSignatureService`
(do not touch the existing 3-arg `generateSignature`/`validateSignature`/
`sign`):

```java
    /** Signs {merchantId, branchId} — no table. Used by the digital-menu URL family. */
    public String generateSignature(UUID merchantId, Long branchId) {
        return sign(secret, merchantId, branchId);
    }

    public boolean validateSignature(String signature, UUID merchantId, Long branchId) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        boolean valid = constantTimeEquals(signature, sign(secret, merchantId, branchId));
        if (!valid && !previousSecret.isBlank()) {
            valid = constantTimeEquals(signature, sign(previousSecret, merchantId, branchId));
        }
        return valid;
    }

    private String sign(String masterSecret, UUID merchantId, Long branchId) {
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId is required to sign a QR payload");
        }
        // Prefixed "dm:" so this payload space can never collide with the
        // existing "{merchantId}:{branchId}:{tableId}" scheme even when
        // branchId happens to render the same — e.g. a tableId of null would
        // otherwise produce the literal same string as this method's output.
        String payload = "dm:" + merchantId + ":" + branchId;
        return mac(deriveTenantKey(masterSecret, merchantId), payload.getBytes(StandardCharsets.UTF_8));
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Recompile `shared:common` and run `QrSignatureServiceDigitalMenuTest` via
`JUnitRunner`. Expect `2 tests successful`. Also re-run the existing
`QrSignatureServiceTest` to confirm the 3-arg scheme is unaffected.

- [ ] **Step 5: Commit**

```bash
git add backend/shared/common/src/main/java/com/qrserve/shared/common/QrSignatureService.java \
        backend/shared/common/src/test/java/com/qrserve/shared/common/QrSignatureServiceDigitalMenuTest.java
git commit -m "feat(shared-common): add merchant+branch QR signature scope"
```

---

### Task 7: `DigitalMenuUrl` builder

**Files:**
- Create: `backend/shared/common/src/main/java/com/qrserve/shared/common/DigitalMenuUrl.java`
- Test: `backend/shared/common/src/test/java/com/qrserve/shared/common/DigitalMenuUrlTest.java`

**Interfaces:**
- Produces: `DigitalMenuUrl.branchUrl(String merchantSlug, String branchSlug): String`
  → `{scheme}://{publicMenuDomain}/m/{merchantSlug}/{branchSlug}`.
  `DigitalMenuUrl.merchantUrl(String merchantSlug): String` →
  `{scheme}://{publicMenuDomain}/m/{merchantSlug}`.

A sibling of `PublicMenuUrl`, not a modification of it — it needs its own
`publicMenuDomain`, wired to the new `PUBLIC_MENU_DOMAIN` config value,
distinct from `PublicMenuUrl`'s `PUBLIC_BASE_DOMAIN`.

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.shared.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DigitalMenuUrlTest {

    private final DigitalMenuUrl url = new DigitalMenuUrl("menu.safaricom.et", "https");

    @Test
    void buildsTheCanonicalBranchUrl() {
        assertEquals("https://menu.safaricom.et/m/sunrise-coffee/main", url.branchUrl("sunrise-coffee", "main"));
    }

    @Test
    void buildsTheMerchantShortLink() {
        assertEquals("https://menu.safaricom.et/m/sunrise-coffee", url.merchantUrl("sunrise-coffee"));
    }

    @Test
    void encodesSlugsThatNeedIt() {
        assertEquals("https://menu.safaricom.et/m/joe%27s-diner/main",
                url.branchUrl("joe's-diner", "main"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Compile failure — `DigitalMenuUrl` doesn't exist.

- [ ] **Step 3: Write `DigitalMenuUrl`**

```java
package com.qrserve.shared.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * URL builder for the phase-1 Digital Menu HLD's path-based public URL
 * family — {@code /m/{merchant-slug}[/{branch-slug}]}. Deliberately a
 * sibling of {@link PublicMenuUrl}, not a change to it: this scheme lives on
 * its own domain ({@code PUBLIC_MENU_DOMAIN}), scales to ~30,000 merchants
 * on one shared host (no per-tenant subdomain/wildcard-cert concern), and
 * never encodes a table — see
 * docs/superpowers/specs/2026-09-04-menu-url-access-redesign-design.md.
 */
@Component
public class DigitalMenuUrl {

    private final String publicMenuDomain;
    private final String scheme;

    public DigitalMenuUrl(
            @Value("${app.public-menu-domain}") String publicMenuDomain,
            @Value("${app.public-url-scheme:https}") String scheme) {
        require(publicMenuDomain, "app.public-menu-domain");
        this.publicMenuDomain = publicMenuDomain;
        this.scheme = scheme;
    }

    public String merchantUrl(String merchantSlug) {
        require(merchantSlug, "merchantSlug");
        return scheme + "://" + publicMenuDomain + "/m/" + encode(merchantSlug);
    }

    public String branchUrl(String merchantSlug, String branchSlug) {
        require(merchantSlug, "merchantSlug");
        require(branchSlug, "branchSlug");
        return scheme + "://" + publicMenuDomain + "/m/" + encode(merchantSlug) + "/" + encode(branchSlug);
    }

    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Recompile `shared:common` and run `DigitalMenuUrlTest` via `JUnitRunner`.
Expect `3 tests successful`.

- [ ] **Step 5: Commit**

```bash
git add backend/shared/common/src/main/java/com/qrserve/shared/common/DigitalMenuUrl.java \
        backend/shared/common/src/test/java/com/qrserve/shared/common/DigitalMenuUrlTest.java
git commit -m "feat(shared-common): add DigitalMenuUrl builder for the /m/** URL family"
```

---

### Task 8: menu-service public content + publish endpoints

**Files:**
- Modify: `backend/menu-service/src/main/java/com/qrserve/menu/controller/MenuController.java`
- Test: `backend/menu-service/src/test/java/com/qrserve/menu/controller/MenuControllerDigitalMenuTest.java`

**Interfaces:**
- Consumes: `MenuService.getFullMenuByMenuId`, `MenuService.publish` (Task 5),
  `MenuRepository.findByBranchId` (via a new `MenuService.getMenuStatusForBranch`
  helper — see Step 3).

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.menu.controller;

import com.qrserve.menu.dto.MenuResponse;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.service.MenuService;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuControllerDigitalMenuTest {

    private MenuService menuService;
    private MenuController controller;
    private static final Long BRANCH = 5L;
    private static final UUID MENU_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        menuService = mock(MenuService.class);
        controller = new MenuController(menuService);
    }

    @Test
    void getMenuForBranchReturnsContentWhenPublished() {
        MenuEntity menu = MenuEntity.builder().id(MENU_ID).branchId(BRANCH)
                .status(MenuEntity.Status.PUBLISHED).build();
        when(menuService.getMenuForBranch(BRANCH)).thenReturn(Optional.of(menu));
        when(menuService.getFullMenuByMenuId(MENU_ID)).thenReturn(MenuResponse.builder().build());

        MenuResponse body = controller.getMenuForBranch(BRANCH).getBody();

        assertNotNull(body);
    }

    @Test
    void getMenuForBranchRefusesADraftMenu() {
        MenuEntity menu = MenuEntity.builder().id(MENU_ID).branchId(BRANCH)
                .status(MenuEntity.Status.DRAFT).build();
        when(menuService.getMenuForBranch(BRANCH)).thenReturn(Optional.of(menu));

        assertThrows(ResourceNotFoundException.class, () -> controller.getMenuForBranch(BRANCH));
    }

    @Test
    void getMenuForBranchRefusesABranchWithNoMenuAtAll() {
        when(menuService.getMenuForBranch(BRANCH)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> controller.getMenuForBranch(BRANCH));
    }

    @Test
    void publishDelegatesToTheService() {
        MenuEntity published = MenuEntity.builder().id(MENU_ID).branchId(BRANCH)
                .status(MenuEntity.Status.PUBLISHED).build();
        when(menuService.publish(BRANCH)).thenReturn(published);

        MenuEntity body = controller.publish(BRANCH).getBody();

        assertEquals(MenuEntity.Status.PUBLISHED, body.getStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Compile failure: `MenuService.getMenuForBranch`, `MenuController.getMenuForBranch`,
`MenuController.publish` don't exist yet.

- [ ] **Step 3: Add `getMenuForBranch` to `MenuService`**

```java
    public Optional<MenuEntity> getMenuForBranch(Long branchId) {
        return menuRepository.findByBranchId(branchId);
    }
```

(add `import java.util.Optional;` if not already present in the file — it
is not, since the class had no `Optional`-returning method before.)

- [ ] **Step 4: Add the two endpoints to `MenuController`**

```java
    @GetMapping("/branch/{branchId}")
    @Operation(summary = "Get the published digital menu for a branch (public, phase-1 URL family)")
    public ResponseEntity<MenuResponse> getMenuForBranch(@PathVariable Long branchId) {
        MenuEntity menu = menuService.getMenuForBranch(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("No menu for branch " + branchId));
        if (menu.getStatus() != MenuEntity.Status.PUBLISHED) {
            throw new ResourceNotFoundException("No published menu for branch " + branchId);
        }
        return ResponseEntity.ok(menuService.getFullMenuByMenuId(menu.getId()));
    }

    @PostMapping("/branch/{branchId}/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Publish a branch's menu, making it publicly reachable")
    public ResponseEntity<MenuEntity> publish(@PathVariable Long branchId) {
        return ResponseEntity.ok(menuService.publish(branchId));
    }
```

Add the needed imports:
`com.qrserve.menu.entity.MenuEntity`,
`com.qrserve.shared.exceptions.ResourceNotFoundException`,
`org.springframework.security.access.prepost.PreAuthorize`.

- [ ] **Step 5: Run tests to verify they pass**

Recompile and run `MenuControllerDigitalMenuTest` via `JUnitRunner`. Expect
`4 tests successful`.

- [ ] **Step 6: Commit**

```bash
git add backend/menu-service/src/main/java/com/qrserve/menu/controller/MenuController.java \
        backend/menu-service/src/main/java/com/qrserve/menu/service/MenuService.java \
        backend/menu-service/src/test/java/com/qrserve/menu/controller/MenuControllerDigitalMenuTest.java
git commit -m "feat(menu-service): public branch-menu content and publish endpoints"
```

---

### Task 9: merchant-service `PublicDigitalMenuController`

**Files:**
- Create: `backend/merchant-service/src/main/java/com/qrserve/merchant/controller/PublicDigitalMenuController.java`
- Create: `backend/merchant-service/src/main/java/com/qrserve/merchant/dto/DigitalMenuResolutionResponse.java`
- Create: `backend/merchant-service/src/main/java/com/qrserve/merchant/service/PublicDigitalMenuResolutionService.java`
- Test: `backend/merchant-service/src/test/java/com/qrserve/merchant/service/PublicDigitalMenuResolutionServiceTest.java`

**Interfaces:**
- Consumes: `MerchantService.getMerchantBySlug` (existing), `BranchService.getBranchesByMerchant`,
  `BranchService.getBranchByMerchantAndSlug` (existing, Task 2 file read).
- Produces: `DigitalMenuResolutionResponse{merchantId, merchantSlug, branchId, branchSlug, branchName}`.
  `PublicDigitalMenuResolutionService.resolvePrimary(String merchantSlug): DigitalMenuResolutionResponse`
  — throws `ResourceNotFoundException` if the merchant has no primary branch
  designated (should not happen given Task 2's DB default, but a branch
  deletion could leave this state — see spec Open Item 2).
  `PublicDigitalMenuResolutionService.resolveBranch(String merchantSlug, String branchSlug): DigitalMenuResolutionResponse`.

This is a **new file**, a sibling of the existing `PublicMenuController`/
`PublicMenuResolutionService` — neither of those is modified.

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.DigitalMenuResolutionResponse;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicDigitalMenuResolutionServiceTest {

    private MerchantService merchantService;
    private BranchService branchService;
    private PublicDigitalMenuResolutionService service;
    private static final UUID MERCHANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        merchantService = mock(MerchantService.class);
        branchService = mock(BranchService.class);
        service = new PublicDigitalMenuResolutionService(merchantService, branchService);
    }

    @Test
    void resolvePrimaryReturnsTheMerchantsPrimaryBranch() {
        when(merchantService.getMerchantBySlug("sunrise")).thenReturn(
                MerchantEntity.builder().id(MERCHANT_ID).slug("sunrise").build());
        when(branchService.getBranchesByMerchant(MERCHANT_ID)).thenReturn(List.of(
                BranchEntity.builder().id(1L).slug("annex").isPrimary(false).name("Annex").build(),
                BranchEntity.builder().id(2L).slug("main").isPrimary(true).name("Main").build()));

        DigitalMenuResolutionResponse response = service.resolvePrimary("sunrise");

        assertEquals("main", response.getBranchSlug());
        assertEquals(2L, response.getBranchId());
    }

    @Test
    void resolvePrimaryRefusesAMerchantWithNoPrimaryBranch() {
        when(merchantService.getMerchantBySlug("sunrise")).thenReturn(
                MerchantEntity.builder().id(MERCHANT_ID).slug("sunrise").build());
        when(branchService.getBranchesByMerchant(MERCHANT_ID)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.resolvePrimary("sunrise"));
    }

    @Test
    void resolveBranchReturnsTheNamedBranch() {
        when(merchantService.getMerchantBySlug("sunrise")).thenReturn(
                MerchantEntity.builder().id(MERCHANT_ID).slug("sunrise").build());
        when(branchService.getBranchByMerchantAndSlug(MERCHANT_ID, "annex")).thenReturn(
                BranchEntity.builder().id(1L).slug("annex").name("Annex").build());

        DigitalMenuResolutionResponse response = service.resolveBranch("sunrise", "annex");

        assertEquals(1L, response.getBranchId());
        assertEquals("Annex", response.getBranchName());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Compile failure: none of the new classes exist yet.

- [ ] **Step 3: Write `DigitalMenuResolutionResponse`**

```java
package com.qrserve.merchant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DigitalMenuResolutionResponse {
    private UUID merchantId;
    private String merchantSlug;
    private Long branchId;
    private String branchSlug;
    private String branchName;
}
```

- [ ] **Step 4: Write `PublicDigitalMenuResolutionService`**

```java
package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.DigitalMenuResolutionResponse;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resolves the two phase-1 digital-menu public routes:
 * {@code /m/{merchant-slug}} (primary branch) and
 * {@code /m/{merchant-slug}/{branch-slug}} (a named branch). A sibling of
 * {@link PublicMenuResolutionService} — that class and its table-scoped
 * route are untouched by this addition.
 */
@Service
@RequiredArgsConstructor
public class PublicDigitalMenuResolutionService {

    private final MerchantService merchantService;
    private final BranchService branchService;

    public DigitalMenuResolutionResponse resolvePrimary(String merchantSlug) {
        MerchantEntity merchant = merchantService.getMerchantBySlug(merchantSlug);
        List<BranchEntity> branches = branchService.getBranchesByMerchant(merchant.getId());
        BranchEntity primary = branches.stream()
                .filter(BranchEntity::isPrimary)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Merchant " + merchantSlug + " has no primary branch designated"));

        return toResponse(merchant, primary);
    }

    public DigitalMenuResolutionResponse resolveBranch(String merchantSlug, String branchSlug) {
        MerchantEntity merchant = merchantService.getMerchantBySlug(merchantSlug);
        BranchEntity branch = branchService.getBranchByMerchantAndSlug(merchant.getId(), branchSlug);
        return toResponse(merchant, branch);
    }

    private DigitalMenuResolutionResponse toResponse(MerchantEntity merchant, BranchEntity branch) {
        return DigitalMenuResolutionResponse.builder()
                .merchantId(merchant.getId())
                .merchantSlug(merchant.getSlug())
                .branchId(branch.getId())
                .branchSlug(branch.getSlug())
                .branchName(branch.getName())
                .build();
    }
}
```

- [ ] **Step 5: Write `PublicDigitalMenuController`**

```java
package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.DigitalMenuResolutionResponse;
import com.qrserve.merchant.service.PublicDigitalMenuResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public (no-JWT) resolution for the phase-1 Digital Menu URL family
 * ({@code /m/{merchant-slug}[/{branch-slug}]}). Exposed under
 * {@code /api/v1/public/**} so the gateway routes it without a session,
 * exactly like {@link PublicMenuController} — a sibling of that controller,
 * which stays untouched.
 */
@RestController
@RequestMapping("/api/v1/public/digital-menu")
@RequiredArgsConstructor
@Tag(name = "Public Digital Menu Resolution", description = "Unauthenticated resolution for the phase-1 path-based menu URLs")
public class PublicDigitalMenuController {

    private final PublicDigitalMenuResolutionService resolutionService;

    @GetMapping("/{merchantSlug}")
    @Operation(summary = "Resolve a merchant's primary branch, for the /m/{slug} short link")
    public ResponseEntity<DigitalMenuResolutionResponse> resolvePrimary(@PathVariable String merchantSlug) {
        return ResponseEntity.ok(resolutionService.resolvePrimary(merchantSlug));
    }

    @GetMapping("/{merchantSlug}/{branchSlug}")
    @Operation(summary = "Resolve a specific branch's canonical digital-menu URL")
    public ResponseEntity<DigitalMenuResolutionResponse> resolveBranch(
            @PathVariable String merchantSlug, @PathVariable String branchSlug) {
        return ResponseEntity.ok(resolutionService.resolveBranch(merchantSlug, branchSlug));
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Recompile merchant-service (main + test) and run
`PublicDigitalMenuResolutionServiceTest` via `JUnitRunner`. Expect
`3 tests successful`.

- [ ] **Step 7: Commit**

```bash
git add backend/merchant-service/src/main/java/com/qrserve/merchant/controller/PublicDigitalMenuController.java \
        backend/merchant-service/src/main/java/com/qrserve/merchant/dto/DigitalMenuResolutionResponse.java \
        backend/merchant-service/src/main/java/com/qrserve/merchant/service/PublicDigitalMenuResolutionService.java \
        backend/merchant-service/src/test/java/com/qrserve/merchant/service/PublicDigitalMenuResolutionServiceTest.java
git commit -m "feat(merchant-service): add public digital-menu URL resolution"
```

---

### Task 10: `SecurityConfig` — permit the two new public paths

**Files:**
- Modify: `backend/shared/security/src/main/java/com/qrserve/shared/security/SecurityConfig.java`
- Test: none — `SecurityConfig` has no existing unit test in this codebase
  (it's exercised by full integration tests like `TenantIsolationIT`, which
  is out of this plan's reach without a running database). Verify by
  inspection against the ordering rule below, and re-confirm during
  `tenant-smoke-test` skill execution before merging (see Task 14).

**Interfaces:** none — configuration only.

- [ ] **Step 1: Add the two new rules**

In the `authorizeHttpRequests` block, in section 5 ("Public customer-facing
reads/writes, narrowly scoped"), add these two lines directly below the
existing `"/api/v1/public/**"` rule (so they are visibly grouped with it,
though `/api/v1/public/**` already technically covers
`/api/v1/public/digital-menu/**` — see the note below):

```java
                // Public digital-menu content (menu-service). Single-segment
                // wildcard so this cannot widen to match a write endpoint.
                .requestMatchers(HttpMethod.GET, "/api/menu/branch/*").permitAll()
```

**Note on `/api/v1/public/digital-menu/**`:** it is already covered by the
existing `.requestMatchers("/api/v1/public/**").permitAll()` rule (section
5), so no new rule is needed for it — confirm this explicitly rather than
adding a redundant, easy-to-drift-from-the-original duplicate rule. The one
genuinely new surface is `/api/menu/branch/*` (menu-service, Task 8), which
is a different path prefix from `/api/v1/public/**` and does need its own
line.

- [ ] **Step 2: Verify placement against the ordering rule**

Confirm the new `/api/menu/branch/*` rule sits **above** section 7's
`anyRequest().authenticated()` (it does, by construction — everything in
section 5 sits above section 7) and does not sit **below** any broader
`permitAll()` that might already shadow-match it (there is none — no
existing rule matches `/api/menu/**` more broadly than the two specific
rules already present). This is a direct instance of the
`docs/codebase-review.md` `permitAll` ordering defect class — this
verification step exists specifically to check for it, per the spec's
testing-gate section.

- [ ] **Step 3: Commit**

```bash
git add backend/shared/security/src/main/java/com/qrserve/shared/security/SecurityConfig.java
git commit -m "feat(security): permit public GET /api/menu/branch/* for digital menus"
```

---

### Task 11: Gateway path-based tenant resolution for `/m/**`

**Files:**
- Create: `backend/api-gateway/src/main/java/com/qrserve/gateway/tenant/PathTenantResolutionGlobalFilter.java`
- Test: `backend/api-gateway/src/test/java/com/qrserve/gateway/tenant/PathTenantResolutionGlobalFilterTest.java`

**Interfaces:**
- Consumes: `TenantSlugResolver.resolve(String label): Mono<UUID>` (existing,
  reused as-is — same class `TenantResolutionGlobalFilter` already depends
  on).
- Produces: a second `GlobalFilter` bean, active only on paths under
  `/api/v1/public/digital-menu/**`. `TenantResolutionGlobalFilter` is not
  modified — this is a **new, separate filter class**, per the spec's
  "additive... a new filter (or a guarded branch at the top of the same
  filter, implementation's call)" — a new class keeps the two resolution
  strategies (host-based vs. path-based) fully independent and each
  individually testable, which a shared/branching class would not.

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.gateway.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class PathTenantResolutionGlobalFilterTest {

    private static final UUID MERCHANT_ID = UUID.randomUUID();

    @Test
    void resolvesTheMerchantSlugFromThePathAndInjectsHeaders() {
        TenantSlugResolver resolver = mock(TenantSlugResolver.class);
        when(resolver.resolve("sunrise")).thenReturn(Mono.just(MERCHANT_ID));
        PathTenantResolutionGlobalFilter filter = new PathTenantResolutionGlobalFilter(resolver);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/public/digital-menu/sunrise/main").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        org.mockito.ArgumentCaptor<ServerWebExchange> captor =
                org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerHttpRequest mutated = captor.getValue().getRequest();
        assertEquals(MERCHANT_ID.toString(), mutated.getHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_ID_HEADER));
        assertEquals("sunrise", mutated.getHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_SLUG_HEADER));
    }

    @Test
    void unknownSlugIs404NotAFallback() {
        TenantSlugResolver resolver = mock(TenantSlugResolver.class);
        when(resolver.resolve("ghost")).thenReturn(Mono.empty());
        PathTenantResolutionGlobalFilter filter = new PathTenantResolutionGlobalFilter(resolver);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/public/digital-menu/ghost").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void pathsOutsideTheDigitalMenuPrefixAreIgnored() {
        TenantSlugResolver resolver = mock(TenantSlugResolver.class);
        PathTenantResolutionGlobalFilter filter = new PathTenantResolutionGlobalFilter(resolver);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(resolver);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Compile failure — `PathTenantResolutionGlobalFilter` doesn't exist.

- [ ] **Step 3: Write `PathTenantResolutionGlobalFilter`**

```java
package com.qrserve.gateway.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Resolves tenant identity from the URL PATH, for the phase-1 digital-menu
 * routes only ({@code /api/v1/public/digital-menu/**}). A sibling of
 * {@link TenantResolutionGlobalFilter} (host-based, for the parked
 * subdomain+table scheme) — that filter is untouched. Both domains
 * (menu.safaricom.et vs. the tenant-subdomain host) never collide, so there
 * is no ambiguity about which filter's output applies to a given request.
 */
@Component
@Slf4j
public class PathTenantResolutionGlobalFilter implements GlobalFilter, Ordered {

    private static final String DIGITAL_MENU_PREFIX = "/api/v1/public/digital-menu/";

    private final TenantSlugResolver resolver;

    public PathTenantResolutionGlobalFilter(TenantSlugResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith(DIGITAL_MENU_PREFIX)) {
            return chain.filter(exchange);
        }

        String remainder = path.substring(DIGITAL_MENU_PREFIX.length());
        String merchantSlug = remainder.split("/", 2)[0];
        if (merchantSlug.isBlank()) {
            return chain.filter(exchange);
        }

        return resolver.resolve(merchantSlug)
                .flatMap(merchantId -> chain.filter(withTenant(exchange, merchantId, merchantSlug)))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No tenant for path-derived slug '{}'", merchantSlug);
                    exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
                    return exchange.getResponse().setComplete();
                }));
    }

    private ServerWebExchange withTenant(ServerWebExchange exchange, java.util.UUID merchantId, String slug) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(TenantResolutionGlobalFilter.TENANT_ID_HEADER, merchantId.toString());
                    headers.set(TenantResolutionGlobalFilter.TENANT_SLUG_HEADER, slug);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    /** Same order as the host-based filter — both run ahead of routing, independently. */
    @Override
    public int getOrder() {
        return -100;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

`api-gateway` is a reactive/WebFlux module — `spring-boot-starter-webflux`
already pulls in `org.springframework.mock.http.server.reactive` and
`org.springframework.mock.web.server` test fixtures used above; confirm
they resolve on the classpath the `verify-backend` skill builds (they are
transitively part of `spring-boot-starter-test`'s `spring-test` artifact,
already in this module's dependencies). Compile `api-gateway` main + test
sources and run `PathTenantResolutionGlobalFilterTest` via `JUnitRunner`.
Expect `3 tests successful`.

- [ ] **Step 5: Commit**

```bash
git add backend/api-gateway/src/main/java/com/qrserve/gateway/tenant/PathTenantResolutionGlobalFilter.java \
        backend/api-gateway/src/test/java/com/qrserve/gateway/tenant/PathTenantResolutionGlobalFilterTest.java
git commit -m "feat(api-gateway): path-based tenant resolution for /api/v1/public/digital-menu/**"
```

---

### Task 12: config wiring — `PUBLIC_MENU_DOMAIN`

**Files:**
- Modify: `backend/api-gateway/src/main/resources/application.yml`
- Modify: `backend/menu-service/src/main/resources/application.yml`
- Modify: `backend/merchant-service/src/main/resources/application.yml`
- Modify: `backend/.env.example`

**Interfaces:** none — configuration only. `DigitalMenuUrl` (Task 7) is a
`@Component` in `shared:common`, component-scanned into every service, so
every service that boots needs `app.public-menu-domain` set — same
fail-fast reasoning already documented above `app.public-base-domain` in
`api-gateway/application.yml`.

- [ ] **Step 1: Add the property to each service's `application.yml`**

In each of the three files, add alongside the existing `app:` block (or
create one if absent):

```yaml
app:
  public-menu-domain: ${PUBLIC_MENU_DOMAIN}
```

(menu-service and merchant-service may not have an `app:` block yet — add
one. api-gateway already has `app.public-base-domain` and
`app.public-url-scheme` under `app:` — add `public-menu-domain` as a third
key in that same block.)

- [ ] **Step 2: Document the new variable**

Add to `backend/.env.example`, next to the existing `PUBLIC_BASE_DOMAIN`
documentation:

```
# Public domain for the phase-1 path-based Digital Menu URLs
# (/m/{merchant-slug}[/{branch-slug}]). Separate from PUBLIC_BASE_DOMAIN,
# which serves the parked per-tenant-subdomain scheme. No wildcard DNS
# needed for local dev — any value works, e.g. localhost:3000.
PUBLIC_MENU_DOMAIN=
```

- [ ] **Step 3: Verify every service still boots**

There is no automated boot test available in this environment (per
`.claude/skills/verify-backend/SKILL.md` — the `./gradlew` loopback issue).
Report `./gradlew bootRun` (or `docker compose up`) against each modified
service as the user's manual verification step for this task specifically —
a missing/misspelled property here fails at Spring context startup, not at
compile time, so the `verify-backend` javac harness cannot catch it.

- [ ] **Step 4: Commit**

```bash
git add backend/api-gateway/src/main/resources/application.yml \
        backend/menu-service/src/main/resources/application.yml \
        backend/merchant-service/src/main/resources/application.yml \
        backend/.env.example
git commit -m "feat(config): wire PUBLIC_MENU_DOMAIN for the digital-menu URL family"
```

---

### Task 13: backfill runner

**Files:**
- Create: `backend/menu-service/src/main/java/com/qrserve/menu/migration/BranchMenuBackfillRunner.java`
- Test: `backend/menu-service/src/test/java/com/qrserve/menu/migration/BranchMenuBackfillRunnerTest.java`
- Modify: `backend/menu-service/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `MenuRepository`, `CategoryRepository`, `ProductRepository`
  (existing), `RestTemplate` (new `AppConfig` bean, mirroring
  `qr-service`'s `AppConfig` read during planning), `JwtTokenProvider.generateInternalServiceToken`
  (existing, `shared:security`).

This is a **one-off, opt-in job**: a `CommandLineRunner` gated by
`backfill.branch-menus.enabled` (default `false`), so it does nothing on
every normal boot and is switched on for exactly one deploy. Per the spec:
duplicating a relational tree (categories → products) with new ids, once
per branch, is the trickiest part of this migration — done in code, tested,
rather than hand-written SQL.

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.menu.migration;

import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The runner's actual REST calls to merchant-service aren't exercised here —
 * BranchLookup is injected as a seam so this test proves the duplication
 * logic (one independent Menu+Category+Product copy per branch) without a
 * running HTTP stack.
 */
class BranchMenuBackfillRunnerTest {

    private MenuRepository menuRepository;
    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        menuRepository = mock(MenuRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
    }

    @Test
    void eachBranchGetsAnIndependentCopyOfTheMerchantsExistingCatalog() {
        CategoryEntity existingCategory = CategoryEntity.builder()
                .id(1L).merchantId(MERCHANT).name("Drinks").displayOrder(0).build();
        ProductEntity existingProduct = ProductEntity.builder()
                .id(1L).merchantId(MERCHANT).categoryId(1L).name("Espresso")
                .price(BigDecimal.TEN).available(true).preparationTime(3).build();

        when(categoryRepository.findByMerchantIdOrderByDisplayOrderAsc(MERCHANT))
                .thenReturn(List.of(existingCategory));
        when(productRepository.findByCategoryId(1L)).thenReturn(List.of(existingProduct));
        when(menuRepository.findByBranchId(any())).thenReturn(Optional.empty());
        when(menuRepository.save(any())).thenAnswer(inv -> {
            MenuEntity m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BranchMenuBackfillRunner runner = new BranchMenuBackfillRunner(
                menuRepository, categoryRepository, productRepository);

        runner.backfillMerchant(MERCHANT, List.of(10L, 11L));

        // One Menu per branch, both PUBLISHED (existing catalogs are already
        // always-live today — publishing nothing new does not change
        // customer-visible behavior at cutover, per the spec).
        verify(menuRepository, times(2)).save(argThat(m -> m.getStatus() == MenuEntity.Status.PUBLISHED));
        // One independent category copy per branch (2 branches -> 2 saves), not one shared row.
        verify(categoryRepository, times(2)).save(any());
        verify(productRepository, times(2)).save(any());
    }

    @Test
    void aBranchThatAlreadyHasAMenuIsSkipped() {
        when(menuRepository.findByBranchId(10L)).thenReturn(
                Optional.of(MenuEntity.builder().id(UUID.randomUUID()).branchId(10L).build()));
        when(categoryRepository.findByMerchantIdOrderByDisplayOrderAsc(MERCHANT)).thenReturn(List.of());

        BranchMenuBackfillRunner runner = new BranchMenuBackfillRunner(
                menuRepository, categoryRepository, productRepository);

        runner.backfillMerchant(MERCHANT, List.of(10L));

        // Idempotent: re-running the job must not duplicate an already-backfilled branch.
        verify(menuRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Compile failure — `BranchMenuBackfillRunner` doesn't exist.

- [ ] **Step 3: Write `BranchMenuBackfillRunner`**

```java
package com.qrserve.menu.migration;

import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.ProductRepository;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One-off, opt-in migration: every existing branch gets its own independent
 * copy of its merchant's current (always-live, merchantId-scoped) catalog.
 * Disabled by default ({@code backfill.branch-menus.enabled=false}); flip it
 * on for exactly one deploy, then back off. See
 * docs/superpowers/specs/2026-09-04-menu-url-access-redesign-design.md
 * Section 5 for why this is code, not a Flyway/SQL script: generating new
 * ids and remapping category->product foreign keys per branch is far safer
 * to get right — and to unit test — here than in {@code INSERT ... SELECT}.
 */
@Component
@Slf4j
public class BranchMenuBackfillRunner implements CommandLineRunner {

    private final MenuRepository menuRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    // Optional constructor-injected collaborators for the full run() path;
    // the no-HTTP constructor below is what the unit test above uses.
    private RestTemplate restTemplate;
    private JwtTokenProvider jwtTokenProvider;
    private String merchantServiceUrl;
    private boolean enabled;

    public BranchMenuBackfillRunner(MenuRepository menuRepository, CategoryRepository categoryRepository,
                                     ProductRepository productRepository) {
        this.menuRepository = menuRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public BranchMenuBackfillRunner(
            MenuRepository menuRepository, CategoryRepository categoryRepository,
            ProductRepository productRepository, RestTemplate restTemplate,
            JwtTokenProvider jwtTokenProvider,
            @Value("${services.merchant-service-url:http://localhost:8085}") String merchantServiceUrl,
            @Value("${backfill.branch-menus.enabled:false}") boolean enabled) {
        this(menuRepository, categoryRepository, productRepository);
        this.restTemplate = restTemplate;
        this.jwtTokenProvider = jwtTokenProvider;
        this.merchantServiceUrl = merchantServiceUrl;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        log.info("Branch-menu backfill starting");
        HttpHeaders authHeaders = serviceAuthHeaders();

        List<UUID> merchantIds = fetchAllMerchantIds(authHeaders);
        for (UUID merchantId : merchantIds) {
            List<Long> branchIds = fetchBranchIds(merchantId, authHeaders);
            backfillMerchant(merchantId, branchIds);
        }
        log.info("Branch-menu backfill complete: {} merchants processed", merchantIds.size());
    }

    /**
     * The pure, HTTP-free part — one independent Menu (+ category/product
     * copy) per branch, skipping any branch that already has one. Public so
     * it's directly unit-testable without standing up REST calls.
     */
    public void backfillMerchant(UUID merchantId, List<Long> branchIds) {
        List<CategoryEntity> sourceCategories =
                categoryRepository.findByMerchantIdOrderByDisplayOrderAsc(merchantId);

        for (Long branchId : branchIds) {
            if (menuRepository.findByBranchId(branchId).isPresent()) {
                continue; // already backfilled — idempotent re-run
            }

            MenuEntity menu = menuRepository.save(MenuEntity.builder()
                    .branchId(branchId)
                    .merchantId(merchantId)
                    .status(MenuEntity.Status.PUBLISHED)
                    .build());

            for (CategoryEntity source : sourceCategories) {
                CategoryEntity copy = categoryRepository.save(CategoryEntity.builder()
                        .menuId(menu.getId())
                        .merchantId(merchantId)
                        .name(source.getName())
                        .displayOrder(source.getDisplayOrder())
                        .build());

                for (ProductEntity sourceProduct : productRepository.findByCategoryId(source.getId())) {
                    productRepository.save(ProductEntity.builder()
                            .menuId(menu.getId())
                            .merchantId(merchantId)
                            .categoryId(copy.getId())
                            .name(sourceProduct.getName())
                            .description(sourceProduct.getDescription())
                            .price(sourceProduct.getPrice())
                            .image(sourceProduct.getImage())
                            .available(sourceProduct.isAvailable())
                            .preparationTime(sourceProduct.getPreparationTime())
                            .build());
                }
            }
        }
    }

    private HttpHeaders serviceAuthHeaders() {
        UserPrincipal serviceIdentity = UserPrincipal.builder().role(UserRole.SUPER_ADMIN).build();
        String token = jwtTokenProvider.generateInternalServiceToken(serviceIdentity);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private List<UUID> fetchAllMerchantIds(HttpHeaders headers) {
        String url = merchantServiceUrl + "/api/merchants";
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        List<Map<String, Object>> body = response.getBody();
        return body == null ? List.of() : body.stream()
                .map(m -> UUID.fromString((String) m.get("id")))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Long> fetchBranchIds(UUID merchantId, HttpHeaders headers) {
        String url = merchantServiceUrl + "/api/branches/merchant/" + merchantId;
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        List<Map<String, Object>> body = response.getBody();
        return body == null ? List.of() : body.stream()
                .map(b -> ((Number) b.get("id")).longValue())
                .toList();
    }
}
```

**Verify during implementation:** `GET /api/merchants` (used by
`fetchAllMerchantIds`) was not directly inspected while writing this plan —
confirm its exact path and required role against
`MerchantController.getAllMerchants` before running this job, and adjust
`serviceAuthHeaders`'s role if it demands something other than
`SUPER_ADMIN`.

- [ ] **Step 4: Add the config flag**

In `backend/menu-service/src/main/resources/application.yml`, add:

```yaml
backfill:
  branch-menus:
    enabled: ${BACKFILL_BRANCH_MENUS_ENABLED:false}
```

- [ ] **Step 5: Run tests to verify they pass**

Recompile and run `BranchMenuBackfillRunnerTest` via `JUnitRunner`. Expect
`2 tests successful`.

- [ ] **Step 6: Commit**

```bash
git add backend/menu-service/src/main/java/com/qrserve/menu/migration/BranchMenuBackfillRunner.java \
        backend/menu-service/src/test/java/com/qrserve/menu/migration/BranchMenuBackfillRunnerTest.java \
        backend/menu-service/src/main/resources/application.yml
git commit -m "feat(menu-service): opt-in backfill runner for per-branch menus"
```

---

### Task 14: frontend — `/m/**` routes and a lean digital-menu page

**Files:**
- Create: `src/pages/DigitalMenuPage.tsx`
- Create: `src/lib/digitalMenu.ts`
- Create: `src/lib/digitalMenu.test.ts`
- Modify: `src/router/AppRouter.tsx`

**Interfaces:**
- Produces: `resolvePrimaryBranch(merchantSlug): Promise<DigitalMenuResolution>`,
  `resolveBranch(merchantSlug, branchSlug): Promise<DigitalMenuResolution>`,
  `fetchBranchMenu(branchId): Promise<MenuResponse>` in `src/lib/digitalMenu.ts`.
  `DigitalMenuPage` — a new component, **not** a modification of
  `CustomerMenuPage.tsx` (that page's cart/order UI stays exactly as-is,
  parked with the rest of the ordering flow this plan does not touch).

- [ ] **Step 1: Write the failing test**

```typescript
// src/lib/digitalMenu.test.ts
import { buildDigitalMenuApiPath } from './digitalMenu';

function ok(name: string, condition: boolean) {
  console.log(`  ${condition ? 'ok' : 'FAIL'}  ${name}`);
  if (!condition) process.exitCode = 1;
}

ok(
  'primary-branch resolution path has no branch segment',
  buildDigitalMenuApiPath('sunrise-coffee') === '/api/v1/public/digital-menu/sunrise-coffee',
);

ok(
  'branch resolution path includes the branch slug',
  buildDigitalMenuApiPath('sunrise-coffee', 'main') === '/api/v1/public/digital-menu/sunrise-coffee/main',
);

ok(
  'slugs are URI-encoded',
  buildDigitalMenuApiPath("joe's-diner", 'main') === "/api/v1/public/digital-menu/joe's-diner/main"
    ? false // a literal apostrophe must NOT pass through unencoded
    : buildDigitalMenuApiPath("joe's-diner", 'main') === '/api/v1/public/digital-menu/joe%27s-diner/main',
);

console.log(process.exitCode ? 'FAILED' : 'all digitalMenu tests passed');
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npx tsx src/lib/digitalMenu.test.ts
```

Expected: module-not-found error — `src/lib/digitalMenu.ts` doesn't exist
yet.

- [ ] **Step 3: Write `src/lib/digitalMenu.ts`**

```typescript
/**
 * Client for the phase-1 Digital Menu URL family
 * (/m/{merchant-slug}[/{branch-slug}]). Deliberately separate from
 * src/lib/tenant.ts and src/lib/api.ts's table-scoped menu calls — this is
 * the new, read-only public surface; the existing table-scoped flow
 * (CustomerMenuPage, resolveMenuTarget) is untouched.
 */

export interface DigitalMenuResolution {
  merchantId: string;
  merchantSlug: string;
  branchId: number;
  branchSlug: string;
  branchName: string;
}

export interface DigitalMenuProduct {
  id: number;
  name: string;
  description: string | null;
  price: number;
  image: string | null;
  available: boolean;
  preparationTime: number;
}

export interface DigitalMenuCategory {
  id: number;
  name: string;
  items: DigitalMenuProduct[];
}

export interface DigitalMenuResponse {
  categories: DigitalMenuCategory[];
}

export function buildDigitalMenuApiPath(merchantSlug: string, branchSlug?: string): string {
  const base = `/api/v1/public/digital-menu/${encodeURIComponent(merchantSlug)}`;
  return branchSlug ? `${base}/${encodeURIComponent(branchSlug)}` : base;
}

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path);
  if (!response.ok) {
    throw new Error(`Request to ${path} failed with ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export function resolvePrimaryBranch(merchantSlug: string): Promise<DigitalMenuResolution> {
  return getJson<DigitalMenuResolution>(buildDigitalMenuApiPath(merchantSlug));
}

export function resolveBranch(merchantSlug: string, branchSlug: string): Promise<DigitalMenuResolution> {
  return getJson<DigitalMenuResolution>(buildDigitalMenuApiPath(merchantSlug, branchSlug));
}

export function fetchBranchMenu(branchId: number): Promise<DigitalMenuResponse> {
  return getJson<DigitalMenuResponse>(`/api/menu/branch/${branchId}`);
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npx tsx src/lib/digitalMenu.test.ts
```

Expected: `all digitalMenu tests passed`.

- [ ] **Step 5: Write `src/pages/DigitalMenuPage.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  resolveBranch,
  resolvePrimaryBranch,
  fetchBranchMenu,
  type DigitalMenuResolution,
} from '@/lib/digitalMenu';

/**
 * Read-only digital menu view — no cart, no ordering (out of scope for the
 * phase-1 HLD rollout; see docs/superpowers/specs/2026-09-04-menu-url-access-redesign-design.md).
 * Deliberately a new page, not a reuse of CustomerMenuPage, which carries
 * cart/order UI that belongs to the parked ordering flow.
 */
export function DigitalMenuPage() {
  const { merchantSlug, branchSlug } = useParams<{ merchantSlug: string; branchSlug?: string }>();

  if (!merchantSlug) {
    return <div role="alert">Menu not found.</div>;
  }

  if (!branchSlug) {
    return <PrimaryBranchRedirect merchantSlug={merchantSlug} />;
  }

  return <BranchMenu merchantSlug={merchantSlug} branchSlug={branchSlug} />;
}

function PrimaryBranchRedirect({ merchantSlug }: { merchantSlug: string }) {
  const [resolution, setResolution] = useState<DigitalMenuResolution | 'not-found' | null>(null);

  useEffect(() => {
    let cancelled = false;
    resolvePrimaryBranch(merchantSlug)
      .then((r) => { if (!cancelled) setResolution(r); })
      .catch(() => { if (!cancelled) setResolution('not-found'); });
    return () => { cancelled = true; };
  }, [merchantSlug]);

  if (resolution === null) {
    return <div>Loading menu…</div>;
  }
  if (resolution === 'not-found') {
    return <div role="alert">Menu coming soon.</div>;
  }
  return <Navigate replace to={`/m/${merchantSlug}/${resolution.branchSlug}`} />;
}

function BranchMenu({ merchantSlug, branchSlug }: { merchantSlug: string; branchSlug: string }) {
  const resolutionQuery = useQuery({
    queryKey: ['digital-menu-resolution', merchantSlug, branchSlug],
    queryFn: () => resolveBranch(merchantSlug, branchSlug),
  });

  const menuQuery = useQuery({
    queryKey: ['digital-menu-content', resolutionQuery.data?.branchId],
    queryFn: () => fetchBranchMenu(resolutionQuery.data!.branchId),
    enabled: !!resolutionQuery.data,
  });

  if (resolutionQuery.isError) {
    return <div role="alert">Menu not found.</div>;
  }
  if (resolutionQuery.isLoading || menuQuery.isLoading) {
    return <div>Loading menu…</div>;
  }
  if (menuQuery.isError) {
    return <div role="alert">Menu coming soon.</div>;
  }

  return (
    <div>
      <h1>{resolutionQuery.data?.branchName}</h1>
      {menuQuery.data?.categories.map((category) => (
        <section key={category.id}>
          <h2>{category.name}</h2>
          <ul>
            {category.items.map((item) => (
              <li key={item.id}>
                <strong>{item.name}</strong> — {item.price}
                {item.description && <p>{item.description}</p>}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
```

- [ ] **Step 6: Add the routes**

In `src/router/AppRouter.tsx`, add two new routes (import `DigitalMenuPage`
at the top). Place them alongside the other public/customer-facing routes,
without touching the existing `/menu/:merchantSlug/:branchSlug/:tableNumber`
route:

```tsx
<Route path="/m/:merchantSlug" element={<DigitalMenuPage />} />
<Route path="/m/:merchantSlug/:branchSlug" element={<DigitalMenuPage />} />
```

- [ ] **Step 7: Run the frontend test suite and lint**

```bash
npm run lint
npx tsx src/lib/digitalMenu.test.ts
```

Both must pass with no new errors (the `.claude/hooks/frontend-lint.ps1` and
`frontend-test.ps1` hooks run this automatically after each edit in this
task, per this repo's `.claude/settings.json`).

- [ ] **Step 8: Commit**

```bash
git add src/pages/DigitalMenuPage.tsx src/lib/digitalMenu.ts src/lib/digitalMenu.test.ts src/router/AppRouter.tsx
git commit -m "feat(frontend): add /m/{merchant-slug}[/{branch-slug}] digital menu route"
```

---

### Task 15: qr-service — branch-only QR rendering

**Files:**
- Modify: `backend/qr-service/src/main/java/com/qrserve/qr/service/QrGeneratorService.java`
- Modify: `backend/qr-service/src/main/java/com/qrserve/qr/controller/QrController.java`
- Test: `backend/qr-service/src/test/java/com/qrserve/qr/service/QrGeneratorServiceDigitalMenuTest.java`

**Interfaces:**
- Consumes: `DigitalMenuUrl.branchUrl` (Task 7), `QrSignatureService.generateSignature(UUID, Long)`
  (Task 6), `PublicDigitalMenuController.resolveBranch` (Task 9, called over
  REST — same `merchantServiceUrl` + `getAuthHeaders()` pattern already used
  by `fetchBranch`/`fetchMerchant` in this class), the existing
  package-private static `renderPng` (unchanged, reused as-is).
- Produces: `QrGeneratorService.getQrForBranch(String merchantSlug, String branchSlug): byte[]`.

This closes the spec's Section 3 commitment ("generate a QR code encoding
it... store both") for the new URL family — the "store" is the branch's own
slug (already persisted, permanent — slug renames are blocked, per the
existing `PublicMenuUrl` design this mirrors), so the QR is rendered
on-demand from data already at hand, exactly as the existing table QR
system's PNG rendering already is (`exportPng`) — nothing new needs
persisting.

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.qr.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Only the static, HTTP-free decision is unit-tested here — the same reason
 * targetUrlFor and renderPng are static and package-private: this must not
 * diverge between calls without standing up HTTP.
 */
class QrGeneratorServiceDigitalMenuTest {

    @Test
    void rendersAPngForADigitalMenuUrl() {
        byte[] png = QrGeneratorService.renderPng("https://menu.safaricom.et/m/sunrise-coffee/main", 300);

        // PNG magic bytes: 89 50 4E 47
        assertTrue(png.length > 8);
        assertTrue((png[0] & 0xFF) == 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G');
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

This actually **passes immediately** — `renderPng` already exists and is
generic over any string payload; there is nothing digital-menu-specific
about it yet. This step exists to lock in the exact behavior being relied
upon before layering the new method on top of it in Step 3. Confirm it
passes, then proceed — TDD's "red" step is the compile failure in Step 3
below instead, which is the actual new surface.

- [ ] **Step 3: Add `getQrForBranch` to `QrGeneratorService`**

```java
    /**
     * Renders a QR for the phase-1 digital-menu branch URL. No stored QR
     * state exists for this scheme (unlike table QR's rotation-tracked
     * payload) — slugs are permanent, so the URL is fully reproducible from
     * merchant-service data on every call.
     */
    public byte[] getQrForBranch(String merchantSlug, String branchSlug) {
        DigitalMenuResolution resolution = fetchDigitalMenuResolution(merchantSlug, branchSlug);
        String url = digitalMenuUrl.branchUrl(resolution.merchantSlug(), resolution.branchSlug());
        String signature = qrSignatureService.generateSignature(resolution.merchantId(), resolution.branchId());
        return renderPng(url + "?signature=" + signature, QR_SIZE);
    }

    private DigitalMenuResolution fetchDigitalMenuResolution(String merchantSlug, String branchSlug) {
        try {
            String url = merchantServiceUrl + "/api/v1/public/digital-menu/" + merchantSlug + "/" + branchSlug;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(getAuthHeaders()),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new ResourceNotFoundException("No branch " + branchSlug + " for merchant " + merchantSlug);
            }
            return new DigitalMenuResolution(
                    UUID.fromString((String) body.get("merchantId")),
                    (String) body.get("merchantSlug"),
                    ((Number) body.get("branchId")).longValue(),
                    (String) body.get("branchSlug"));
        } catch (Exception e) {
            log.error("Failed to resolve digital menu branch {}/{}", merchantSlug, branchSlug, e);
            throw new ResourceNotFoundException("No branch " + branchSlug + " for merchant " + merchantSlug);
        }
    }

    private record DigitalMenuResolution(UUID merchantId, String merchantSlug, Long branchId, String branchSlug) {}
```

Add two new constructor-injected fields alongside the existing ones
(`restTemplate`, `publicMenuUrl`, `qrSignatureService`,
`merchantServiceUrl` already exist in this class):

```java
    private final com.qrserve.shared.common.DigitalMenuUrl digitalMenuUrl;
```

(`@RequiredArgsConstructor` regenerates the constructor from the field
list.

**Verify during implementation:** this constructor gains a new required
field, which breaks any existing test that constructs
`new QrGeneratorService(...)` directly rather than through Spring. This
plan's own new test only calls the static `renderPng`, so it's unaffected —
but `QrGeneratorServiceTest` (existing, not read while writing this plan)
was not confirmed either way. Before this task is done, grep that file for
`new QrGeneratorService(` and add `digitalMenuUrl` (a mock is fine) to any
constructor call found.)

- [ ] **Step 4: Add the endpoint to `QrController`**

```java
    @GetMapping("/digital-menu/{merchantSlug}/{branchSlug}")
    @Operation(summary = "Render a QR PNG for a branch's phase-1 digital menu URL")
    public ResponseEntity<byte[]> getDigitalMenuQr(
            @PathVariable String merchantSlug, @PathVariable String branchSlug) {
        byte[] png = qrGeneratorService.getQrForBranch(merchantSlug, branchSlug);
        return ResponseEntity.ok().header("Content-Type", "image/png").body(png);
    }
```

- [ ] **Step 5: Add the gateway route and security rule**

`qr-service`'s existing gateway route (`Path=/api/qr/**`) does not cover
`/api/qr/digital-menu/**`... it does — `/api/qr/digital-menu/{merchantSlug}/{branchSlug}`
matches `Path=/api/qr/**`, so no gateway route change is needed. Add the
security rule (`SecurityConfig`, alongside the other public digital-menu
rule from Task 10):

```java
                .requestMatchers(HttpMethod.GET, "/api/qr/digital-menu/*/*").permitAll()
```

- [ ] **Step 6: Run tests to verify they pass**

Recompile `qr-service` (main + test, plus its `shared:common` dependency
already carrying `DigitalMenuUrl` from Task 7) and run
`QrGeneratorServiceDigitalMenuTest` via `JUnitRunner`. Expect
`1 test successful`.

- [ ] **Step 7: Commit**

```bash
git add backend/qr-service/src/main/java/com/qrserve/qr/service/QrGeneratorService.java \
        backend/qr-service/src/main/java/com/qrserve/qr/controller/QrController.java \
        backend/qr-service/src/test/java/com/qrserve/qr/service/QrGeneratorServiceDigitalMenuTest.java \
        backend/shared/security/src/main/java/com/qrserve/shared/security/SecurityConfig.java
git commit -m "feat(qr-service): render QR for the phase-1 digital-menu branch URL"
```

---

## Final verification

After all 15 tasks:

1. Run the full backend suite via the `verify-backend` skill for every
   module touched (`shared:common`, `shared:security`, `menu-service`,
   `merchant-service`, `api-gateway`) — confirm nothing outside this plan's
   new tests regressed.
2. Run `npm run lint` and `npm run test:unit` (frontend) — both must be
   clean.
3. Report `./gradlew build` as the user's outstanding step (per
   `verify-backend` skill — this harness cannot catch dependency-alignment
   failures the way Gradle's real resolver would).
4. Run the `tenant-smoke-test` skill against a running local stack before
   merging — this plan adds new public, unauthenticated endpoints
   (`/api/v1/public/digital-menu/**`, `/api/menu/branch/*`), which is
   exactly the surface class that skill exists to catch regressions on.
5. Manually exercise the golden path in a browser: create a branch (auto-
   primary), add a category and product to it, publish it, then open
   `/m/{merchant-slug}` and confirm it redirects to
   `/m/{merchant-slug}/{branch-slug}` and renders the published items.
