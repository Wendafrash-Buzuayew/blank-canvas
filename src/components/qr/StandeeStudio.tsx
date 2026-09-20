import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Printer, Download, Copy, Check, ExternalLink, Scissors, Loader2, Lock, Banknote } from 'lucide-react';
import { paymentApi, type MerchantTier } from '../../lib/api';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { FormField } from '../ui/FormField';
import { ErrorState } from '../ui/States';
import { StandeeSheet, type StandeeContent } from './StandeeSheet';
import { StandeeBackSheet } from './StandeeBackSheet';
import { EthQrCard, ETH_QR_LANGUAGE_OPTIONS, type EthQrLanguage } from './EthQrCard';
import { useBranchMenuUrl } from '../../hooks/useApiData';
import { qrSvgString, qrPngDataUrl, downloadQrPng, downloadQrSvg } from '../../lib/qrRender';
import { imageToDataUrl, exportPagesToPdf } from '../../lib/standeePdfExport';
import {
  STANDEE_SIZES,
  STANDEE_SIZE_OPTIONS,
  MAX_STANDEES,
  BLEED_MM,
  pageSizeCss,
  tableLabels,
  qrFilename,
  slugifyForFilename,
  type StandeeSize,
} from '../../lib/standee';
import { friendlyError } from '../../lib/errors';

/**
 * QR & Standee Studio.
 *
 * Left: the controls. Right: the live preview, which IS the printed sheet —
 * see StandeeSheet. Downloading the PDF goes through the browser's own print
 * pipeline, which is what gives vector text, a vector QR and exact millimetre
 * trim sizes without adding a PDF library. See the header comment in
 * src/lib/standee.ts for why the backend's existing PDF endpoint is not used.
 *
 * Below `lg` (a phone), the two panes are no longer both on screen at once —
 * `mobileTab` switches between an "Edit" tab (the control form) and a
 * "Preview" tab (the sheet plus a floating print button), since stacking
 * both in one scrolling column left the preview buried under a long form.
 * At `lg`+ this state is inert; both panes always show side by side.
 */

const selectClasses =
  'h-11 w-full rounded-control border border-line bg-surface px-3 text-body-m text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark';

export interface StandeeStudioProps {
  open: boolean;
  onClose: () => void;
  merchantSlug: string;
  branchSlug: string;
  branchName: string;
  /** Needed for GET /api/payment/ethqr?branchId=..., not the URL-lookup paths above, which key off the slug. */
  branchId: number;
  restaurantName: string;
  logoUrl?: string | null;
  /** From the merchant record. Editable below just for this print job — not written back. */
  merchantAddress?: string | null;
  merchantPhone?: string | null;
  /**
   * Gates the promo banner, bulk table export and the ETHQR payment standee
   * behind Pro. Treated as FREE when omitted — restrictive by default is the
   * safe failure mode for a paywall, unlike most optional props here, so
   * this one has no "unrestricted" fallback.
   */
  merchantTier?: MerchantTier;
}

/** Safaricom Dark Green — the interactive brand green, and the default accent. */
const DEFAULT_ACCENT = '#00833E';

const ACCENT_PRESETS = [
  { label: 'Brand green', value: '#00833E' },
  { label: 'Safaricom red', value: '#F5333F' },
  { label: 'Ink', value: '#222222' },
  { label: 'Teal', value: '#00695C' },
  { label: 'Amber', value: '#8A5A00' },
];

