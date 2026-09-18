/**
 * Print geometry and content rules for a table standee.
 *
 * Pure functions only — no React, no DOM — so the sizing and the filename
 * conventions can be asserted directly (see standee.test.ts). The rendering
 * lives in src/components/qr/StandeeStudio.tsx.
 *
 * WHY THE BROWSER PRINTS THE PDF, not the backend:
 *
 * qr-service already has POST /api/qr/export/pdf, and it does not work — it
 * hand-assembles PDF bytes as a string, declares /Filter /DCTDecode while
 * embedding PNG data, hardcodes a wrong /Length, and writes no xref table, so
 * no conforming reader opens the file. Fixing it properly means adding a real
 * PDF library to the backend and reimplementing text layout, font embedding
 * and vector graphics.
 *
 * The browser already has all of that. Printing a page whose @page size is set
 * in millimetres produces genuine vector output: real text (not rasterised), a
 * vector SVG QR that stays crisp at any DPI, and exact trim dimensions. It also
 * makes the on-screen preview and the printed artefact THE SAME DOM, so they
 * cannot drift — which is the whole point of a preview.
 *
 * The trade-off, and it is a real one: the merchant goes through the browser's
 * print dialog and chooses "Save as PDF" rather than receiving a file
 * automatically. That is one extra click in exchange for output that is
 * actually valid.
 */

export type StandeeSize = 'A5' | 'A6' | 'TENT_4X6';

export interface StandeeGeometry {
  /** Trim width in millimetres. */
  widthMm: number;
  /** Trim height in millimetres. */
  heightMm: number;
  label: string;
  hint: string;
}

/**
 * Trim sizes. A5/A6 are ISO; the 4x6 tent card is the common acrylic-holder
 * insert size in inches, converted at exactly 25.4 mm/in.
 */
export const STANDEE_SIZES: Record<StandeeSize, StandeeGeometry> = {
  A5: {
    widthMm: 148,
    heightMm: 210,
    label: 'A5 portrait',
    hint: '148 × 210 mm — the usual tabletop acrylic stand',
  },
  A6: {
    widthMm: 105,
    heightMm: 148,
    label: 'A6 portrait',
    hint: '105 × 148 mm — smaller, for crowded tables',
  },
  TENT_4X6: {
    widthMm: 101.6,
    heightMm: 152.4,
    label: '4 × 6 in tent card',
    hint: '101.6 × 152.4 mm — folds to stand unaided',
  },
};

export const STANDEE_SIZE_OPTIONS: { value: StandeeSize; label: string; hint: string }[] = (
  Object.keys(STANDEE_SIZES) as StandeeSize[]
).map((value) => ({ value, label: STANDEE_SIZES[value].label, hint: STANDEE_SIZES[value].hint }));

/**
 * Extra paper around the trim box for crop marks.
 *
 * 5mm on every side. Commercial printers need somewhere to put the trim line;
 * without bleed the marks would sit inside the artwork and be printed onto the
 * finished card.
 */
export const BLEED_MM = 5;

export function sheetSizeMm(size: StandeeSize, withCropMarks: boolean): { widthMm: number; heightMm: number } {
  const { widthMm, heightMm } = STANDEE_SIZES[size];
  const pad = withCropMarks ? BLEED_MM * 2 : 0;
  return { widthMm: widthMm + pad, heightMm: heightMm + pad };
}

/**
 * The `@page size` value for a given standee.
 *
 * Emitted as a CSS custom property rather than a Tailwind class because
 * `@page` cannot be targeted by a utility, and the value is numeric and
 * unbounded — exactly the case where a literal class lookup does not apply.
 */
export function pageSizeCss(size: StandeeSize, withCropMarks: boolean): string {
  const { widthMm, heightMm } = sheetSizeMm(size, withCropMarks);
  return `${round(widthMm)}mm ${round(heightMm)}mm`;
}

function round(n: number): number {
  // Two decimals is finer than any printer resolves, and avoids 101.60000000001.
  return Math.round(n * 100) / 100;
}

// ---- Table numbering ------------------------------------------------------

/**
 * Expands a table-number range into printed labels.
 *
 * Zero-padded to the width of the largest number so "Table 05" and "Table 12"
 * line up on the shelf — a detail that only shows up once a restaurant has
 * more than nine tables.
 *
 * Returns an empty array for an invalid or inverted range rather than throwing:
 * the caller is a live form, and a half-typed range must not blow up the
 * preview.
 */
export function tableLabels(from: number, to: number, prefix = 'Table'): string[] {
  if (!Number.isInteger(from) || !Number.isInteger(to)) return [];
  if (from < 1 || to < from) return [];
  if (to - from + 1 > MAX_STANDEES) return [];
  const width = String(to).length;
  const out: string[] = [];
  for (let n = from; n <= to; n++) {
    out.push(`${prefix} ${String(n).padStart(width, '0')}`.trim());
  }
  return out;
}

/**
 * A print job is one DOM containing every page, so a huge range would build
 * thousands of nodes and hang the tab before the print dialog ever opened.
 * 200 is far past any single restaurant's table count.
 */
export const MAX_STANDEES = 200;

// ---- Downloads ------------------------------------------------------------

/** Filesystem-safe, lowercase, no double separators. */
export function slugifyForFilename(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60);
}

export function qrFilename(branchSlug: string, extension: 'png' | 'svg'): string {
  const base = slugifyForFilename(branchSlug) || 'branch';
  return `qr-${base}.${extension}`;
}
