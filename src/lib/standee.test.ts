// src/lib/standee.test.ts
import {
  STANDEE_SIZES,
  STANDEE_SIZE_OPTIONS,
  BLEED_MM,
  MAX_STANDEES,
  sheetSizeMm,
  pageSizeCss,
  tableLabels,
  slugifyForFilename,
  qrFilename,
} from './standee';

function ok(name: string, condition: boolean) {
  console.log(`  ${condition ? 'ok' : 'FAIL'}  ${name}`);
  if (!condition) process.exitCode = 1;
}

// ---- geometry -------------------------------------------------------------
// These are physical dimensions. A wrong number here is a stack of unusable
// printed cards, so they are asserted exactly rather than approximately.

ok('A5 is 148 x 210 mm', STANDEE_SIZES.A5.widthMm === 148 && STANDEE_SIZES.A5.heightMm === 210);
ok('A6 is 105 x 148 mm', STANDEE_SIZES.A6.widthMm === 105 && STANDEE_SIZES.A6.heightMm === 148);
// Asserted against the exact decimal values, NOT against `6 * 25.4` — that
// evaluates to 152.39999999999998 in IEEE-754, so comparing to it would fail
// against the correct constant. The stored values are the exact millimetre
// equivalents of 4 and 6 inches, to the precision a printer can use.
ok(
  '4x6 in is stored as exact millimetres (101.6 x 152.4)',
  STANDEE_SIZES.TENT_4X6.widthMm === 101.6 && STANDEE_SIZES.TENT_4X6.heightMm === 152.4,
);
ok(
  '...and those really are 4in and 6in to within a printer-irrelevant epsilon',
  Math.abs(STANDEE_SIZES.TENT_4X6.widthMm - 4 * 25.4) < 1e-9 &&
    Math.abs(STANDEE_SIZES.TENT_4X6.heightMm - 6 * 25.4) < 1e-9,
);
ok('every size is portrait (taller than wide)', STANDEE_SIZE_OPTIONS.every((o) => STANDEE_SIZES[o.value].heightMm > STANDEE_SIZES[o.value].widthMm));
ok('every size is offered in the picker', STANDEE_SIZE_OPTIONS.length === Object.keys(STANDEE_SIZES).length);

ok(
  'without crop marks the sheet is exactly the trim size',
  sheetSizeMm('A5', false).widthMm === 148 && sheetSizeMm('A5', false).heightMm === 210,
);
ok(
  'crop marks add bleed on all four sides, not two',
  sheetSizeMm('A5', true).widthMm === 148 + BLEED_MM * 2 &&
    sheetSizeMm('A5', true).heightMm === 210 + BLEED_MM * 2,
);

ok('the @page value is a valid two-length CSS size', pageSizeCss('A5', false) === '148mm 210mm');
ok('crop-marked A5 pages at 158x220', pageSizeCss('A5', true) === '158mm 220mm');
ok(
  'floating-point mm are rounded, not printed as 101.60000000000001',
  pageSizeCss('TENT_4X6', false) === '101.6mm 152.4mm',
);
ok(
  'every size produces a well-formed @page value',
  STANDEE_SIZE_OPTIONS.every((o) =>
    [true, false].every((crop) => /^\d+(\.\d+)?mm \d+(\.\d+)?mm$/.test(pageSizeCss(o.value, crop))),
  ),
);

// ---- table numbering ------------------------------------------------------

ok('a single table yields one label', tableLabels(1, 1).length === 1 && tableLabels(1, 1)[0] === 'Table 1');
ok('an inclusive range yields every table', tableLabels(1, 12).length === 12);
ok(
  'labels zero-pad to the width of the largest number so they align',
  tableLabels(8, 12)[0] === 'Table 08' && tableLabels(8, 12)[4] === 'Table 12',
);
ok('a single-digit range is not padded', tableLabels(1, 9)[0] === 'Table 1');
ok('a three-digit range pads to three', tableLabels(9, 100)[0] === 'Table 009');
ok('the prefix is configurable', tableLabels(1, 1, 'Booth')[0] === 'Booth 1');
ok('an empty prefix does not leave a leading space', tableLabels(1, 1, '')[0] === '1');

// A live form feeds this half-typed input; it must degrade, not throw.
ok('an inverted range yields nothing rather than throwing', tableLabels(10, 2).length === 0);
ok('a zero or negative start yields nothing', tableLabels(0, 5).length === 0 && tableLabels(-3, 5).length === 0);
ok('a non-integer yields nothing', tableLabels(1.5, 5).length === 0 && tableLabels(1, NaN).length === 0);
ok(
  `a range past the ${MAX_STANDEES}-page cap yields nothing rather than hanging the tab`,
  tableLabels(1, MAX_STANDEES).length === MAX_STANDEES && tableLabels(1, MAX_STANDEES + 1).length === 0,
);

// ---- filenames ------------------------------------------------------------

ok('a slug passes through unchanged', slugifyForFilename('bole-branch') === 'bole-branch');
ok('spaces and case are normalised', slugifyForFilename('Bole Branch') === 'bole-branch');
ok('punctuation collapses to a single separator', slugifyForFilename("Joe's  Diner!!") === 'joe-s-diner');
ok('leading and trailing separators are trimmed', slugifyForFilename('--Bole--') === 'bole');
ok('a non-Latin name does not produce an empty filename', qrFilename('ቦሌ', 'png') === 'qr-branch.png');
ok('the extension is applied', qrFilename('bole-branch', 'svg') === 'qr-bole-branch.svg');
ok('a long name is truncated', slugifyForFilename('x'.repeat(200)).length === 60);

console.log(process.exitCode ? 'FAILED' : 'all standee tests passed');
