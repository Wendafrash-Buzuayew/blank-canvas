/**
 * Client-side rasterised PDF export — the fallback path for a device that has
 * no working "print to PDF" (most notably iOS Safari, where window.print()
 * opens the AirPrint sheet with no direct save-as-PDF option, only an actual
 * printer). See src/lib/standee.ts for why the PRIMARY path is the browser's
 * native print pipeline instead of this: that gives vector text and a vector
 * QR, crisp at any size, while this rasterises the DOM to a bitmap first.
 * Use this only where the native path genuinely is not available to the
 * merchant — it is a deliberate quality trade-off, not a general replacement.
 */

// Dynamically imported inside exportPagesToPdf, not at module load — these
// two libraries together add several hundred KB, and every merchant who
// opens Menu Builder loads this module's exports (imageToDataUrl is used
// eagerly) whether or not they ever tap "Download PDF" on a phone. A static
// top-level import would ship that weight to all of them for a mobile-only,
// deliberately-a-fallback feature.

/**
 * Fetches an image and returns it as a base64 data URL, or null on any
 * failure (missing URL, network error, or a response with no CORS headers —
 * html2canvas "taints" its canvas on a cross-origin image it can't read back
 * as pixels, which would silently blank that image in the raster export).
 * Callers should treat null as "render without this image" — the studio
 * already has a monogram fallback for exactly this case — never as a reason
 * to fail the whole export.
 */
export async function imageToDataUrl(url: string): Promise<string | null> {
  try {
    const res = await fetch(url, { mode: 'cors' });
    if (!res.ok) return null;
    const blob = await res.blob();
    return await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(reader.error);
      reader.readAsDataURL(blob);
    });
  } catch {
    return null;
  }
}

export interface PdfPage {
  /** A fully rendered, life-size (scale 1) standee node — see StandeeSheet.tsx. */
  node: HTMLElement;
  widthMm: number;
  heightMm: number;
}

/**
 * Rasterises each page's DOM node and assembles them into one PDF, one page
 * per node at its exact physical millimetre size.
 *
 * `scale: 4` (roughly 380 DPI at these trim sizes) is chosen so the QR still
 * resolves cleanly for a scanner despite being a bitmap here rather than the
 * vector code the print path embeds — see the module comment above.
 */
export async function exportPagesToPdf(pages: PdfPage[], filename: string): Promise<void> {
  if (pages.length === 0) return;

  const [{ default: html2canvas }, { default: jsPDF }] = await Promise.all([
    import('html2canvas'),
    import('jspdf'),
  ]);

  let pdf: InstanceType<typeof jsPDF> | null = null;
  for (const page of pages) {
    const canvas = await html2canvas(page.node, {
      scale: 4,
      backgroundColor: '#ffffff',
      useCORS: true,
    });
    const imageData = canvas.toDataURL('image/png');
    const orientation = page.heightMm >= page.widthMm ? 'portrait' : 'landscape';

    if (!pdf) {
      pdf = new jsPDF({ unit: 'mm', format: [page.widthMm, page.heightMm], orientation });
    } else {
      pdf.addPage([page.widthMm, page.heightMm], orientation);
    }
    pdf.addImage(imageData, 'PNG', 0, 0, page.widthMm, page.heightMm);
  }

  pdf!.save(filename);
}
