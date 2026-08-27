# Phase 1 Frontend Shell & Customer Menu Gating Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gate the QRServe frontend so it runs as a lean, mobile-first Phase 1 Merchant Mini App by default — MERCHANT_OWNER-only login, a bottom-tab shell replacing the sidebar, every Phase 2 route hard-blocked, and the public menu page stripped of ordering/waiter-call UI — with the full Phase 2 experience unchanged and one env var away from returning.

**Architecture:** One build-time boolean flag (`VITE_ENABLE_PHASE_2`, default off) drives everything. Two new pure-function modules (`phase.ts`, plus additions to `navigation.ts`) carry all the testable logic; `tsx`-run `.test.ts` files exercise them exactly like the repo's existing `tenant.test.ts`. The React components that consume those functions (`ProtectedRoute`, `LoginPage`, `DashboardLayout`, `AppRouter`, `CustomerMenuPage`) are edited but not unit-tested, matching this repo's existing convention (no component test runner exists — verify those via `npm run lint` plus manual check).

**Tech Stack:** React 19, React Router 7, Vite, TypeScript, TanStack Query, Tailwind. Test harness: `tsx` running plain `.test.ts` files with `node:assert/strict` (no Jest/Vitest — this repo doesn't have one).

**Spec:** `docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md` (§4.1–4.3, 4.5)

## Global Constraints

- Never delete existing code, controllers, or components. Every Phase 2 branch must still fully work when `VITE_ENABLE_PHASE_2=true`.
- The env var is `VITE_ENABLE_PHASE_2`, a string `'true'`/`'false'` (Vite env vars are always strings), default disabled.
- Only `MERCHANT_OWNER` may use the app while Phase 2 is disabled — every other role (including `SUPER_ADMIN`) is unauthorized.
- Match the existing pure-function test style exactly: `node:assert/strict`, the hand-rolled `test(name, fn)` runner, `process.exit(1)` on failure — see `src/lib/tenant.test.ts`. Do not introduce Jest, Vitest, or any new test runner.
- Run `npm run lint` (`tsc --noEmit`) after every task that touches `.tsx`/`.ts` files — this is the only automated check available for the JSX-only tasks.

---

### Task 1: `src/lib/phase.ts` — Phase 1/2 gating logic

**Files:**
- Create: `src/lib/phase.ts`
- Create: `src/lib/phase.test.ts`
- Modify: `package.json:13`

**Interfaces:**
- Produces: `parseEnabledFlag(value: string | undefined): boolean`, `isPhase2Enabled(): boolean`, `isRoleAllowedInPhase(role: string, phase2Enabled?: boolean): boolean` — all later tasks import these three from `../lib/phase`.

- [ ] **Step 1: Write the failing test**

Create `src/lib/phase.test.ts`:

```ts
/**
 * Pure-function tests for Phase 1/2 gating. Run with `npm run test:unit`.
 */
import assert from 'node:assert/strict';
import { parseEnabledFlag, isRoleAllowedInPhase } from './phase';

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

// ---- parseEnabledFlag ----

test('the literal string "true" enables phase 2', () => {
  assert.equal(parseEnabledFlag('true'), true);
});

test('anything else disables phase 2, including "1" and "TRUE"', () => {
  assert.equal(parseEnabledFlag('1'), false);
  assert.equal(parseEnabledFlag('TRUE'), false);
  assert.equal(parseEnabledFlag('false'), false);
});

test('an unset env value disables phase 2 by default', () => {
  assert.equal(parseEnabledFlag(undefined), false);
});

// ---- isRoleAllowedInPhase ----

test('MERCHANT_OWNER is allowed whether or not phase 2 is enabled', () => {
  assert.equal(isRoleAllowedInPhase('MERCHANT_OWNER', false), true);
  assert.equal(isRoleAllowedInPhase('MERCHANT_OWNER', true), true);
});

test('every other role is blocked in phase 1', () => {
  assert.equal(isRoleAllowedInPhase('SUPER_ADMIN', false), false);
  assert.equal(isRoleAllowedInPhase('BRANCH_MANAGER', false), false);
  assert.equal(isRoleAllowedInPhase('WAITER', false), false);
  assert.equal(isRoleAllowedInPhase('KITCHEN', false), false);
  assert.equal(isRoleAllowedInPhase('CASHIER', false), false);
});

test('every role is allowed once phase 2 is enabled', () => {
  assert.equal(isRoleAllowedInPhase('WAITER', true), true);
  assert.equal(isRoleAllowedInPhase('SUPER_ADMIN', true), true);
});

if (failures > 0) {
  console.error(`\n${failures} test(s) failed`);
  process.exit(1);
}
console.log('\nall phase tests passed');
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx tsx src/lib/phase.test.ts`
Expected: FAIL — `Cannot find module './phase'` (the file doesn't exist yet).

- [ ] **Step 3: Write the implementation**

Create `src/lib/phase.ts`:

```ts
/**
 * Phase 1 / Phase 2 feature gating.
 *
 * Phase 1 is the lean M-PESA Merchant Mini App: Auth, Menu, a single QR,
 * a read-only public menu. Phase 2 (tables, waiters, kitchen, orders,
 * analytics) is fully preserved in the codebase but hidden behind this flag
 * until it's turned back on. See
 * docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md.
 */

const MERCHANT_OWNER_ROLE = 'MERCHANT_OWNER';

/**
 * Parses the raw VITE_ENABLE_PHASE_2 env value. Exported as a pure function
 * (rather than reading import.meta.env directly everywhere) so it's
 * testable without mocking Vite's env injection.
 */
export function parseEnabledFlag(value: string | undefined): boolean {
  return value === 'true';
}

function readRawFlag(): string | undefined {
  // `import.meta.env` only exists under Vite. This module is also imported
  // by phase.test.ts, which runs under plain node via tsx, so reading it
  // unguarded throws at import time and takes the whole test file with it -
  // same guard as src/lib/tenant.ts's readBaseDomain().
  const env = (import.meta as ImportMeta & { env?: Record<string, string> }).env;
  return env?.VITE_ENABLE_PHASE_2;
}

const PHASE_2_ENABLED: boolean = parseEnabledFlag(readRawFlag());

/** Whether Phase 2 (tables, waiters, kitchen, orders, analytics) is active. */
export function isPhase2Enabled(): boolean {
  return PHASE_2_ENABLED;
}

/**
 * Whether `role` may use the app in the current phase. In Phase 1 only
 * MERCHANT_OWNER may log in; every other role (including SUPER_ADMIN) is
 * unauthorized until Phase 2 is re-enabled.
 */
export function isRoleAllowedInPhase(
  role: string,
  phase2Enabled: boolean = isPhase2Enabled(),
): boolean {
  return phase2Enabled || role === MERCHANT_OWNER_ROLE;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx tsx src/lib/phase.test.ts`
Expected: PASS — `all phase tests passed`, exit code 0.

- [ ] **Step 5: Wire into the test:unit script**

Modify `package.json:13`, from:

```json
    "test:unit": "tsx src/lib/tenant.test.ts && tsx src/lib/orderSession.test.ts && tsx src/lib/qrDisplay.test.ts"
```

to:

```json
    "test:unit": "tsx src/lib/tenant.test.ts && tsx src/lib/orderSession.test.ts && tsx src/lib/qrDisplay.test.ts && tsx src/lib/phase.test.ts"
```

- [ ] **Step 6: Run the full unit suite to confirm nothing broke**

Run: `npm run test:unit`
Expected: all four test files report `ok` for every case, exit code 0.

- [ ] **Step 7: Commit**

```bash
git add src/lib/phase.ts src/lib/phase.test.ts package.json
git commit -m "feat(frontend): add phase 1/2 gating logic"
```

---

### Task 2: `src/lib/navigation.ts` — Phase 1 navigation table

**Files:**
- Modify: `src/lib/navigation.ts`
- Create: `src/lib/navigation.test.ts`

**Interfaces:**
- Consumes: `isPhase2Enabled` from `../lib/phase` (Task 1).
- Produces: `getNavigationForRole(role: string, phase2Enabled?: boolean): NavItem[]`, `getRoleHomeRoute(role: string, phase2Enabled?: boolean): string` — both keep their existing names/shapes (only gaining an optional second parameter), so every existing caller (`Sidebar.tsx`, `ProtectedRoute.tsx`, `AppRouter.tsx`) keeps working unchanged.

- [ ] **Step 1: Write the failing test**

Create `src/lib/navigation.test.ts`:

```ts
/**
 * Pure-function tests for phase-aware role navigation. Run with
 * `npm run test:unit`.
 */
import assert from 'node:assert/strict';
import { getNavigationForRole, getRoleHomeRoute } from './navigation';

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

// ---- getNavigationForRole ----

test('phase 1 gives MERCHANT_OWNER exactly Dashboard, Menu & QR, Settings', () => {
  const items = getNavigationForRole('MERCHANT_OWNER', false);
  assert.deepEqual(
    items.map((i) => i.path),
    ['/merchant/dashboard', '/merchant/menu', '/merchant/settings'],
  );
});

test('phase 1 gives every other role no navigation at all', () => {
  assert.deepEqual(getNavigationForRole('SUPER_ADMIN', false), []);
  assert.deepEqual(getNavigationForRole('BRANCH_MANAGER', false), []);
  assert.deepEqual(getNavigationForRole('WAITER', false), []);
  assert.deepEqual(getNavigationForRole('KITCHEN', false), []);
});

test('phase 2 restores the full navigation for every role', () => {
  const merchantItems = getNavigationForRole('MERCHANT_OWNER', true);
  assert.equal(merchantItems.length, 9);
  assert.ok(getNavigationForRole('WAITER', true).length > 0);
  assert.ok(getNavigationForRole('SUPER_ADMIN', true).length > 0);
});

test('an unknown role gets no navigation in either phase', () => {
  assert.deepEqual(getNavigationForRole('BOGUS', false), []);
  assert.deepEqual(getNavigationForRole('BOGUS', true), []);
});

// ---- getRoleHomeRoute ----

test('MERCHANT_OWNER lands on the dashboard in phase 1', () => {
  assert.equal(getRoleHomeRoute('MERCHANT_OWNER', false), '/merchant/dashboard');
});

test('a role with no phase 1 navigation falls back to /login', () => {
  assert.equal(getRoleHomeRoute('WAITER', false), '/login');
  assert.equal(getRoleHomeRoute('SUPER_ADMIN', false), '/login');
});

test('phase 2 restores each role\'s real home route', () => {
  assert.equal(getRoleHomeRoute('SUPER_ADMIN', true), '/admin/dashboard');
  assert.equal(getRoleHomeRoute('WAITER', true), '/waiter/dashboard');
});

if (failures > 0) {
  console.error(`\n${failures} test(s) failed`);
  process.exit(1);
}
console.log('\nall navigation tests passed');
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx tsx src/lib/navigation.test.ts`
Expected: FAIL — `getNavigationForRole('MERCHANT_OWNER', false)` returns the full 9-item list (the phase parameter doesn't exist yet), so the first assertion fails.

- [ ] **Step 3: Write the implementation**

Modify `src/lib/navigation.ts`. Add the import at the top (after the existing `lucide-react` import block, before `export interface NavItem`):

```ts
import { isPhase2Enabled } from './phase';
```

Add a new `ROLE_NAVIGATION_PHASE1` constant directly after the existing `ROLE_NAVIGATION` map closes (after its final `};`):

```ts
/**
 * Phase 1 navigation: only MERCHANT_OWNER has anything to show. Every other
 * role's Phase 1 experience is "no navigation, log out" - see
 * isRoleAllowedInPhase in ./phase.ts and its use in ProtectedRoute/LoginPage.
 */
export const ROLE_NAVIGATION_PHASE1: Record<string, NavItem[]> = {
  MERCHANT_OWNER: [
    { label: 'Dashboard', path: '/merchant/dashboard', icon: LayoutDashboard },
    { label: 'Menu & QR', path: '/merchant/menu', icon: QrCode },
    { label: 'Settings', path: '/merchant/settings', icon: Settings },
  ],
};
```

Add `QrCode` to the `lucide-react` import at the top of the file — change:

```ts
import {
  LayoutDashboard,
  Store,
  Building2,
  Users,
  Table as TableIcon,
  UserCog,
  BarChart3,
  Settings,
  CreditCard,
  Utensils,
  ShoppingBag,
  ChefHat,
  ClipboardList,
  CheckCircle2,
  type LucideIcon,
} from 'lucide-react';
```

to:

```ts
import {
  LayoutDashboard,
  Store,
  Building2,
  Users,
  Table as TableIcon,
  UserCog,
  BarChart3,
  Settings,
  CreditCard,
  Utensils,
  ShoppingBag,
  ChefHat,
  ClipboardList,
  CheckCircle2,
  QrCode,
  type LucideIcon,
} from 'lucide-react';
```

Replace the existing `getNavigationForRole` and `getRoleHomeRoute` functions:

```ts
/**
 * Get the navigation items for a given role.
 * Falls back to an empty array if the role is unknown.
 */
export function getNavigationForRole(role: string): NavItem[] {
  return ROLE_NAVIGATION[role] || [];
}

/**
 * Get the home route for a given role.
 */
export function getRoleHomeRoute(role: string): string {
  const items = ROLE_NAVIGATION[role];
  if (items && items.length > 0) {
    return items[0].path;
  }
  return '/login';
}
```

with:

```ts
/**
 * Get the navigation items for a given role, respecting the current phase.
 * In Phase 1, ROLE_NAVIGATION_PHASE1 is used instead of the full table, so
 * every role except MERCHANT_OWNER gets an empty list rather than a filtered
 * one - falling back to '/login' below, not a route with no nav to reach it.
 */
export function getNavigationForRole(
  role: string,
  phase2Enabled: boolean = isPhase2Enabled(),
): NavItem[] {
  const table = phase2Enabled ? ROLE_NAVIGATION : ROLE_NAVIGATION_PHASE1;
  return table[role] || [];
}

/**
 * Get the home route for a given role, respecting the current phase.
 */
export function getRoleHomeRoute(
  role: string,
  phase2Enabled: boolean = isPhase2Enabled(),
): string {
  const items = getNavigationForRole(role, phase2Enabled);
  if (items.length > 0) {
    return items[0].path;
  }
  return '/login';
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx tsx src/lib/navigation.test.ts`
Expected: PASS — `all navigation tests passed`, exit code 0.

- [ ] **Step 5: Wire into the test:unit script and run the full suite**

Modify `package.json:13` again, from:

```json
    "test:unit": "tsx src/lib/tenant.test.ts && tsx src/lib/orderSession.test.ts && tsx src/lib/qrDisplay.test.ts && tsx src/lib/phase.test.ts"
```

to:

```json
    "test:unit": "tsx src/lib/tenant.test.ts && tsx src/lib/orderSession.test.ts && tsx src/lib/qrDisplay.test.ts && tsx src/lib/phase.test.ts && tsx src/lib/navigation.test.ts"
```

Run: `npm run test:unit`
Expected: all five test files pass.

- [ ] **Step 6: Type-check**

Run: `npm run lint`
Expected: no errors (confirms `Sidebar.tsx`'s existing `getNavigationForRole(user.role)` one-arg call still type-checks against the new optional-second-parameter signature).

- [ ] **Step 7: Commit**

```bash
git add src/lib/navigation.ts src/lib/navigation.test.ts package.json
git commit -m "feat(frontend): add phase 1 navigation table"
```

---

### Task 3: `ProtectedRoute.tsx` + `LoginPage.tsx` — the login/session phase gate

**Files:**
- Modify: `src/router/ProtectedRoute.tsx`
- Modify: `src/pages/LoginPage.tsx`

**Interfaces:**
- Consumes: `isPhase2Enabled`, `isRoleAllowedInPhase` from `../lib/phase` (Task 1); `getRoleHomeRoute` from `../lib/navigation` (Task 2, unchanged call site — default param handles it).
- Produces: `ProtectedRoute` gains a `requiresPhase2?: boolean` prop, consumed by `AppRouter.tsx` in Task 4. No other exported signatures change (`getRoleHome`, `getRoleLabel` untouched).

No new pure functions are introduced here — this task wires Task 1's `isRoleAllowedInPhase` into two components. There is no automated test for JSX in this repo (see Global Constraints); verify via `npm run lint` plus the manual checks at the end of this task.

- [ ] **Step 1: Modify `ProtectedRoute.tsx`**

Full replacement of `src/router/ProtectedRoute.tsx`:

```tsx
import React, { useEffect } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Spinner } from '../components/ui/States';
import { getRoleHomeRoute, getNavigationForRole } from '../lib/navigation';
import { isPhase2Enabled, isRoleAllowedInPhase } from '../lib/phase';

export type AllowedRoles = Array<'SUPER_ADMIN' | 'MERCHANT_OWNER' | 'BRANCH_MANAGER' | 'KITCHEN' | 'WAITER' | 'CASHIER'>;

interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: AllowedRoles;
  /**
   * Marks a route as Phase 2 only. Set on every table/waiter/kitchen/order/
   * analytics/admin route in AppRouter.tsx - see
   * docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §4.3.
   */
  requiresPhase2?: boolean;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, allowedRoles, requiresPhase2 }) => {
  const { isAuthenticated, user, isLoading, logout } = useAuth();
  const location = useLocation();

  // A role that isn't allowed in the current phase (e.g. a WAITER's stored
  // session, restored on page load) must never reach a protected page - log
  // it out here rather than relying on every caller to re-check.
  const phaseBlocked = Boolean(user) && !isRoleAllowedInPhase(user!.role, isPhase2Enabled());

  useEffect(() => {
    if (phaseBlocked) {
      logout();
    }
  }, [phaseBlocked, logout]);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <Spinner label="Checking session..." />
      </div>
    );
  }

  if (!isAuthenticated) {
    // Redirect to login, preserve the intended destination
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (phaseBlocked) {
    return <Navigate to="/login" state={{ phaseBlocked: true }} replace />;
  }

  if (requiresPhase2 && !isPhase2Enabled()) {
    return <Navigate to={getRoleHomeRoute(user!.role)} replace />;
  }

  // Role-based access control
  if (allowedRoles && user && !allowedRoles.includes(user.role as any)) {
    // Redirect to the user's home dashboard based on role
    return <Navigate to={getRoleHomeRoute(user.role)} replace />;
  }

  return <>{children}</>;
};

export function getRoleHome(role: string): string {
  return getRoleHomeRoute(role);
}

export function getRoleLabel(role: string): string {
  switch (role) {
    case 'SUPER_ADMIN': return 'Super Admin';
    case 'MERCHANT_OWNER': return 'Merchant';
    case 'BRANCH_MANAGER': return 'Branch Manager';
    case 'KITCHEN': return 'Kitchen';
    case 'WAITER': return 'Waiter';
    case 'CASHIER': return 'Cashier';
    default: return role;
  }
}
```

(`getNavigationForRole` stays imported even though this file doesn't call it directly — confirm before removing; if `tsc --noEmit` flags it as unused, drop the import. It was unused in the original file too, so this is pre-existing, not introduced here.)

- [ ] **Step 2: Modify `LoginPage.tsx`**

Change the import line, from:

```tsx
import React, { useState } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import { QrCode, Loader2, AlertCircle, Lock, Mail, Eye, EyeOff } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../lib/api';
import { getRoleHome } from '../router/ProtectedRoute';
```

to:

```tsx
import React, { useEffect, useState } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import { QrCode, Loader2, AlertCircle, Lock, Mail, Eye, EyeOff } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../lib/api';
import { getRoleHome } from '../router/ProtectedRoute';
import { isPhase2Enabled, isRoleAllowedInPhase } from '../lib/phase';
```

Change the component body's opening (from `export const LoginPage` through the `isAuthenticated && user` redirect block):

```tsx
export const LoginPage: React.FC = () => {
  const { login, isLoading, isAuthenticated, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('admin@hotel.com');
  const [password, setPassword] = useState('password');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // If already authenticated, redirect to role home
  if (isAuthenticated && user) {
    return <Navigate to={getRoleHome(user.role)} replace />;
  }
```

to:

```tsx
export const LoginPage: React.FC = () => {
  const { login, isLoading, isAuthenticated, user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('admin@hotel.com');
  const [password, setPassword] = useState('password');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [wasPhaseBlocked, setWasPhaseBlocked] = useState(false);

  const restoredPhaseBlock = Boolean(
    (location.state as { phaseBlocked?: boolean } | null)?.phaseBlocked,
  );
  const justLoggedInBlocked =
    isAuthenticated && Boolean(user) && !isRoleAllowedInPhase(user!.role, isPhase2Enabled());
  const phaseBlocked = restoredPhaseBlock || justLoggedInBlocked || wasPhaseBlocked;

  // A role that isn't allowed in the current phase still authenticates
  // successfully (the backend has no notion of phase), so it must be logged
  // out here rather than shown a dashboard it has no navigation for.
  // wasPhaseBlocked latches so the message survives the logout() completing
  // and isAuthenticated flipping back to false mid-render.
  useEffect(() => {
    if (justLoggedInBlocked) {
      setWasPhaseBlocked(true);
      logout();
    }
  }, [justLoggedInBlocked, logout]);

  if (phaseBlocked) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
        <div className="w-full max-w-md bg-white rounded-3xl shadow-xl border border-slate-200 p-8 text-center">
          <div className="w-12 h-12 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center mx-auto mb-4">
            <AlertCircle className="w-6 h-6" />
          </div>
          <h1 className="text-lg font-black text-slate-900">Merchant accounts only</h1>
          <p className="text-sm text-slate-500 mt-2">
            This app is available for merchant accounts during this phase. Please sign in with a merchant account.
          </p>
        </div>
      </div>
    );
  }

  // If already authenticated with an allowed role, redirect to role home
  if (isAuthenticated && user) {
    return <Navigate to={getRoleHome(user.role)} replace />;
  }
```

The rest of `LoginPage.tsx` (the form, `handleSubmit`, demo accounts, JSX return) is unchanged.

- [ ] **Step 3: Type-check**

Run: `npm run lint`
Expected: no errors.

- [ ] **Step 4: Manual verification** (no automated component test harness exists in this repo)

With `VITE_ENABLE_PHASE_2` unset (defaults to Phase 1) and the existing full backend running:
1. Log in as the `SUPER_ADMIN` demo account (`admin@hotel.com` / `password`). Expect: briefly authenticated, then the "Merchant accounts only" message, and a subsequent page load shows the login form again (confirms `logout()` completed).
2. Create or use a `MERCHANT_OWNER` test account and log in. Expect: redirected to `/merchant/dashboard` normally.
3. With a `MERCHANT_OWNER` session active, manually navigate to a URL requiring a role this account doesn't have (e.g. `/admin/dashboard`) — this won't hard-block via `requiresPhase2` until Task 4 wires the routes, so for now confirm only that the existing `allowedRoles` check still redirects as before.

- [ ] **Step 5: Commit**

```bash
git add src/router/ProtectedRoute.tsx src/pages/LoginPage.tsx
git commit -m "feat(frontend): gate login and sessions to MERCHANT_OWNER in phase 1"
```

---

### Task 4: `AppRouter.tsx` — mark every Phase 2 route

**Files:**
- Modify: `src/router/AppRouter.tsx`

**Interfaces:**
- Consumes: `requiresPhase2` prop on `ProtectedRoute` (Task 3).
- Produces: nothing new consumed by later tasks — this is a leaf wiring task.

This is a mechanical, repetitive edit: add `requiresPhase2` to the opening `<ProtectedRoute allowedRoles={...}>` tag of every Phase 2 route. **Do not add it** to `/merchant/dashboard`, `/merchant/menu`, or `/merchant/settings` — those three stay Phase 1.

- [ ] **Step 1: Add `requiresPhase2` to every SUPER_ADMIN route**

In `src/router/AppRouter.tsx`, under the `{/* ===== SUPER_ADMIN Routes ===== */}` comment, every one of these 9 routes currently reads `<ProtectedRoute allowedRoles={['SUPER_ADMIN']}>` — change each to `<ProtectedRoute allowedRoles={['SUPER_ADMIN']} requiresPhase2>`:

- `/admin/dashboard`
- `/admin/merchants`
- `/admin/branches`
- `/admin/users`
- `/admin/tables`
- `/admin/waiters`
- `/admin/analytics`
- `/admin/subscriptions`
- `/admin/settings`

- [ ] **Step 2: Add `requiresPhase2` to the Phase 2 MERCHANT_OWNER routes**

Under `{/* ===== MERCHANT_ADMIN Routes ===== */}`, these currently read `<ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']}>` — change to `<ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']} requiresPhase2>` for:

- `/merchant/branches`
- `/merchant/users`
- `/merchant/tables`
- `/merchant/orders`
- `/merchant/waiters`
- `/merchant/analytics`

**Leave unchanged** (no `requiresPhase2`): `/merchant/dashboard`, `/merchant/menu`, `/merchant/settings`.

- [ ] **Step 3: Add `requiresPhase2` to every BRANCH_MANAGER route**

Under `{/* ===== BRANCH_MANAGER Routes ===== */}`, these currently read `<ProtectedRoute allowedRoles={['BRANCH_MANAGER', 'MERCHANT_OWNER', 'SUPER_ADMIN']}>` — change to add ` requiresPhase2` for all 6:

- `/branch/dashboard`
- `/branch/orders`
- `/branch/tables`
- `/branch/waiters`
- `/branch/kitchen`
- `/branch/reports`

- [ ] **Step 4: Add `requiresPhase2` to every WAITER route**

Under `{/* ===== WAITER Routes ===== */}`, these currently read `<ProtectedRoute allowedRoles={['WAITER', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']}>` — change to add ` requiresPhase2` for all 4:

- `/waiter/dashboard`
- `/waiter/tables`
- `/waiter/orders`
- `/waiter/requests`

- [ ] **Step 5: Add `requiresPhase2` to every KITCHEN route**

Under `{/* ===== KITCHEN Routes ===== */}`, these currently read `<ProtectedRoute allowedRoles={['KITCHEN', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']}>` — change to add ` requiresPhase2` for all 4:

- `/kitchen/dashboard`
- `/kitchen/incoming`
- `/kitchen/preparing`
- `/kitchen/ready`

- [ ] **Step 6: Type-check**

Run: `npm run lint`
Expected: no errors.

- [ ] **Step 7: Manual verification**

With `VITE_ENABLE_PHASE_2` unset and logged in as `MERCHANT_OWNER`:
1. Navigate directly to `/merchant/orders` (a URL with no nav link pointing to it anymore, but still typeable). Expect: redirected to `/merchant/dashboard`.
2. Navigate to `/admin/dashboard`. Expect: redirected to `/merchant/dashboard` (not `/admin/dashboard`, since `requiresPhase2` fires before the `allowedRoles` check would even matter).
3. Navigate to `/merchant/dashboard`, `/merchant/menu`, `/merchant/settings` directly. Expect: all three load normally, unaffected.

Then set `VITE_ENABLE_PHASE_2=true` in `.env.local`, restart the dev server, log in as `SUPER_ADMIN`, and confirm `/admin/dashboard` and the rest of the admin console load exactly as before this change.

- [ ] **Step 8: Commit**

```bash
git add src/router/AppRouter.tsx
git commit -m "feat(frontend): hard-block phase 2 routes behind requiresPhase2"
```

---

### Task 5: `MobileBottomNav.tsx` + `DashboardLayout.tsx` — mobile shell

**Files:**
- Create: `src/components/MobileBottomNav.tsx`
- Modify: `src/components/DashboardLayout.tsx`

**Interfaces:**
- Consumes: `getNavigationForRole` (Task 2), `isPhase2Enabled` (Task 1), `useAuth` (existing).
- Produces: `MobileBottomNav` component, imported by `DashboardLayout.tsx`.

No pure functions here (this is pure JSX layout) — verify via `npm run lint` and manual check.

- [ ] **Step 1: Create `MobileBottomNav.tsx`**

```tsx
import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getNavigationForRole } from '../lib/navigation';

/**
 * Fixed bottom tab bar for the Phase 1 Merchant Mini App shell - replaces
 * the sidebar entirely (not just collapsed for mobile). See
 * docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §4.2.
 */
export const MobileBottomNav: React.FC = () => {
  const { user } = useAuth();
  if (!user) return null;

  const navItems = getNavigationForRole(user.role);
  if (navItems.length === 0) return null;

  return (
    <nav
      aria-label="Primary"
      className="safe-b fixed inset-x-0 bottom-0 z-40 flex border-t border-slate-200 bg-white"
    >
      {navItems.map((item) => {
        const Icon = item.icon;
        return (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              `flex min-h-[48px] flex-1 flex-col items-center justify-center gap-1 py-2.5 text-[11px] font-semibold transition-colors ${
                isActive ? 'text-[#0DA64B]' : 'text-slate-500'
              }`
            }
          >
            <Icon className="w-5 h-5" />
            <span>{item.label}</span>
          </NavLink>
        );
      })}
    </nav>
  );
};
```

(`#0DA64B` is a placeholder M-PESA-style green — swap for the exact brand hex when available; it's isolated to this one file, per spec §11 Open Items.)

- [ ] **Step 2: Modify `DashboardLayout.tsx`**

Full replacement of `src/components/DashboardLayout.tsx`:

```tsx
import React, { useState } from 'react';
import { Menu, QrCode } from 'lucide-react';
import { Sidebar } from './Sidebar.tsx';
import { MobileBottomNav } from './MobileBottomNav.tsx';
import { useAuth } from '../context/AuthContext';
import { getRoleLabel } from '../router/ProtectedRoute';
import { isPhase2Enabled } from '../lib/phase';

interface DashboardLayoutProps {
  children: React.ReactNode;
  title?: string;
}

export const DashboardLayout: React.FC<DashboardLayoutProps> = ({ children, title }) => {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const { user } = useAuth();

  if (!isPhase2Enabled()) {
    // Phase 1: no sidebar at all - a fixed bottom tab bar instead, content
    // constrained to a mobile frame. This is the Merchant Mini App shell,
    // not the full admin console below.
    return (
      <div className="min-h-screen bg-slate-50">
        <header className="sticky top-0 z-20 bg-white border-b border-slate-200 shadow-xs">
          <div className="mx-auto flex h-14 max-w-[430px] items-center gap-2 px-4">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#0DA64B] text-white">
              <QrCode className="w-4 h-4" />
            </div>
            {title && <h1 className="text-base font-bold text-slate-900">{title}</h1>}
          </div>
        </header>
        <main className="mx-auto max-w-[430px] px-4 pb-24 pt-4">{children}</main>
        <MobileBottomNav />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      {/* Main content area - offset for sidebar on desktop */}
      <div className="lg:ml-64">
        {/* Top bar */}
        <header className="sticky top-0 z-20 bg-white border-b border-slate-200 shadow-xs">
          <div className="flex items-center justify-between h-16 px-4 sm:px-6">
            <div className="flex items-center gap-3">
              {/* Mobile menu button */}
              <button
                onClick={() => setSidebarOpen(true)}
                className="lg:hidden p-2 text-slate-500 hover:text-slate-900 hover:bg-slate-100 rounded-lg"
              >
                <Menu className="w-5 h-5" />
              </button>

              {/* Mobile brand */}
              <div className="lg:hidden flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-[#E60028] flex items-center justify-center text-white">
                  <QrCode className="w-4 h-4" />
                </div>
                <span className="font-bold text-sm">QRServe</span>
              </div>

              {/* Page title */}
              {title && (
                <h1 className="hidden sm:block text-lg font-bold text-slate-900">{title}</h1>
              )}
            </div>

            <div className="flex items-center gap-2">
              {user && (
                <div className="flex items-center gap-2 px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg">
                  <span className="text-xs font-bold text-slate-700">{user.email}</span>
                  <span className="text-[10px] font-bold px-1.5 py-0.5 bg-indigo-600 text-white rounded uppercase tracking-wider">
                    {getRoleLabel(user.role)}
                  </span>
                </div>
              )}
            </div>
          </div>
        </header>

        {/* Page content */}
        <main className="p-4 sm:p-6 lg:p-8">
          {children}
        </main>
      </div>
    </div>
  );
};
```

- [ ] **Step 3: Type-check**

Run: `npm run lint`
Expected: no errors.

- [ ] **Step 4: Manual verification**

With `VITE_ENABLE_PHASE_2` unset, log in as `MERCHANT_OWNER`. Expect: no sidebar anywhere (desktop or mobile viewport), a bottom tab bar with Dashboard/Menu & QR/Settings, content centered at ≤430px wide. Resize the browser to desktop width and confirm the layout stays mobile-framed (doesn't widen).

With `VITE_ENABLE_PHASE_2=true`, log in as `SUPER_ADMIN` or `MERCHANT_OWNER` and confirm the sidebar renders exactly as before this task, full width, no bottom nav.

- [ ] **Step 5: Commit**

```bash
git add src/components/MobileBottomNav.tsx src/components/DashboardLayout.tsx
git commit -m "feat(frontend): mobile bottom-tab shell for phase 1"
```

---

### Task 6: `CustomerMenuPage.tsx` — strip ordering/waiter-call UI in Phase 1

**Files:**
- Modify: `src/pages/CustomerMenuPage.tsx`

**Interfaces:**
- Consumes: `isPhase2Enabled` from `../lib/phase` (Task 1).
- Produces: nothing consumed elsewhere — leaf task.

No pure functions introduced; verify via `npm run lint` and manual check against a live table URL.

- [ ] **Step 1: Add the import**

At the top of `src/pages/CustomerMenuPage.tsx`, add to the existing import block:

```tsx
import { isPhase2Enabled } from '../lib/phase';
```

- [ ] **Step 2: Gate the header's table badge and subtitle**

Replace:

```tsx
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h1 className="truncate font-display text-2xl font-extrabold">
                {resolution.merchantName || target?.merchantSlug}
              </h1>
              <p className="mt-1 flex items-center gap-1.5 text-xs text-white/60">
                <MapPin className="h-3.5 w-3.5" aria-hidden />
                {resolution.branchName || effectiveBranchSlug}
              </p>
            </div>
            <span className="shrink-0 rounded-full bg-white/10 px-3 py-1.5 text-xs font-bold ring-1 ring-white/15">
              Table {resolution.tableNumber}
            </span>
          </div>
          <p className="mt-3 flex items-center gap-1.5 text-xs text-white/50">
            <Sparkles className="h-3.5 w-3.5" aria-hidden />
            Order from your table — no app, no queue.
          </p>
```

with:

```tsx
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h1 className="truncate font-display text-2xl font-extrabold">
                {resolution.merchantName || target?.merchantSlug}
              </h1>
              <p className="mt-1 flex items-center gap-1.5 text-xs text-white/60">
                <MapPin className="h-3.5 w-3.5" aria-hidden />
                {resolution.branchName || effectiveBranchSlug}
              </p>
            </div>
            {isPhase2Enabled() && (
              <span className="shrink-0 rounded-full bg-white/10 px-3 py-1.5 text-xs font-bold ring-1 ring-white/15">
                Table {resolution.tableNumber}
              </span>
            )}
          </div>
          <p className="mt-3 flex items-center gap-1.5 text-xs text-white/50">
            <Sparkles className="h-3.5 w-3.5" aria-hidden />
            {isPhase2Enabled() ? 'Order from your table — no app, no queue.' : 'Browse our menu.'}
          </p>
```

- [ ] **Step 3: Gate the order tracker**

Replace:

```tsx
        {/* Live tracker */}
        {placedOrderId && (
          <div className="-mt-5">
            <OrderProgress orderNumber={placedOrderNumber} status={liveStatus} connection={connection} />
          </div>
        )}

        {/* Search + sticky category rail */}
        <div className={placedOrderId ? 'mt-5' : '-mt-5'}>
```

with:

```tsx
        {/* Live tracker */}
        {isPhase2Enabled() && placedOrderId && (
          <div className="-mt-5">
            <OrderProgress orderNumber={placedOrderNumber} status={liveStatus} connection={connection} />
          </div>
        )}

        {/* Search + sticky category rail */}
        <div className={isPhase2Enabled() && placedOrderId ? 'mt-5' : '-mt-5'}>
```

- [ ] **Step 4: Gate the per-item Add/quantity controls**

Replace:

```tsx
                          {soldOut ? (
                            <span className="rounded-full bg-canvas px-3 py-1.5 text-xs font-semibold text-muted">
                              Unavailable
                            </span>
                          ) : line ? (
                            <div className="flex items-center gap-1 rounded-full bg-canvas p-1">
                              <button
                                onClick={() => changeQty(item, -1)}
                                aria-label={`Remove one ${item.name}`}
                                className="flex h-9 w-9 items-center justify-center rounded-full bg-surface shadow-sm transition-transform active:scale-90"
                              >
                                <Minus className="h-4 w-4" aria-hidden />
                              </button>
                              <span className="w-6 text-center text-sm font-extrabold tabular-nums">{line.quantity}</span>
                              <button
                                onClick={() => changeQty(item, 1)}
                                aria-label={`Add one ${item.name}`}
                                className="flex h-9 w-9 items-center justify-center rounded-full bg-brand text-brand-fg transition-transform active:scale-90"
                              >
                                <Plus className="h-4 w-4" aria-hidden />
                              </button>
                            </div>
                          ) : (
                            <button
                              onClick={() => changeQty(item, 1)}
                              className="flex items-center gap-1.5 rounded-full bg-ink px-4 py-2.5 text-sm font-bold text-white transition-transform active:scale-95"
                            >
                              <Plus className="h-4 w-4" aria-hidden /> Add
                            </button>
                          )}
```

with:

```tsx
                          {!isPhase2Enabled() ? null : soldOut ? (
                            <span className="rounded-full bg-canvas px-3 py-1.5 text-xs font-semibold text-muted">
                              Unavailable
                            </span>
                          ) : line ? (
                            <div className="flex items-center gap-1 rounded-full bg-canvas p-1">
                              <button
                                onClick={() => changeQty(item, -1)}
                                aria-label={`Remove one ${item.name}`}
                                className="flex h-9 w-9 items-center justify-center rounded-full bg-surface shadow-sm transition-transform active:scale-90"
                              >
                                <Minus className="h-4 w-4" aria-hidden />
                              </button>
                              <span className="w-6 text-center text-sm font-extrabold tabular-nums">{line.quantity}</span>
                              <button
                                onClick={() => changeQty(item, 1)}
                                aria-label={`Add one ${item.name}`}
                                className="flex h-9 w-9 items-center justify-center rounded-full bg-brand text-brand-fg transition-transform active:scale-90"
                              >
                                <Plus className="h-4 w-4" aria-hidden />
                              </button>
                            </div>
                          ) : (
                            <button
                              onClick={() => changeQty(item, 1)}
                              className="flex items-center gap-1.5 rounded-full bg-ink px-4 py-2.5 text-sm font-bold text-white transition-transform active:scale-95"
                            >
                              <Plus className="h-4 w-4" aria-hidden /> Add
                            </button>
                          )}
```

(Sold-out items still show no interactive control in Phase 1, same as before — the price/description remain visible either way since they sit outside this block.)

- [ ] **Step 5: Gate the service dock, cart bar, and cart sheet**

Replace:

```tsx
      <ServiceDock
        onRequest={sendRequest}
        pending={createRequest.isPending}
        sentType={requestSent}
        failed={createRequest.isError}
        offsetClass={cartCount > 0 ? 'bottom-28' : 'bottom-6'}
      />

      {/* Cart bar */}
      {cartCount > 0 && (
        <div className="safe-b fixed inset-x-0 bottom-0 z-40 border-t border-line bg-surface/95 px-5 pt-3 backdrop-blur">
          <div className="mx-auto max-w-2xl">
            <button
              onClick={() => setCartOpen(true)}
              className="flex w-full items-center justify-between rounded-2xl bg-brand px-5 py-4 text-brand-fg shadow-lift transition-transform active:scale-[0.99]"
            >
              <span className="flex items-center gap-2.5 text-sm font-bold">
                <span key={bumpKey} className="relative flex animate-pop items-center">
                  <ShoppingCart className="h-5 w-5" aria-hidden />
                  <span className="absolute -right-2 -top-2 flex h-5 min-w-5 items-center justify-center rounded-full bg-white px-1 text-[11px] font-extrabold text-brand">
                    {cartCount}
                  </span>
                </span>
                Review order
              </span>
              <span className="font-display text-base font-extrabold tabular-nums">
                {cartTotal.toLocaleString()} {currency}
              </span>
            </button>
          </div>
        </div>
      )}

      <CartSheet
        open={cartOpen}
        onClose={() => setCartOpen(false)}
        lines={cart}
        currency={currency}
        tableNumber={resolution.tableNumber}
        onChangeQty={(id, d) => {
          const l = cart.find((x) => x.productId === id);
          if (l) changeQty({ id, name: l.name, price: l.price }, d);
        }}
        onRemove={(id) => setCart((prev) => prev.filter((l) => l.productId !== id))}
        onConfirm={placeOrder}
        submitting={createOrder.isPending}
        errorMessage={
          createOrder.isError
            ? 'We couldn’t send your order. Check your connection and try again, or ask a waiter.'
            : null
        }
      />
    </div>
  );
};
```

with:

```tsx
      {isPhase2Enabled() && (
        <ServiceDock
          onRequest={sendRequest}
          pending={createRequest.isPending}
          sentType={requestSent}
          failed={createRequest.isError}
          offsetClass={cartCount > 0 ? 'bottom-28' : 'bottom-6'}
        />
      )}

      {/* Cart bar */}
      {isPhase2Enabled() && cartCount > 0 && (
        <div className="safe-b fixed inset-x-0 bottom-0 z-40 border-t border-line bg-surface/95 px-5 pt-3 backdrop-blur">
          <div className="mx-auto max-w-2xl">
            <button
              onClick={() => setCartOpen(true)}
              className="flex w-full items-center justify-between rounded-2xl bg-brand px-5 py-4 text-brand-fg shadow-lift transition-transform active:scale-[0.99]"
            >
              <span className="flex items-center gap-2.5 text-sm font-bold">
                <span key={bumpKey} className="relative flex animate-pop items-center">
                  <ShoppingCart className="h-5 w-5" aria-hidden />
                  <span className="absolute -right-2 -top-2 flex h-5 min-w-5 items-center justify-center rounded-full bg-white px-1 text-[11px] font-extrabold text-brand">
                    {cartCount}
                  </span>
                </span>
                Review order
              </span>
              <span className="font-display text-base font-extrabold tabular-nums">
                {cartTotal.toLocaleString()} {currency}
              </span>
            </button>
          </div>
        </div>
      )}

      {isPhase2Enabled() && (
        <CartSheet
          open={cartOpen}
          onClose={() => setCartOpen(false)}
          lines={cart}
          currency={currency}
          tableNumber={resolution.tableNumber}
          onChangeQty={(id, d) => {
            const l = cart.find((x) => x.productId === id);
            if (l) changeQty({ id, name: l.name, price: l.price }, d);
          }}
          onRemove={(id) => setCart((prev) => prev.filter((l) => l.productId !== id))}
          onConfirm={placeOrder}
          submitting={createOrder.isPending}
          errorMessage={
            createOrder.isError
              ? 'We couldn’t send your order. Check your connection and try again, or ask a waiter.'
              : null
          }
        />
      )}
    </div>
  );
};
```

- [ ] **Step 6: Type-check**

Run: `npm run lint`
Expected: no errors.

- [ ] **Step 7: Manual verification**

With `VITE_ENABLE_PHASE_2` unset, open an existing table's menu URL (e.g. `/menu/demo/main/1`). Expect: menu items show name/description/price with no Add button or quantity stepper, no cart bar ever appears, no floating service dock, no "Table N" badge, subtitle reads "Browse our menu." Edit a product's price via the merchant dashboard and reload the customer page — confirm the new price shows immediately (this was already true before this task; confirms nothing broke it).

With `VITE_ENABLE_PHASE_2=true`, reload the same URL and confirm Add buttons, cart bar, service dock, table badge, and order tracking all work exactly as before this task.

- [ ] **Step 8: Commit**

```bash
git add src/pages/CustomerMenuPage.tsx
git commit -m "feat(frontend): strip ordering/waiter-call UI from the public menu in phase 1"
```

---

## Plan complete

At this point: `npm run test:unit` passes (5 test files), `npm run lint` passes, and manual verification confirms Phase 1 (default) shows a MERCHANT_OWNER-only, bottom-nav, order-free experience while `VITE_ENABLE_PHASE_2=true` reproduces today's full app unchanged.

**Follow-on plans** (per the spec's decomposition, not part of this plan):
- Merchant Auth via Super App + Single Merchant QR
- `MerchantMenuDashboard` (replaces what `/merchant/menu` renders)
- API Gateway `phase1` profile + deployment scaling
