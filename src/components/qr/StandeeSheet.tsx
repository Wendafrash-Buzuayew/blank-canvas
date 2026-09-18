import React from 'react';
import { STANDEE_SIZES, BLEED_MM, type StandeeSize } from '../../lib/standee';

/**
 * One printable standee.
 *
 * This component is the artefact. The studio's right-hand preview and the
 * printed page are the same element — the print stylesheet in index.css only
 * hides the surrounding app chrome and sets @page geometry, it does not redraw
 * anything. That is what makes "preview before you print" meaningful.
 *
 * DIMENSIONS ARE IN MILLIMETRES, deliberately, everywhere in this file. A
 * standee is a physical object: `mm` is resolution-independent, survives the
 * print pipeline unchanged, and means the on-screen preview at 100% is life
 * size. Using rem or px here would make the trim size depend on the browser's
 * font settings.
 */

export interface StandeeContent {
  size: StandeeSize;
  headline: string;
  subtext: string;
  promoTitle: string;
  promoBody: string;
  /** Printed under the QR, e.g. "Table 05". Empty for a single generic standee. */
  tableLabel: string;
  restaurantName: string;
  /** Accent band and rules. A hex string from the merchant's brand colour. */
  accentColor: string;
  logoUrl: string | null;
  showLogo: boolean;
  showCropMarks: boolean;
  /** Printed small at the foot so a guest can type it if scanning fails. */
  showUrlText: boolean;
  /** From the merchant record, not editable here. Null when the merchant has none on file. */
  address: string | null;
  phone: string | null;
  /** e.g. "Follow us @TrattoriaAddis". Free text, per print job like headline/promo. */
  socialHandle: string;
  /** Gates the 📍/📞 footer row. Has no effect when the merchant has neither. */
  showContactFooter: boolean;
}

export interface StandeeSheetProps {
  content: StandeeContent;
  /** Vector QR markup from qrSvgString(). Vector so it stays crisp at any DPI. */
  qrSvg: string | null;
  /**
   * A raster PNG data URL, used instead of `qrSvg` when set. Only the
   * html2canvas export path (see src/lib/standeePdfExport.ts) needs this —
   * html2canvas's own SVG support is inconsistent, so that path renders an
   * `<img>` it can rasterise reliably instead of the inline SVG the print and
   * on-screen preview paths use.
   */
  qrImageSrc?: string | null;
  /** The URL the QR encodes, printed as fallback text when showUrlText is on. */
  menuUrl: string | null;
  /** Screen-only scale for the preview panel. Print CSS forces this back to 1. */
  scale?: number;
}

/**
 * Crop marks sit in the bleed area, outside the trim box, so the trim line
 * never prints onto the finished card. Drawn as four L-shaped corner rules
 * rather than full crossing lines, which is what a trade printer expects.
 */
function CropMarks() {
  const arm = `${BLEED_MM - 1}mm`;
  const rule = '0.25mm solid #222';
  const corners = [
    { top: 0, left: 0, borderTop: rule, borderLeft: rule },
    { top: 0, right: 0, borderTop: rule, borderRight: rule },
    { bottom: 0, left: 0, borderBottom: rule, borderLeft: rule },
    { bottom: 0, right: 0, borderBottom: rule, borderRight: rule },
  ];
  return (
    <>
      {corners.map((style, i) => (
        <span
          key={i}
          aria-hidden="true"
          style={{ position: 'absolute', width: arm, height: arm, ...style }}
        />
      ))}
    </>
  );
}