export function StandeeStudio({
  open,
  onClose,
  merchantSlug,
  branchSlug,
  branchName,
  branchId,
  restaurantName,
  logoUrl,
  merchantAddress,
  merchantPhone,
  merchantTier = 'FREE',
}: StandeeStudioProps) {
  const isPro = merchantTier === 'PRO';
  const urlQuery = useBranchMenuUrl(open ? merchantSlug : null, open ? branchSlug : null);
  const menuUrl = urlQuery.data?.url ?? null;

  const [size, setSize] = useState<StandeeSize>('A5');
  const [headline, setHeadline] = useState('Scan for Menu & Specials');
  const [subtext, setSubtext] = useState('Point your phone camera here');
  const [promoTitle, setPromoTitle] = useState('');
  const [promoBody, setPromoBody] = useState('');
  const [accentColor, setAccentColor] = useState(DEFAULT_ACCENT);
  const [showLogo, setShowLogo] = useState(true);
  const [showCropMarks, setShowCropMarks] = useState(false);
  const [showUrlText, setShowUrlText] = useState(true);
  // Pre-filled from the merchant record, but local — a merchant may shorten
  // the address to fit a small A6 card without touching their real profile.
  const [address, setAddress] = useState(merchantAddress ?? '');
  const [phone, setPhone] = useState(merchantPhone ?? '');
  const [socialHandle, setSocialHandle] = useState('');
  const [showContactFooter, setShowContactFooter] = useState(true);
  // Payment QR (ETHQR, Pro tier) — fetched on demand via a "Generate" button
  // rather than automatically, since this is a real call to an external,
  // possibly rate-limited/metered Safaricom endpoint, not a local
  // computation like the menu QR.
  //
  // There is exactly ONE response shape: a bare, unbranded reusable QR
  // module plus the merchant profile Safaricom holds for the short code. The
  // surrounding card — banner, digit strip, name box, footer marks — is
  // composed locally by EthQrCard, which is what makes the language picker
  // below meaningful (the provider's response carries no language of its
  // own). There is no fixed-amount variant: a printed standee code is
  // reusable and open-amount by definition, and asking for an amount made
  // the provider answer with a pre-composed card in a different schema —
  // see SafaricomEthQrService's class comment.
  const [paymentQrEnabled, setPaymentQrEnabled] = useState(false);
  const [paymentLanguage, setPaymentLanguage] = useState<EthQrLanguage>('english');
  const [paymentQrImageSrc, setPaymentQrImageSrc] = useState<string | null>(null);
  // EthQrCard's merchantName/accountNumber/phone props read from here,
  // sourced from Safaricom's own response fields rather than the studio's
  // locally-edited merchant profile, so the card can never show something
  // the provider didn't itself confirm for this generation.
  const [paymentQrMeta, setPaymentQrMeta] = useState<{
    merchantName: string;
    accountNumber: string;
    phone: string | null;
    city: string | null;
  } | null>(null);
  const [paymentQrLoading, setPaymentQrLoading] = useState(false);
  const [paymentQrError, setPaymentQrError] = useState<string | null>(null);
  const [bulk, setBulk] = useState(false);
  const [tablePrefix, setTablePrefix] = useState('Table');
  const [fromTable, setFromTable] = useState(1);
  const [toTable, setToTable] = useState(10);
  const [copied, setCopied] = useState(false);
  // Mobile only (see the tab bar below `lg` in the JSX) — at `lg`+ both panes
  // show side by side regardless of this.
  const [mobileTab, setMobileTab] = useState<'edit' | 'preview'>('edit');

  const [qrSvg, setQrSvg] = useState<string | null>(null);
  const [qrError, setQrError] = useState<string | null>(null);

  // Tracks the preview pane's actual width so the standee scales to fit a
  // phone screen. Most merchants run this studio on their own handset, where
  // a fixed desktop-sized scale would push the card off the edge.
  const previewPaneRef = useRef<HTMLDivElement>(null);
  const [previewPaneWidth, setPreviewPaneWidth] = useState<number | null>(null);

  useEffect(() => {
    if (!open) return;
    const el = previewPaneRef.current;
    if (!el) return;
    const observer = new ResizeObserver(([entry]) => {
      if (entry) setPreviewPaneWidth(entry.contentRect.width);
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, [open]);

  // @page { size: var(--standee-page-size) } only resolves reliably against
  // the ROOT element's computed style — the custom property set inline on
  // the portaled .standee-print-root div below is NOT visible to the page
  // box at all in Chrome (a descendant declaring a custom property does not
  // propagate it up to :root). Without this, every print job silently fell
  // back to the "A5 portrait" default regardless of the merchant's actual
  // size choice, so an A6 (or bleed-padded) job printed onto an oversized
  // page with leftover space — exactly the gap the next standee sheet then
  // partially spilled into instead of starting cleanly on its own page.
  useEffect(() => {
    if (!open) return;
    const root = document.documentElement;
    root.style.setProperty('--standee-page-size', pageSizeCss(size, showCropMarks));
    return () => { root.style.removeProperty('--standee-page-size'); };
  }, [open, size, showCropMarks]);

  // Encode once per URL. The payload never changes as the merchant edits copy,
  // so re-encoding on every keystroke would be pure waste.
  useEffect(() => {
    if (!menuUrl) {
      setQrSvg(null);
      return;
    }
    let cancelled = false;
    qrSvgString(menuUrl)
      .then((svg) => { if (!cancelled) { setQrSvg(svg); setQrError(null); } })
      .catch((err) => { if (!cancelled) setQrError(friendlyError(err, 'Could not encode the QR code.')); });
    return () => { cancelled = true; };
  }, [menuUrl]);

  // Raster forms, prepared only for the mobile PDF-export path (see
  // handleDownloadPdf below) — the print and on-screen paths use the vector
  // qrSvg/logoUrl directly and never touch these.
  const [qrPngUrl, setQrPngUrl] = useState<string | null>(null);
  const [logoDataUrl, setLogoDataUrl] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const exportNodesRef = useRef<(HTMLDivElement | null)[]>([]);
  // Back-page (ETHQR) export copies, one per label, parallel to
  // exportNodesRef — kept as a separate array since a back page only exists
  // per label when the payment QR is actually enabled and ready.
  const exportBackNodesRef = useRef<(HTMLDivElement | null)[]>([]);

  useEffect(() => {
    if (!menuUrl) { setQrPngUrl(null); return; }
    let cancelled = false;
    qrPngDataUrl(menuUrl, 1024).then((url) => { if (!cancelled) setQrPngUrl(url); }).catch(() => {});
    return () => { cancelled = true; };
  }, [menuUrl]);

  useEffect(() => {
    if (!logoUrl) { setLogoDataUrl(null); return; }
    let cancelled = false;
    // A failed fetch (no CORS headers, network error, ...) just leaves this
    // null — the export then falls back to the monogram badge, same as the
    // print/preview paths do for a merchant with no logo at all.
    imageToDataUrl(logoUrl).then((url) => { if (!cancelled) setLogoDataUrl(url); }).catch(() => {});
    return () => { cancelled = true; };
  }, [logoUrl]);

  // Turning the toggle off clears any code already fetched, so a merchant
  // can't leave it enabled, flip Pro off mid-edit some other way, and still
  // have a stale payment QR baked into baseContent below.
  useEffect(() => {
    if (!paymentQrEnabled || !isPro) {
      setPaymentQrImageSrc(null);
      setPaymentQrMeta(null);
      setPaymentQrError(null);
    }
  }, [paymentQrEnabled, isPro]);

  // Same reasoning as logoDataUrl above: if extractEthQrImageSrc resolved a
  // remote (cross-origin) URL rather than a data URL, html2canvas's export
  // capture would risk a tainted canvas. Converted once here rather than
  // inline in the export JSX so both a successful and a failed conversion
  // are visible to handleDownloadPdf's caller, same as the logo.
  const [paymentQrExportSrc, setPaymentQrExportSrc] = useState<string | null>(null);
  useEffect(() => {
    if (!paymentQrImageSrc) { setPaymentQrExportSrc(null); return; }
    // Already a data URL — the common case, since extractEthQrImageSrc builds
    // one from Safaricom's raw base64 field. It carries no cross-origin risk
    // for html2canvas (nothing is fetched over the network to produce it), so
    // running it through imageToDataUrl's CORS fetch is pure downside: some
    // browsers reject fetch(dataUrl, { mode: 'cors' }) outright, which would
    // silently drop the payment QR from just this export path while it kept
    // showing fine in the live preview (which reads paymentQrImageSrc as-is).
    if (paymentQrImageSrc.startsWith('data:')) { setPaymentQrExportSrc(paymentQrImageSrc); return; }
    let cancelled = false;
    imageToDataUrl(paymentQrImageSrc).then((url) => { if (!cancelled) setPaymentQrExportSrc(url); }).catch(() => {});
    return () => { cancelled = true; };
  }, [paymentQrImageSrc]);

  const handleGeneratePaymentQr = async () => {
    setPaymentQrError(null);
    setPaymentQrLoading(true);
    try {
      // Typed field-for-field against the provider's schema, and the backend
      // has already guaranteed qrImageUrl is a usable <img src> and failed
      // the call outright if it was missing — so there is nothing to probe
      // for or fall back to here.
      const response = await paymentApi.getEthQr(branchId);
      setPaymentQrImageSrc(response.qrImageUrl);
      setPaymentQrMeta({
        merchantName: response.merchantName,
        accountNumber: response.accountNumber,
        phone: response.mobileNumber,
        // Carried through unresolved — EthQrCard applies the "ADDIS"
        // fallback, so the raw provider value stays visible to anything
        // else reading this state rather than being flattened here.
        city: response.city,
      });
    } catch (err) {
      setPaymentQrError(friendlyError(err, 'Could not generate the payment QR.'));
      setPaymentQrImageSrc(null);
      setPaymentQrMeta(null);
    } finally {
      setPaymentQrLoading(false);
    }
  };

  // Bulk export and the promo banner are Pro features (see StandeeStudioProps
  // .merchantTier). The relevant Toggle/fields below are also disabled so a
  // Free-tier merchant cannot turn them on in the first place, but gating the
  // actual content here too means a stale `bulk`/`promoTitle` value from
  // before a downgrade — or the disabled attribute being bypassed in
  // devtools — still can't produce a Pro-only card.
  const bulkActive = bulk && isPro;
  const labels = useMemo(
    () => (bulkActive ? tableLabels(fromTable, toTable, tablePrefix) : ['']),
    [bulkActive, fromTable, toTable, tablePrefix],
  );

  const rangeInvalid = bulkActive && labels.length === 0;

  const baseContent: Omit<StandeeContent, 'tableLabel'> = {
    size,
    headline,
    subtext,
    promoTitle: isPro ? promoTitle : '',
    promoBody: isPro ? promoBody : '',
    restaurantName,
    accentColor,
    logoUrl: logoUrl ?? null,
    showLogo,
    showCropMarks,
    showUrlText,
    address: address.trim() || null,
    phone: phone.trim() || null,
    socialHandle: socialHandle.trim(),
    showContactFooter,
  };

  // isPro-gated the same way as promo/bulk above, in addition to the toggle
  // already being disabled and cleared on downgrade (the effect above) —
  // belt and suspenders, same reasoning as those two. paymentQrMeta is
  // required as well as the image: EthQrCard's name/code/digit display reads
  // from it, and the two are only ever set together (see
  // handleGeneratePaymentQr).
  const hasPaymentBackPage = isPro && paymentQrEnabled && !!paymentQrImageSrc && !!paymentQrMeta;

  // Preview is scaled to fit the panel; print resets to 1:1 (see index.css).
  // The desktop-comfortable scale is capped further down whenever the pane is
  // narrower than that — a phone in portrait, most often — so the card is
  // never wider than the screen a merchant is actually holding.
  const desktopScale = size === 'A5' ? 0.62 : 0.8;
  const previewScale = useMemo(() => {
    if (!previewPaneWidth) return desktopScale;
    const MM_TO_PX = 96 / 25.4;
    const pad = showCropMarks ? BLEED_MM : 0;
    const sheetWidthPx = (STANDEE_SIZES[size].widthMm + pad * 2) * MM_TO_PX;
    const paneInsetPx = 32; // the pane's own left+right padding (p-4)
    const fitScale = Math.max(0.2, (previewPaneWidth - paneInsetPx) / sheetWidthPx);
    return Math.min(desktopScale, fitScale);
  }, [previewPaneWidth, size, showCropMarks, desktopScale]);

  const handleCopy = async () => {
    if (!menuUrl) return;
    try {
      await navigator.clipboard.writeText(menuUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard is permission-gated and unavailable over plain http on some
      // browsers. Selecting the visible URL text is the fallback, so failing
      // silently here is better than an alert the merchant cannot act on.
    }
  };

  // Mobile's "Download standee PDF" (see the floating button below) —
  // window.print() is the desktop path, but iOS Safari's print sheet has no
  // direct save-as-PDF option (only an actual AirPrint printer), so a phone
  // needs a real file produced without going through the browser dialog at
  // all. Rasterises the hidden, full-scale export copies below rather than
  // the live preview, since those are undistorted by the phone-width
  // fit-to-panel scale.
  const handleDownloadPdf = async () => {
    if (rangeInvalid) return;
    setExportError(null);
    setExporting(true);
    try {
      const pad = showCropMarks ? BLEED_MM : 0;
      const widthMm = STANDEE_SIZES[size].widthMm + pad * 2;
      const heightMm = STANDEE_SIZES[size].heightMm + pad * 2;
      // Front then back, per label — front[0], back[0], front[1], back[1],
      // ... — so a duplex-capable print of this PDF puts each table's menu
      // QR and payment QR on the two faces of the SAME physical sheet.
      const activeLabels = rangeInvalid ? [''] : labels;
      const pages: { node: HTMLDivElement; widthMm: number; heightMm: number }[] = [];
      activeLabels.forEach((_, i) => {
        const front = exportNodesRef.current[i];
        if (front) pages.push({ node: front, widthMm, heightMm });
        if (hasPaymentBackPage) {
          const back = exportBackNodesRef.current[i];
          if (back) pages.push({ node: back, widthMm, heightMm });
        }
      });
      await exportPagesToPdf(pages, `${slugifyForFilename(restaurantName) || 'standee'}_standee.pdf`);
    } catch (err) {
      setExportError(friendlyError(err, 'Could not generate the PDF. Try again, or use Print instead.'));
    } finally {
      setExporting(false);
    }
  };

  // What goes inside StandeeBackSheet's trim box — the locally composed
  // ETHQR card, always. Declared inline (not hoisted out) since it closes
  // over paymentQrMeta/paymentLanguage/size. Only ever called where
  // hasPaymentBackPage is true, which already established paymentQrMeta.
  const renderPaymentBackContent = (imageSrc: string): React.ReactNode => (
    <EthQrCard
      qrImageSrc={imageSrc}
      merchantName={paymentQrMeta!.merchantName}
      accountNumber={paymentQrMeta!.accountNumber}
      phone={paymentQrMeta!.phone}
      city={paymentQrMeta!.city}
      language={paymentLanguage}
      widthMm={STANDEE_SIZES[size].widthMm}
    />
  );

  return (
    <Modal
      open={open}
      onClose={onClose}
      presentation="fullscreen"
      title={`QR & Standee Studio · ${branchName}`}
      bodyClassName="flex min-h-0 flex-1 flex-col overflow-y-auto lg:flex-row lg:overflow-hidden"
    >
      {/* ---------------- Mobile tab bar ----------------
          Below `lg` the two panes are no longer stacked in one scrolling
          column — each one is a full tab, since a merchant on a phone
          otherwise has to scroll past the whole control form to see the
          preview (or vice versa). Sticky so it survives scrolling the long
          control list. Irrelevant at `lg`+, where both panes always show. */}
      <div role="tablist" aria-label="Standee studio" className="standee-no-print sticky top-0 z-10 flex shrink-0 border-b border-line bg-surface lg:hidden">
        <button
          type="button"
          role="tab"
          id="standee-tab-edit"
          aria-selected={mobileTab === 'edit'}
          aria-controls="standee-panel-edit"
          onClick={() => setMobileTab('edit')}
          className={`min-h-11 flex-1 border-b-2 px-3 py-3 text-label-m ${
            mobileTab === 'edit' ? 'border-brand-dark text-ink' : 'border-transparent text-muted'
          }`}
        >
          Edit design & details
        </button>
        <button
          type="button"
          role="tab"
          id="standee-tab-preview"
          aria-selected={mobileTab === 'preview'}
          aria-controls="standee-panel-preview"
          onClick={() => setMobileTab('preview')}
          className={`min-h-11 flex-1 border-b-2 px-3 py-3 text-label-m ${
            mobileTab === 'preview' ? 'border-brand-dark text-ink' : 'border-transparent text-muted'
          }`}
        >
          Live preview & download
        </button>
      </div>

      {/* ---------------- Left: controls ----------------
          Below `lg`, only shown on the "Edit" tab (see the tab bar above);
          the whole body scrolls as one column so a tall control list never
          starves the preview of height. At `lg`+ both panes always show,
          switching to independent scroll regions inside a fixed-height row. */}
      <div
        id="standee-panel-edit"
        role="tabpanel"
        aria-labelledby="standee-tab-edit"
        className={`standee-no-print shrink-0 border-line p-4 lg:block lg:min-h-0 lg:w-[26rem] lg:overflow-y-auto lg:border-r ${
          mobileTab === 'edit' ? 'block' : 'hidden'
        }`}
      >
        {urlQuery.isError && (
          <ErrorState
            message={friendlyError(urlQuery.error, 'We could not get this branch’s menu link.')}
            onRetry={() => urlQuery.refetch()}
          />
        )}

        {qrError && (
          <div role="alert" className="mb-4 rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink">
            {qrError}
          </div>
        )}

        <div className="space-y-4">
          {/* Menu link + scan test */}
          <div className="rounded-control border border-line bg-surface-2 p-3">
            <div className="text-label-s uppercase text-muted">Menu link</div>
            <div className="mt-1 break-all text-body-m text-ink">
              {urlQuery.isLoading ? 'Loading…' : (menuUrl ?? 'unavailable')}
            </div>
            <div className="mt-2 flex flex-wrap gap-2">
              <Button variant="secondary" onClick={handleCopy} disabled={!menuUrl}>
                {copied ? <Check className="h-4 w-4" aria-hidden="true" /> : <Copy className="h-4 w-4" aria-hidden="true" />}
                {copied ? 'Copied' : 'Copy link'}
              </Button>
              {menuUrl && (
                <a
                  href={menuUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex min-h-11 items-center gap-2 rounded-control border border-line bg-surface px-4 text-label-m text-ink transition-colors hover:bg-surface-2"
                >
                  <ExternalLink className="h-4 w-4" aria-hidden="true" />
                  Scan test
                </a>
              )}
            </div>
            <p className="mt-2 text-label-s text-muted">
              Scan test opens the same URL the QR encodes, in a new tab — the fastest way to confirm a code before
              printing a hundred of them.
            </p>
          </div>

          {/* QR downloads */}
          <div>
            <div className="mb-1 text-label-s uppercase text-muted">Download the code alone</div>
            <div className="flex flex-wrap gap-2">
              <Button
                variant="secondary"
                disabled={!menuUrl}
                onClick={() => menuUrl && downloadQrSvg(menuUrl, qrFilename(branchSlug, 'svg'))}
              >
                <Download className="h-4 w-4" aria-hidden="true" />
                SVG (vector)
              </Button>
              <Button
                variant="secondary"
                disabled={!menuUrl}
                onClick={() => menuUrl && downloadQrPng(menuUrl, qrFilename(branchSlug, 'png'), 2048)}
              >
                <Download className="h-4 w-4" aria-hidden="true" />
                PNG (2048px)
              </Button>
            </div>
          </div>

          <hr className="border-line" />

          {/* Size */}
          <div>
            <label className="mb-1 block text-label-m text-ink">Standee size</label>
            <select
              aria-label="Standee size"
              value={size}
              onChange={(e) => setSize(e.target.value as StandeeSize)}
              className={selectClasses}
            >
              {STANDEE_SIZE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
            <p className="mt-1 text-label-s text-muted">
              {STANDEE_SIZES[size].hint}
            </p>
          </div>

          {/* Copy */}
          <FormField
            label="Headline"
            maxLength={40}
            value={headline}
            onChange={(e) => setHeadline(e.target.value)}
            hint="The largest text on the card — read from about a metre away"
          />
          <FormField
            label="Subtext"
            maxLength={60}
            value={subtext}
            onChange={(e) => setSubtext(e.target.value)}
          />

          <div className="flex items-center gap-2">
            <span className="text-label-m text-ink">Promo / WiFi banner</span>
            {!isPro && <ProBadge />}
          </div>
          <FormField
            label="Promo heading"
            maxLength={30}
            placeholder="e.g. Wi-Fi, Happy hour"
            value={promoTitle}
            onChange={(e) => setPromoTitle(e.target.value)}
            disabled={!isPro}
          />
          <div>
            <label htmlFor="promo-body" className="mb-1 block text-label-m text-ink">
              Promo content
            </label>
            <textarea
              id="promo-body"
              rows={3}
              maxLength={160}
              value={promoBody}
              onChange={(e) => setPromoBody(e.target.value)}
              disabled={!isPro}
              placeholder={'Wi-Fi: guest\nPassword: latte2026'}
              className="w-full rounded-control border border-line bg-surface p-3 text-body-m text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark disabled:cursor-not-allowed disabled:opacity-50"
            />
            <p className="mt-1 text-label-s text-muted">
              {isPro
                ? 'Line breaks are kept. Pinned to the foot of the card, so it never pushes the QR out of place.'
                : 'Upgrade to Pro to add a promo or WiFi banner to the card footer.'}
            </p>
          </div>

          <hr className="border-line" />

          {/* Payment QR (Safaricom ETHQR) */}
          <Toggle
            id="std-payment-qr"
            checked={paymentQrEnabled}
            onChange={setPaymentQrEnabled}
            disabled={!isPro}
            label="Add a payment QR (M-PESA / ETHQR)"
            badge={!isPro && <ProBadge />}
            hint={
              isPro
                ? 'Prints on the BACK of the card — one code guests scan to view the menu, the other to pay via M-PESA. Leave the amount blank for open-amount payments.'
                : 'Upgrade to Pro to print a Scan-to-pay QR on the back of the card.'
            }
          />
          {isPro && paymentQrEnabled && (
            <>
              <div>
                <label className="mb-1 block text-label-m text-ink">Card language</label>
                <select
                  aria-label="ETHQR card language"
                  value={paymentLanguage}
                  onChange={(e) => setPaymentLanguage(e.target.value as EthQrLanguage)}
                  className={selectClasses}
                >
                  {ETH_QR_LANGUAGE_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
                <p className="mt-1 text-label-s text-muted">
                  Sets the header banner wording on the printed card. The QR itself is the same in every language, so
                  switching this does not need a regenerate.
                </p>
              </div>
              <Button
                type="button"
                variant="secondary"
                onClick={handleGeneratePaymentQr}
                loading={paymentQrLoading}
                fullWidth
              >
                <Banknote className="h-4 w-4" aria-hidden="true" />
                {paymentQrLoading ? 'Generating…' : paymentQrImageSrc ? 'Regenerate payment QR' : 'Generate payment QR'}
              </Button>
              {paymentQrError && (
                <div role="alert" className="rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink">
                  {paymentQrError}
                </div>
              )}
              {paymentQrImageSrc && !paymentQrError && (
                <p className="flex items-center gap-2 rounded-control bg-info-soft px-3 py-2 text-label-s text-ink">
                  <Check className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                  Payment QR ready — shown as a second, back-side page in the preview and included in downloads.
                </p>
              )}
            </>
          )}

          {/* Accent */}
          <div>
            <label className="mb-1 block text-label-m text-ink">Accent colour</label>
            <div className="flex flex-wrap items-center gap-2">
              {ACCENT_PRESETS.map((p) => (
                <button
                  key={p.value}
                  type="button"
                  onClick={() => setAccentColor(p.value)}
                  aria-label={p.label}
                  aria-pressed={accentColor === p.value}
                  title={p.label}
                  className={`h-11 w-11 rounded-pill border-2 ${accentColor === p.value ? 'border-ink' : 'border-line-strong'}`}
                  style={{ backgroundColor: p.value }}
                />
              ))}
              <input
                type="color"
                aria-label="Custom accent colour"
                value={accentColor}
                onChange={(e) => setAccentColor(e.target.value)}
                className="h-11 w-14 rounded-control border border-line-strong bg-surface"
              />
            </div>
          </div>

          <hr className="border-line" />

          {/* Contact & socials, printed as the 📍/📞 footer row. Pre-filled
              from the merchant record (see StandeeStudioProps) but edited
              locally, same as headline/promo — this print job only. */}
          <div>
            <label className="mb-1 block text-label-m text-ink">Contact & socials</label>
            <p className="text-label-s text-muted">
              Printed as a footer row under the QR. Pre-filled from your merchant profile — change it here for just
              this card without touching your profile.
            </p>
          </div>
          <FormField
            label="Address"
            maxLength={80}
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            placeholder="Bole Road, Near Friendship Building, Addis Ababa"
          />
          <FormField
            label="Phone"
            maxLength={30}
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="+251 911 123 456"
          />
          <FormField
            label="Social handle"
            maxLength={40}
            value={socialHandle}
            onChange={(e) => setSocialHandle(e.target.value)}
            placeholder="Follow us @TrattoriaAddis"
          />
          <Toggle
            id="std-contact"
            checked={showContactFooter}
            onChange={setShowContactFooter}
            label="Show address & phone in footer"
            hint={!address && !phone ? 'Add an address or phone above to show this row' : undefined}
          />

          {/* Toggles */}
          <div className="space-y-2">
            <Toggle
              id="std-logo"
              checked={showLogo}
              onChange={setShowLogo}
              label="Show restaurant logo"
              hint={logoUrl ? undefined : 'No logo on file yet — a monogram badge is used instead'}
            />
            <Toggle id="std-url" checked={showUrlText} onChange={setShowUrlText} label="Print the link as text" hint="A fallback for a guest whose camera will not scan" />
            <Toggle
              id="std-crop"
              checked={showCropMarks}
              onChange={setShowCropMarks}
              label="Crop / trim marks"
              hint="Adds 5mm bleed on each side. For commercial printing only."
            />
          </div>

          <hr className="border-line" />

          {/* Bulk */}
          <Toggle
            id="std-bulk"
            checked={bulk}
            onChange={setBulk}
            disabled={!isPro}
            label="One standee per table"
            badge={!isPro && <ProBadge />}
            hint={
              isPro
                ? 'Prints a numbered card for each table, as pages of one PDF'
                : 'Upgrade to Pro to export a numbered card per table'
            }
          />
          {bulk && (
            <>
              <div className="grid grid-cols-3 gap-2">
                <FormField label="Prefix" maxLength={12} value={tablePrefix} onChange={(e) => setTablePrefix(e.target.value)} />
                <FormField
                  label="From"
                  type="number"
                  value={String(fromTable)}
                  onChange={(e) => setFromTable(Number(e.target.value))}
                />
                <FormField
                  label="To"
                  type="number"
                  value={String(toTable)}
                  onChange={(e) => setToTable(Number(e.target.value))}
                />
              </div>
              {rangeInvalid ? (
                <div role="alert" className="rounded-control bg-warn-soft px-3 py-2 text-label-s text-ink">
                  Enter a range from 1 upward, with at most {MAX_STANDEES} tables.
                </div>
              ) : (
                <p className="text-label-s text-muted">
                  {labels.length} page{labels.length === 1 ? '' : 's'} — “{labels[0]}” to “{labels[labels.length - 1]}”.
                  Every card carries the same branch QR; the number is printed text.
                </p>
              )}
            </>
          )}

          {/* The one point where a per-table QR would be expected. Saying so
              here is better than letting a merchant assume it. */}
          {bulk && !rangeInvalid && (
            <p className="rounded-control bg-info-soft px-3 py-2 text-label-s text-ink">
              The digital menu is branch-level by design, so one code serves every table. Per-table codes are a
              separate feature (they carry a payment payload and must be provisioned per table).
            </p>
          )}
        </div>
      </div>

      {/* ---------------- Right: live preview ----------------
          Below `lg`, only shown on the "Preview" tab; extra bottom padding
          clears the floating print button added below. */}
      <div
        ref={previewPaneRef}
        id="standee-panel-preview"
        role="tabpanel"
        aria-labelledby="standee-tab-preview"
        className={`flex-1 overflow-x-auto bg-surface-2 p-4 pb-24 lg:block lg:min-h-0 lg:overflow-y-auto lg:pb-4 ${
          mobileTab === 'preview' ? 'block' : 'hidden'
        }`}
      >
        <div className="standee-no-print mb-3 flex flex-wrap items-center gap-2">
          <span className="text-label-s uppercase text-muted">
            Live preview · {STANDEE_SIZES[size].label}
            {showCropMarks ? ' + 5mm bleed' : ''}
          </span>
          {/* Desktop only — a phone gets the floating "Download PDF" button
              below instead, which produces a real file directly rather than
              going through a print dialog (see handleDownloadPdf). */}
          <div className="ml-auto hidden lg:block">
            <Button onClick={() => window.print()} disabled={!qrSvg || rangeInvalid}>
              {urlQuery.isLoading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Printer className="h-4 w-4" aria-hidden="true" />}
              {bulk ? `Print ${labels.length} standees` : 'Download standee PDF'}
            </Button>
          </div>
        </div>

        <p className="standee-no-print mb-4 hidden items-start gap-2 rounded-control bg-info-soft px-3 py-2 text-label-s text-ink lg:flex">
          <Scissors className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          Choose <strong>Save as PDF</strong> as the destination, and set margins to <strong>None</strong> and scale to{' '}
          <strong>100%</strong>. The page size is already set to {pageSizeCss(size, showCropMarks)} — the print dialog
          should not need to scale anything.
        </p>

        {exportError && (
          <div role="alert" className="standee-no-print mb-4 rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink lg:hidden">
            {exportError}
          </div>
        )}

        {/* On-screen only — scaled to fit the panel (see previewScale). Never
            targeted by print CSS; printing renders the separate, life-size
            portal below instead (see the header comment in index.css for why
            this can't be the same node the old code used for both jobs). */}
        <div
          className="standee-preview-root flex flex-col items-center gap-6"
        >
          {(rangeInvalid ? [''] : labels).map((label, i) => (
            <React.Fragment key={`${label}-${i}`}>
              <StandeeSheet
                content={{ ...baseContent, tableLabel: label }}
                qrSvg={qrSvg}
                menuUrl={menuUrl}
                scale={previewScale}
              />
              {hasPaymentBackPage && (
                <StandeeBackSheet size={size} showCropMarks={showCropMarks} scale={previewScale}>
                  {renderPaymentBackContent(paymentQrImageSrc!)}
                </StandeeBackSheet>
              )}
            </React.Fragment>
          ))}
        </div>
      </div>

      {/* Print target — portaled onto document.body so it truly is a direct
          child of <body>, which is what the @media print rules in index.css
          require to isolate it from the rest of the app (see that file's
          header comment for the blank-page bug this fixes). Always mounted
          while the studio is open and life-size (scale 1) — display:none on
          screen, revealed only by @media print. */}
      {open && createPortal(
        // --standee-page-size is NOT set here — see the useEffect above for
        // why an inline custom property on this (non-root) node is invisible
        // to the @page rule that actually needs it.
        <div className="standee-print-root flex flex-col items-center gap-6">
          {(rangeInvalid ? [''] : labels).map((label, i) => (
            <React.Fragment key={`${label}-${i}`}>
              <StandeeSheet
                content={{ ...baseContent, tableLabel: label }}
                qrSvg={qrSvg}
                menuUrl={menuUrl}
                scale={1}
              />
              {hasPaymentBackPage && (
                <StandeeBackSheet size={size} showCropMarks={showCropMarks} scale={1}>
                  {renderPaymentBackContent(paymentQrImageSrc!)}
                </StandeeBackSheet>
              )}
            </React.Fragment>
          ))}
        </div>,
        document.body,
      )}

      {/* Hidden export copies — off-screen (not display:none, which
          html2canvas cannot rasterise), life-size, and built from the RASTER
          QR/logo rather than the vector ones above: html2canvas's own inline
          SVG support is inconsistent, and a cross-origin logo <img> would
          taint its canvas without a same-origin data URL. Only read by
          handleDownloadPdf (the mobile "Download PDF" button). */}
      {open && (
        <div style={{ position: 'fixed', top: 0, left: '-99999px', pointerEvents: 'none' }} aria-hidden="true">
          {(rangeInvalid ? [''] : labels).map((label, i) => (
            <React.Fragment key={`export-${label}-${i}`}>
              <div ref={(el) => { exportNodesRef.current[i] = el; }}>
                <StandeeSheet
                  // A same-origin data URL or nothing — never the original
                  // logoUrl here, since an untranslated cross-origin <img>
                  // risks tainting html2canvas's capture. A merchant whose
                  // logo failed to convert gets the monogram badge instead
                  // (see StandeeSheet's showLogo fallback).
                  content={{ ...baseContent, logoUrl: logoDataUrl, tableLabel: label }}
                  qrSvg={null}
                  qrImageSrc={qrPngUrl}
                  menuUrl={menuUrl}
                  scale={1}
                />
              </div>
              {hasPaymentBackPage && (
                <div ref={(el) => { exportBackNodesRef.current[i] = el; }}>
                  <StandeeBackSheet size={size} showCropMarks={showCropMarks} scale={1}>
                    {/* The CORS-safe converted copy, same reasoning as
                        logoDataUrl above — see the paymentQrExportSrc effect. */}
                    {renderPaymentBackContent(paymentQrExportSrc ?? paymentQrImageSrc!)}
                  </StandeeBackSheet>
                </div>
              )}
            </React.Fragment>
          ))}
        </div>
      )}

      {/* Floating download button — mobile "Preview" tab only. Produces a
          real .pdf file directly (handleDownloadPdf), rather than
          window.print(): iOS Safari's print sheet has no direct save-as-PDF
          option, so a phone can't depend on the same dialog desktop uses.
          Not rendered at all on the "Edit" tab, rather than merely hidden,
          so the modal's focus trap never lands Tab on an invisible control. */}
      {mobileTab === 'preview' && (
        <div className="standee-no-print fixed inset-x-4 bottom-4 z-10 lg:hidden">
          <Button
            fullWidth
            size="lg"
            className="shadow-[var(--shadow-lift)]"
            onClick={handleDownloadPdf}
            loading={exporting}
            disabled={!qrPngUrl || rangeInvalid || exporting}
          >
            {!exporting && <Download className="h-4 w-4" aria-hidden="true" />}
            {exporting ? 'Preparing PDF…' : bulk ? `Download ${labels.length} standees` : 'Download standee PDF'}
          </Button>
        </div>
      )}
    </Modal>
  );
}

function Toggle({
  id,
  checked,
  onChange,
  label,
  hint,
  disabled,
  badge,
}: {
  id: string;
  checked: boolean;
  onChange: (next: boolean) => void;
  label: string;
  hint?: string;
  disabled?: boolean;
  badge?: React.ReactNode;
}) {
  return (
    <div>
      <label htmlFor={id} className="flex min-h-11 items-center gap-2">
        <input
          id={id}
          type="checkbox"
          checked={checked && !disabled}
          disabled={disabled}
          onChange={(e) => onChange(e.target.checked)}
          className="h-4 w-4 rounded-[4px] border-line-strong accent-[var(--color-brand-dark)]"
        />
        <span className={`text-label-m ${disabled ? 'text-muted' : 'text-ink'}`}>{label}</span>
        {badge}
      </label>
      {hint && <p className="text-label-s text-muted">{hint}</p>}
    </div>
  );
}

/** Small lock badge marking a Pro-only control. */
function ProBadge() {
  return (
    <span className="inline-flex items-center gap-1 rounded-pill bg-warn-soft px-2 py-0.5 text-label-s text-ink">
      <Lock className="h-3 w-3" aria-hidden="true" />
      Pro
    </span>
  );
}
