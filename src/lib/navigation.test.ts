/**
 * Pure-function tests for phase-aware role navigation. Run with
 * `npm run test:unit`.
 */
import assert from 'node:assert/strict';
import { getNavigationForRole, getRoleHomeRoute, ROLE_NAVIGATION } from './navigation';
import { isRoleAllowedInPhase } from './phase';

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

test('phase 1 gives MERCHANT_OWNER exactly Dashboard, Branches, Menu & QR, Reviews, Settings', () => {
  const items = getNavigationForRole('MERCHANT_OWNER', false);
  assert.deepEqual(
    items.map((i) => i.path),
    ['/merchant/dashboard', '/merchant/branches', '/merchant/menu', '/merchant/reviews', '/merchant/settings'],
  );
});

test('phase 1 gives SUPER_ADMIN exactly Back Office, Merchants, Settings', () => {
  const items = getNavigationForRole('SUPER_ADMIN', false);
  assert.deepEqual(
    items.map((i) => i.path),
    ['/admin/back-office', '/admin/merchants', '/admin/settings'],
  );
});

test('phase 1 gives every other role no navigation at all', () => {
  assert.deepEqual(getNavigationForRole('BRANCH_MANAGER', false), []);
  assert.deepEqual(getNavigationForRole('WAITER', false), []);
  assert.deepEqual(getNavigationForRole('KITCHEN', false), []);
});

test('phase 2 restores the full navigation for every role', () => {
  const merchantItems = getNavigationForRole('MERCHANT_OWNER', true);
  assert.equal(merchantItems.length, 10);
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
  assert.equal(getRoleHomeRoute('BRANCH_MANAGER', false), '/login');
});

test('SUPER_ADMIN lands on Back Office in phase 1', () => {
  assert.equal(getRoleHomeRoute('SUPER_ADMIN', false), '/admin/back-office');
});

test('phase 2 restores each role\'s real home route', () => {
  assert.equal(getRoleHomeRoute('SUPER_ADMIN', true), '/admin/dashboard');
  assert.equal(getRoleHomeRoute('WAITER', true), '/waiter/dashboard');
});

test('isRoleAllowedInPhase agrees with Phase 1 navigation for every known role', () => {
  for (const role of Object.keys(ROLE_NAVIGATION)) {
    const allowed = isRoleAllowedInPhase(role, false);
    const hasPhase1Nav = getNavigationForRole(role, false).length > 0;
    assert.equal(
      allowed,
      hasPhase1Nav,
      `${role}: isRoleAllowedInPhase=${allowed} but getNavigationForRole(...).length>0=${hasPhase1Nav}`,
    );
  }
});

if (failures > 0) {
  console.error(`\n${failures} test(s) failed`);
  process.exit(1);
}
console.log('\nall navigation tests passed');