export function StandeeSheet({ content, qrSvg, qrImageSrc, menuUrl, scale = 1 }: StandeeSheetProps) {
  const trim = STANDEE_SIZES[content.size];
  const pad = content.showCropMarks ? BLEED_MM : 0;
  const widthMm = trim.widthMm + pad * 2;
  const heightMm = trim.heightMm + pad * 2;

  // The QR is sized as a fraction of the trim width rather than a fixed mm
  // value, so it stays proportionate across A6 and A5 instead of swamping the
  // smaller card.
  const qrSideMm = trim.widthMm * 0.52;

  // `transform: scale()` shrinks what is painted but NOT the box's footprint
  // in normal flow — the flex column of standees would still reserve full
  // life-size width for every card, forcing the phone-width preview pane into
  // horizontal scroll even though the card itself looks small enough to fit.
  // This wrapper is sized to the SCALED footprint so the flex layout (and the
  // pane's fit-to-width math in StandeeStudio) sees the shrunk size. Print
  // CSS below forces both the wrapper and the transform back to 1:1.
  return (
    <div className="standee-scale-wrap" style={{ width: `${widthMm * scale}mm`, height: `${heightMm * scale}mm` }}>
      <div
        className="standee-sheet"
        style={{
          width: `${widthMm}mm`,
          height: `${heightMm}mm`,
          transform: scale === 1 ? undefined : `scale(${scale})`,
          transformOrigin: 'top left',
        }}
      >
        {content.showCropMarks && <CropMarks />}

        {/* The trim box. Everything inside prints on the finished card. */}
        <div
          style={{
            position: 'absolute',
            top: `${pad}mm`,
            left: `${pad}mm`,
            width: `${trim.widthMm}mm`,
            height: `${trim.heightMm}mm`,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            textAlign: 'center',
            padding: '10mm 8mm',
            boxSizing: 'border-box',
          }}
        >
        {/* Accent band across the head of the card — the one place the brand
            colour appears at size, so it reads across a room. */}
        <div
          aria-hidden="true"
          style={{
            position: 'absolute',
            top: `${pad === 0 ? 0 : 0}mm`,
            left: 0,
            right: 0,
            height: '6mm',
            backgroundColor: content.accentColor,
          }}
        />

        {content.showLogo && (
          content.logoUrl ? (
            <img
              src={content.logoUrl}
              alt=""
              style={{
                width: '18mm',
                height: '18mm',
                objectFit: 'contain',
                marginTop: '2mm',
                marginBottom: '3mm',
              }}
            />
          ) : (
            // No logo on file yet — a monogram badge beats leaving a blank
            // gap where a guest expects a mark.
            <div
              aria-hidden="true"
              style={{
                width: '18mm',
                height: '18mm',
                borderRadius: '50%',
                marginTop: '2mm',
                marginBottom: '3mm',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: content.accentColor,
                color: '#ffffff',
                fontSize: '7mm',
                fontWeight: 800,
              }}
            >
              {(content.restaurantName.trim().charAt(0) || '?').toUpperCase()}
            </div>
          )
        )}

        {content.restaurantName && (
          <div
            style={{
              fontSize: '4mm',
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              color: '#5F6368',
              marginTop: content.showLogo ? 0 : '4mm',
            }}
          >
            {content.restaurantName}
          </div>
        )}

        {/* Headline is the largest text on the card: it is what a seated guest
            reads first, from about a metre away. */}
        <h2
          style={{
            fontSize: '8mm',
            lineHeight: 1.1,
            fontWeight: 900,
            margin: '3mm 0 0',
            color: '#222222',
          }}
        >
          {content.headline}
        </h2>

        {content.subtext && (
          <p style={{ fontSize: '3.6mm', lineHeight: 1.3, margin: '2mm 0 0', color: '#5F6368' }}>
            {content.subtext}
          </p>
        )}

        {/* Menu QR, rendered from the backend's signed URL as inline SVG so it
            is vector on paper — a raster code shows stair-stepped module
            edges at print resolution, which is exactly where scanners
            struggle. The payment QR, when enabled, is a separate back page —
            see StandeeBackSheet — printed double-sided with this front page
            rather than squeezed alongside this code. */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', margin: '4mm 0 0' }}>
          <div
            style={{
              width: `${qrSideMm}mm`,
              height: `${qrSideMm}mm`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              border: `0.6mm solid ${content.accentColor}`,
              padding: '2mm',
              backgroundColor: '#ffffff',
              boxSizing: 'border-box',
            }}
          >
            {qrImageSrc ? (
              <img src={qrImageSrc} alt="" style={{ width: '100%', height: '100%', objectFit: 'contain' }} />
            ) : qrSvg ? (
              <div
                style={{ width: '100%', height: '100%' }}
                // The markup comes from the qrcode library encoding a
                // backend-supplied string — not from user input or the
                // network — and inline SVG is the only way to get a vector
                // code onto the page. qrSvgString never emits script or
                // foreignObject.
                dangerouslySetInnerHTML={{ __html: qrSvg }}
              />
            ) : (
              <span style={{ fontSize: '3mm', color: '#5F6368' }}>QR unavailable</span>
            )}
          </div>
          {/* Static micro-CTA right under the frame — distinct from the
              merchant-editable headline above the QR, and never blank even
              if a merchant clears the headline field. */}
          <div
            style={{
              fontSize: '2.6mm',
              fontWeight: 700,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              color: content.accentColor,
              margin: '2mm 0 0',
              textAlign: 'center',
            }}
          >
            Scan to view menu
          </div>
        </div>

        {content.tableLabel && (
          <div
            style={{
              fontSize: '5mm',
              fontWeight: 700,
              margin: '3mm 0 0',
              fontVariantNumeric: 'tabular-nums',
              color: '#222222',
            }}
          >
            {content.tableLabel}
          </div>
        )}

        {content.showUrlText && menuUrl && (
          // A printed fallback for a guest whose camera will not scan — an
          // older phone, a cracked lens, a bad angle. Without it the card has
          // no other way in.
          <div
            style={{
              fontSize: '2.6mm',
              margin: '2mm 0 0',
              color: '#5F6368',
              wordBreak: 'break-all',
              maxWidth: '100%',
            }}
          >
            {stripSignature(menuUrl)}
          </div>
        )}

        {/* Footer block pinned to the foot, so its height never pushes the QR
            around as a merchant types. Promo/WiFi banner, then a 📍/📞
            metadata row sourced from the merchant record, then an optional
            socials line — in that order, each one only if it has content. */}
        {(() => {
          const hasPromo = !!(content.promoTitle || content.promoBody);
          const hasContact = content.showContactFooter && !!(content.address || content.phone);
          const hasSocial = !!content.socialHandle;
          if (!hasPromo && !hasContact && !hasSocial) return null;
          return (
            <div
              style={{
                position: 'absolute',
                left: '8mm',
                right: '8mm',
                bottom: '8mm',
                borderTop: `0.3mm solid ${content.accentColor}`,
                paddingTop: '3mm',
              }}
            >
              {content.promoTitle && (
                <div
                  style={{
                    fontSize: '3.4mm',
                    fontWeight: 700,
                    textTransform: 'uppercase',
                    letterSpacing: '0.06em',
                    color: content.accentColor,
                  }}
                >
                  {content.promoTitle}
                </div>
              )}
              {content.promoBody && (
                <div
                  style={{
                    fontSize: '3.2mm',
                    lineHeight: 1.35,
                    marginTop: content.promoTitle ? '1mm' : 0,
                    color: '#222222',
                    whiteSpace: 'pre-line',
                  }}
                >
                  {content.promoBody}
                </div>
              )}

              {hasContact && (
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'center',
                    flexWrap: 'wrap',
                    columnGap: '4mm',
                    rowGap: '0.8mm',
                    marginTop: hasPromo ? '2mm' : 0,
                    fontSize: '2.6mm',
                    color: '#5F6368',
                  }}
                >
                  {content.address && <span>📍 {content.address}</span>}
                  {content.phone && <span>📞 {content.phone}</span>}
                </div>
              )}

              {hasSocial && (
                <div
                  style={{
                    marginTop: hasPromo || hasContact ? '1.5mm' : 0,
                    fontSize: '2.8mm',
                    fontWeight: 700,
                    color: content.accentColor,
                  }}
                >
                  🌐 {content.socialHandle}
                </div>
              )}
            </div>
          );
        })()}
        </div>
      </div>
    </div>
  );
}

/**
 * Drops the `?signature=` query from the printed fallback URL.
 *
 * The signature is long, opaque and impossible to type off a card, so printing
 * it makes the fallback useless. The digital-menu route resolves without it —
 * DigitalMenuPage's resolveBranch takes no signature parameter — so the
 * shortened URL still works when typed. The QR itself keeps the full signed
 * string.
 */
function stripSignature(url: string): string {
  const q = url.indexOf('?');
  const bare = q === -1 ? url : url.slice(0, q);
  return bare.replace(/^https?:\/\//, '');
}
