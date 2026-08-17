# Frontend Optimization — Summary

**Date:** 2026-08-16
**Scope:** Phase 2 of the QRServe monorepo review. Performance & correctness only — no visual design/style changes were made.

---

## What was changed and why

### 1. Fixed confirmed frontend bug — QR signature was dropped (`src/hooks/useApiData.ts`, `src/pages/CustomerMenuPage.tsx`)

**Finding (Phase 1):** `useCreateTableRequest` destructured `signature` but never forwarded it to the backend. `CustomerMenuPage` passed `signature` into the mutation, but it was silently discarded — so the backend's QR-signature tamper check was never exercised from the customer flow, and any caller could create customer requests for any table.

**Fix:** `useCreateTableRequest` now routes through `publicApi.createTableRequest(tableId, { requestType, note }, signature)` — the versioned public endpoint `POST /api/v1/tables/{tableId}/requests?signature=...` — so the signature actually reaches the backend. The `CustomerMenuPage` call site was simplified to pass only `{ tableId, requestType, signature }` (the v1 endpoint derives merchant/branch tenant from the table ID server-side, so the now-unused `merchantId`/`branchId` fields were removed).

### 2. Reactive auth gating for TanStack Query (`src/hooks/useApiData.ts`, `src/hooks/useLookups.ts`)

**Finding (Phase 1):** `enabled: isAuthenticated()` reads a module-level `authToken` variable that is not reactive. Queries gated this way would not re-enable when the user logs in without a full component remount. `AuthContext` already exposed a reactive `isAuthenticated` state that the hooks were not using.

**Fix:** Every auth-gated query now uses `const { isAuthenticated: isAuth } = useAuth()` and passes `enabled: isAuth` (or `enabled: !!id && isAuth`). Affected hooks in `useApiData.ts`: `useCurrentUser`, `useMerchant`, `useBranches`, `useTables`, `useTable`, `useWaiters`, `useMenu`, `useOrders`, `useKitchenOrders`, `useTableQr`, `useTodayAnalytics`, `useRevenueAnalytics`, `usePopularItems`, `useWaiterTasks`. Same fix applied to all lookup hooks in `useLookups.ts`.

### 3. Scoped query invalidation (`src/hooks/useApiData.ts`)

**Finding (Phase 1):** `useUpdateCategory` / `useDeleteCategory` / `useCreateProduct` / `useUpdateProduct` / `useDeleteProduct` invalidated the broad `['menu']` prefix, over-refetching every menu query in the app (all merchants) on any single change.

**Fix:**
- `useCreateCategory` now invalidates exactly `['menu', variables.merchantId]`.
- `useUpdateProduct` now invalidates `['menu', variables.data.merchantId]` when a merchantId is supplied, falling back to the broad prefix only when it isn't.
- Merchant/branch mutations now also invalidate `['lookup', 'merchants']` / `['lookup', 'branches']` so the cached lookup maps (used by `useRelationships`) stay in sync with CRUD.

### 4. Route-level code splitting (`src/router/AppRouter.tsx`)

**Finding (Phase 1):** Every page (admin, merchant, branch, waiter, kitchen) was statically imported, so the initial bundle included all of them.

**Fix:** Applied `React.lazy` + `Suspense` to all 13 page components: `CustomerMenuPage`, `DashboardPage`, `MerchantManagement`, `BranchManagement`, `TableManagement`, `WaiterManagement`, `UserManagement`, `AnalyticsPage`, `SettingsPage`, `WaiterRequestsPage`, `KitchenLivePage`, `MenuBuilderPage`, `WaiterDashboardPage`. Landing and Login remain statically imported (entry shell). A shared `RouteFallback` spinner wraps the `Routes` tree.

### 5. Removed unused dependencies (`package.json`)

**Finding (Phase 1):** `@google/genai`, `axios` (the API client uses `fetch`), `dotenv`, and `express` were declared but never imported anywhere in the source tree (verified via `search_files`). `@types/express` was also unused.

**Fix:** Removed all five from `package.json`. `package-lock.json` will be re-generated on the next `npm install`/`bun install`.

### 6. Accessibility audit (verification pass)

**Findings & outcome:**
- The active customer components rendered by `CustomerMenuPage` — `ServiceDock` (FAB button has `aria-label`, `aria-expanded`; action buttons have visible text), `CartSheet` (dialog role, `aria-modal`, `aria-label` on every icon-only button), and `OrderProgress` — **all already have correct accessible names/roles**. No changes needed.
- The only icon-only button missing an `aria-label` was the legacy `CustomerMenuView.tsx` add-to-cart button, but that component is **dead code** — it is not referenced by any route or page (verified via `search_files`). It was deliberately left alone; removing a dead component is not a performance/correctness change.

---

## Flagged but deliberately left alone

| Item | Rationale |
|---|---|
| Legacy `CustomerMenuView.tsx` + its modal children (`ItemDetailModal`, `CartCheckoutDrawer`, `OrderStatusModal`) | Dead code — not reachable from `AppRouter`. Removing it would be cleanup beyond Phase 2's performance/correctness scope; no runtime impact. |
| `staleTime` / `refetchInterval` defaults in `queryClient.ts` (30s stale, 10–15s kitchen/order polling) | Current values are reasonable for a demo/real-time ordering app; lowering them further would increase backend load without UX benefit. The realtime STOMP stream already invalidates kitchen/order queries on push events (see `useRealtime.ts`), so polling is a fallback, not the primary path. |
| Route component tree duplication (same page on multiple kitchen paths) | Functionally correct; lazy chunks are cached after first load so reusing the same lazy component on multiple routes costs nothing. |
| `bun.lock` / `package-lock.json` | Not manually edited — they'll re-sync on next install. Rewriting lockfiles by hand is error-prone and unnecessary. |
| Backend-side fixes from Phase 1 (e.g. phantom endpoints, `@PreAuthorize` gaps) | Out of scope — Phase 2 is frontend-only. |

---

## Verification

- `npx tsc --noEmit` passes with exit code 0 (no type errors) after all edits.
- No visual/style tokens, classNames, or layout markup were altered — all changes are behavioral (data-fetching, code-splitting, dependency hygiene, one bug fix).