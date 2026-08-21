/**
 * Tests for QR display gating. Run with `npm run test:unit`.
 *
 * The component this backs used to fall back to a fabricated
 * `https://qrserve.com/menu/${slug}/${branchId || 1}/${tableId || 1}` when metadata
 * had not loaded — the wrong domain, with branch and table defaulted to 1. A merchant
 * could print and laminate that. These tests exist to keep the guess deleted.
 */
import assert from 'node:assert/strict';
import { canRenderQr, qrCaption, qrImageSrc } from './qrDisplay';

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

const READY = {
  base64Content: 'data:image/png;base64,iVBORw0KGgo=',
  payloadRaw: '000201010211...9A4D',
  terminalLabel: 'T42-1',
  profile: 'EMVCO',
};

test('renders when the server supplied an image', () => {
  assert.equal(canRenderQr(READY), true);
  assert.equal(qrImageSrc(READY), READY.base64Content);
});

test('renders nothing at all when metadata has not loaded', () => {
  assert.equal(canRenderQr(undefined), false);
  assert.equal(qrImageSrc(undefined), null);
});

test('renders nothing when the image is missing, rather than guessing a URL', () => {
  const noImage = { ...READY, base64Content: '' };
  assert.equal(canRenderQr(noImage), false);
  assert.equal(qrImageSrc(noImage), null);
});

test('a payment code is captioned with its terminal label', () => {
  // Staff need this to answer "which sticker is on table 15" without a database.
  assert.match(qrCaption(READY), /T42-1/);
});

test('a menu-url code is captioned as a menu link, not as a payment code', () => {
  assert.match(qrCaption({ ...READY, profile: 'MENU_URL' }), /menu/i);
});

test('an unloaded caption says so instead of inventing a label', () => {
  assert.match(qrCaption(undefined), /not provisioned|loading/i);
});

if (failures > 0) {
  console.error(`\n${failures} test(s) failed`);
  process.exit(1);
}
console.log('\nall QR display tests passed');
