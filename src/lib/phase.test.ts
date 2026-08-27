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
