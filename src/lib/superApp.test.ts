/**
 * Pure-function tests for reading the M-PESA Super App handoff token from
 * the URL. Run with `npm run test:unit`.
 *
 * No real Super App integration contract exists yet - this is a placeholder
 * handoff mechanism (a query param), matching the backend's dev-fake port.
 * See docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §5.
 */
import assert from 'node:assert/strict';
import { getSuperAppToken } from './superApp';

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

test('extracts the token from the query string', () => {
  assert.equal(getSuperAppToken('?superapp_token=abc123'), 'abc123');
});

test('returns null when the param is absent', () => {
  assert.equal(getSuperAppToken(''), null);
  assert.equal(getSuperAppToken('?other=1'), null);
});

test('returns null for a blank or whitespace-only token value', () => {
  assert.equal(getSuperAppToken('?superapp_token='), null);
  assert.equal(getSuperAppToken('?superapp_token=%20'), null);
});

test('works alongside other query params', () => {
  assert.equal(getSuperAppToken('?utm_source=x&superapp_token=abc123&ref=y'), 'abc123');
});

if (failures > 0) {
  console.error(`\n${failures} test(s) failed`);
  process.exit(1);
}
console.log('\nall superApp tests passed');
