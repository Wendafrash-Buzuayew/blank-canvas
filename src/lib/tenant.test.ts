/**
 * Pure-function tests for tenant host resolution. Run with `npm run test:unit`.
 *
 * These two functions decide which restaurant's menu a scanned QR code shows.
 * Getting them wrong means a customer at one cafe sees another cafe's menu, so
 * they are worth testing even though this project has no test runner.
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
  assert.equal(tenantSlugFromHost('sunrise.localtest.me', 'localtest.me:3000'), 'sunrise');
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
