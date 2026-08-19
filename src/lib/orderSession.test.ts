/**
 * Tests for the guest order-tracking record. Run with `npm run test:unit`.
 *
 * This record is what lets a guest keep their order tracker across a browser
 * refresh. Getting the scope or expiry rules wrong shows one table's order on
 * another table's page, or restores a token the backend will only answer with
 * 401, so the rules are worth testing even though this project has no test
 * runner.
 */
import assert from 'node:assert/strict';
import { parseTrackedOrder, TRACKING_TTL_MS } from './orderSession';
import { mostAdvancedStatus, statusRank } from './orderStatus';

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

const NOW = 1_700_000_000_000;
const MERCHANT = '0dcb197c-2e06-4f27-a596-e6ec93a4f1c3';

function record(overrides: Record<string, unknown> = {}) {
  return JSON.stringify({
    orderId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
    orderNumber: 'ORD-1001',
    status: 'PREPARING',
    streamToken: 'header.payload.signature',
    merchantId: MERCHANT,
    tableId: 15,
    savedAt: NOW - 60_000,
    ...overrides,
  });
}

// ---- parseTrackedOrder ----

test('restores a record for the table being viewed', () => {
  const restored = parseTrackedOrder(record(), { merchantId: MERCHANT, tableId: 15, now: NOW });
  assert.equal(restored?.orderNumber, 'ORD-1001');
  assert.equal(restored?.status, 'PREPARING');
  assert.equal(restored?.streamToken, 'header.payload.signature');
});

test('nothing stored means nothing to restore', () => {
  assert.equal(parseTrackedOrder(null, { now: NOW }), null);
  assert.equal(parseTrackedOrder('', { now: NOW }), null);
});

test('garbage is ignored rather than thrown', () => {
  assert.equal(parseTrackedOrder('{not json', { now: NOW }), null);
  assert.equal(parseTrackedOrder('null', { now: NOW }), null);
  assert.equal(parseTrackedOrder('"a string"', { now: NOW }), null);
  assert.equal(parseTrackedOrder('{}', { now: NOW }), null);
});

test('a record without an order id is useless', () => {
  assert.equal(parseTrackedOrder(record({ orderId: '' }), { now: NOW }), null);
  assert.equal(parseTrackedOrder(record({ orderId: 42 }), { now: NOW }), null);
});

test("another table's order does not surface here", () => {
  // The guest moved tables, or scanned a neighbour's QR code.
  assert.equal(parseTrackedOrder(record(), { merchantId: MERCHANT, tableId: 9, now: NOW }), null);
});

test("another merchant's order does not surface here", () => {
  assert.equal(
    parseTrackedOrder(record(), { merchantId: 'ffffffff-ffff-ffff-ffff-ffffffffffff', tableId: 15, now: NOW }),
    null
  );
});

test('an expired record is dropped, because its token no longer authenticates', () => {
  assert.equal(
    parseTrackedOrder(record({ savedAt: NOW - TRACKING_TTL_MS }), { merchantId: MERCHANT, tableId: 15, now: NOW }),
    null
  );
  assert.notEqual(
    parseTrackedOrder(record({ savedAt: NOW - TRACKING_TTL_MS + 1000 }), { merchantId: MERCHANT, tableId: 15, now: NOW }),
    null
  );
});

test('a record stamped in the future is dropped', () => {
  // A device clock correction must not hand back a record that outlives the token.
  assert.equal(
    parseTrackedOrder(record({ savedAt: NOW + 10 * 60_000 }), { merchantId: MERCHANT, tableId: 15, now: NOW }),
    null
  );
});

test('an unknown scope restores the record, so the page can render before resolution lands', () => {
  const restored = parseTrackedOrder(record(), { now: NOW });
  assert.equal(restored?.tableId, 15);
});

test('a missing status is null rather than a bogus step', () => {
  const restored = parseTrackedOrder(record({ status: undefined }), { now: NOW });
  assert.equal(restored?.status, null);
});

// ---- status ranking ----

test('the lifecycle ranks in the order the backend advances it', () => {
  assert.ok(statusRank('PENDING') < statusRank('ACCEPTED'));
  assert.ok(statusRank('ACCEPTED') < statusRank('PREPARING'));
  assert.ok(statusRank('PREPARING') < statusRank('READY'));
  assert.ok(statusRank('READY') < statusRank('DELIVERED'));
  assert.ok(statusRank('DELIVERED') < statusRank('PAID'));
});

test('an unknown or absent status ranks below every real one', () => {
  assert.equal(statusRank(null), -1);
  assert.equal(statusRank(undefined), -1);
  assert.equal(statusRank('SERVED'), -1);
  assert.ok(statusRank('SERVED') < statusRank('PENDING'));
});

test('the furthest-along status wins, whichever channel reported it', () => {
  // A poll answering PENDING must not undo a push that already said READY.
  assert.equal(mostAdvancedStatus('PENDING', 'READY'), 'READY');
  assert.equal(mostAdvancedStatus('READY', 'PENDING'), 'READY');
  assert.equal(mostAdvancedStatus(null, 'PREPARING', undefined), 'PREPARING');
});

test('progress never walks backwards on a cancellation', () => {
  assert.equal(mostAdvancedStatus('READY', 'CANCELLED'), 'CANCELLED');
});

test('no recognised status means no status', () => {
  assert.equal(mostAdvancedStatus(null, undefined), null);
  assert.equal(mostAdvancedStatus('SERVED'), null);
});

test('a lowercase status from any channel still ranks', () => {
  assert.equal(mostAdvancedStatus('preparing', null), 'PREPARING');
});

if (failures > 0) {
  console.error(`\n${failures} test(s) failed`);
  process.exit(1);
}
console.log('\nall order session tests passed');
