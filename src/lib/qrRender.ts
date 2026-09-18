/**
 * Encodes a URL into QR artwork, client-side, for display and print.
 *
 * WHAT THIS IS ALLOWED TO DO, and what it is not:
 *
 * src/lib/qrDisplay.ts states the rule for TABLE QRs — the frontend shows the
 * image the backend minted and never constructs a code, because a table QR is
 * an EMVCo money instrument and a fabricated one is a laminated 404. That rule
 * still stands and this module does not touch it.
 *
 * This module encodes a URL the BACKEND produced. The caller fetches the
 * canonical, signed branch-menu URL from
 * GET /api/qr/digital-menu/{merchant}/{branch}/url and passes the string here
 * verbatim. The payload is still the backend's; only the rasterising and
 * vectorising happen here — which is what makes a crisp SVG at print
 * resolution and a 2048px PNG possible without a new backend endpoint per
 * size.
 *
 * Never call these with a URL assembled in the browser.
 */

import QRCode from 'qrcode';

/**
 * Error correction H, matching QrGeneratorService.renderPng exactly.
 *
 * The two renderers must agree: a merchant could scan-test the on-screen code
 * and then print from the other path, and a different correction level would
 * mean a visibly different code for the same URL. H also survives the scuffing
 * a laminated table card actually gets.
 */
const OPTIONS = {
  errorCorrectionLevel: 'H',
  margin: 2,
} as const;

/**
 * Vector QR, for print. An SVG stays sharp at any DPI, which is the whole
 * reason the standee is printed from the browser rather than from a
 * server-rasterised PNG.
 */
export function qrSvgString(payload: string): Promise<string> {
  return QRCode.toString(payload, { ...OPTIONS, type: 'svg' });
}

/**
 * Raster QR as a data URL.
 *
 * `width` is in device pixels. 2048 is offered for download because a merchant
 * may hand the file to a sign printer, where anything under ~1200px across an
 * A5 card starts to show stair-stepping on the module edges.
 */
export function qrPngDataUrl(payload: string, width = 1024): Promise<string> {
  return QRCode.toDataURL(payload, { ...OPTIONS, width, type: 'image/png' });
}

/** Triggers a browser download of `content` without leaving the page. */
export function downloadBlob(content: BlobPart, filename: string, mimeType: string): void {
  const url = URL.createObjectURL(new Blob([content], { type: mimeType }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  // Revoked on the next tick rather than immediately: Safari has not finished
  // reading the blob when click() returns, and revoking synchronously produces
  // a zero-byte file.
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

export async function downloadQrSvg(payload: string, filename: string): Promise<void> {
  downloadBlob(await qrSvgString(payload), filename, 'image/svg+xml');
}

export async function downloadQrPng(payload: string, filename: string, width = 2048): Promise<void> {
  const dataUrl = await qrPngDataUrl(payload, width);
  // A data: URL cannot carry a download filename reliably across browsers, so
  // it is converted back to a blob first.
  const base64 = dataUrl.split(',')[1] ?? '';
  const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  downloadBlob(bytes, filename, 'image/png');
}
