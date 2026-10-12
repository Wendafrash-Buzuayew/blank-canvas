/**
 * Tests for which /auth/refresh failures end the session. Run with
 * `npm run test:unit`.
 *
 * Clearing tokens on a transient failure (network down, gateway 5xx) logs a
 * user out for nothing; keeping them on a real rejection leaves a dead session.
 */
import assert from 'node:assert/strict';
import { isRefreshRejection } from './api';

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

test('400/401/403 from /auth/refresh end the session', () => {
  for (const status of [400, 401, 403]) assert.equal(isRefreshRejection(status), true, String(status));
});

test('network errors, 5xx and 429 keep the session', () => {
  for (const status of [null, 429, 500, 502, 503, 504]) assert.equal(isRefreshRejection(status), false, String(status));
});

if (failures > 0) {
  console.error(`\n${failures} test(s) failed`);
  process.exit(1);
}
console.log('\nall auth refresh tests passed');
