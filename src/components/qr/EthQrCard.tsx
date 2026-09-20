import React from 'react';
import { ETH_QR_TITLES, toLocalPhoneDigits, toCodeDigits, resolveLocation, type EthQrLanguage } from '../../lib/ethQr';

/**
 * The locally-composed ETHQR standee card.
 *
 * `qrImageSrc` is the bare, unbranded QR module Safaricom returns under
 * `qrImageUrl` for an accountNumber-only request — the one and only response
 * shape (see SafaricomEthQrService's class comment). Everything around it —
 * the language banner, the ETHQR badge, the digit strip, the name box, the
 * CODE row and the footer marks — is composed here, which is what makes the
 * card's language selectable at all.
 *
 * It follows that this must only ever be handed a bare QR module. Passing it
 * an already-composed card image nests a tiny copy of a full card inside
 * this one's QR slot, which is exactly the bug an earlier version of this
 * feature shipped (see git history).
 *
 * Brand marks (the ETHQR wordmark, M-PESA, EthSwitch) are rendered as styled
 * text, not image logos — no real logo asset files exist in this repo, and
 * fabricating or hotlinking one would not be an authentic brand mark. Swap
 * these `<span>`s for real `<img>`s the moment approved logo assets exist;
 * nothing else about this layout needs to change.
 */

// The language set, the approved titles and the two digit splits live in
// src/lib/ethQr.ts so they can be unit-tested without a DOM (see
// ethQr.test.ts). Re-exported here because this component was the original
// home of both and StandeeStudio imports them from this path.
export type { EthQrLanguage } from '../../lib/ethQr';
export { ETH_QR_LANGUAGE_OPTIONS } from '../../lib/ethQr';

const BRAND_BRONZE = '#8C6339';
const BRAND_RED = '#D9383A';

/**
 * The digit strip's width and inter-box gap, in the same 1440-wide reference
 * units as every other measurement below (see `k`). Named because the digit
 * box side is DERIVED from them rather than being a third independent
 * number — see digitBoxSide.
 */
const STRIP_WIDTH = 1240;
const DIGIT_GAP = 6;

export interface EthQrCardProps {
  /** Safaricom's bare QR module image (data: URL) — see the header comment above. */
  qrImageSrc: string;
  merchantName: string;
  /** The merchant's Safaricom short code — the "CODE:" row. */
  accountNumber: string;
  /** Safaricom's own `mobileNumber` field, e.g. "+251718788479". Renders nothing if absent. */
  phone?: string | null;
  /**
   * Safaricom's own `city` field — the acquiring location printed bottom
   * left. Null/blank in most real responses, which is why this falls back
   * rather than rendering an empty slot; see resolveLocation.
   */
  city?: string | null;
  language: EthQrLanguage;
  widthMm: number;
}

export function EthQrCard({ qrImageSrc, merchantName, accountNumber, phone, city, language, widthMm }: EthQrCardProps) {
  // The real card (per the brand guideline PDF referenced in git history) is
  // ~1440x1708px — kept here only to size the elements below proportionately
  // to whatever widthMm the caller (StandeeBackSheet) hands this, not to
  // claim this recreates that exact reference pixel-for-pixel.
  const k = widthMm / 1440;
  const phoneDigits = toLocalPhoneDigits(phone);
  const codeDigits = toCodeDigits(accountNumber);
  const location = resolveLocation(city);
  // What `flex: 1` used to work out to, stated directly so the box can be
  // square by construction. Derived from the actual digit count rather than
  // assuming 10: a provider record carrying a short landline still gets a
  // strip that spans the same width, just with fewer, wider boxes.
  const digitBoxSide =
    phoneDigits.length > 0
      ? (STRIP_WIDTH - DIGIT_GAP * (phoneDigits.length - 1)) / phoneDigits.length
      : 0;

  return (
    <div
      style={{
        width: `${widthMm}mm`,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        overflow: 'hidden',
        boxSizing: 'border-box',
        backgroundColor: '#ffffff',
      }}
    >
      {/* Header banner */}
      <div
        style={{
          width: '100%',
          backgroundColor: BRAND_BRONZE,
          color: '#ffffff',
          fontWeight: 800,
          textTransform: 'uppercase',
          textAlign: 'center',
          fontSize: `${52 * k}mm`,
          lineHeight: 1.3,
          padding: `${28 * k}mm ${40 * k}mm`,
          boxSizing: 'border-box',
        }}
      >
        {ETH_QR_TITLES[language]}
      </div>

      {/* ETHQR wordmark badge — styled text, see header comment. */}
      <div
        aria-hidden="true"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: `${4 * k}mm`,
          marginTop: `${20 * k}mm`,
          fontWeight: 900,
          fontSize: `${56 * k}mm`,
          letterSpacing: `${1 * k}mm`,
        }}
      >
        <span style={{ color: '#00833E' }}>ETH</span>
        <span
          style={{
            color: '#1a1a1a',
            border: `${3 * k}mm solid #1a1a1a`,
            borderRadius: `${6 * k}mm`,
            padding: `0 ${6 * k}mm`,
          }}
        >
          QR
        </span>
      </div>
      <div style={{ fontSize: `${16 * k}mm`, color: '#5F6368', letterSpacing: `${0.5 * k}mm`, textTransform: 'uppercase' }}>
        Ethiopian Interoperable Payment QR Code
      </div>

      {/* QR holder — square, bronze frame, tight padding so the code fills the
          box (see StandeeBackSheet's header comment for why this must never
          be a wider-than-tall box: a square QR centered in one just adds
          dead space on both sides). */}
      <div
        style={{
          width: `${780 * k}mm`,
          height: `${780 * k}mm`,
          marginTop: `${20 * k}mm`,
          backgroundColor: BRAND_BRONZE,
          borderRadius: `${24 * k}mm`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxSizing: 'border-box',
          padding: `${10 * k}mm`,
        }}
      >
        <div
          style={{
            width: '100%',
            height: '100%',
            backgroundColor: '#ffffff',
            borderRadius: `${14 * k}mm`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxSizing: 'border-box',
            padding: `${8 * k}mm`,
          }}
        >
          <img src={qrImageSrc} alt="Scan to pay" style={{ width: '100%', height: '100%', objectFit: 'contain' }} />
        </div>
      </div>

      {phoneDigits.length > 0 && (
        <div style={{ display: 'flex', gap: `${DIGIT_GAP * k}mm`, marginTop: `${40 * k}mm`, width: `${STRIP_WIDTH * k}mm`, justifyContent: 'center' }}>
          {phoneDigits.map((digit, i) => (
            <div
              key={i}
              style={{
                // Square, per the brand standard's digit grid — sized
                // explicitly rather than with `flex: 1` + `aspect-ratio`.
                // The strip is also rasterised by html2canvas for the mobile
                // PDF export, which does not reliably resolve aspect-ratio;
                // a box whose height came out as 0 there would drop the
                // whole number off the printed card while the on-screen
                // preview still looked right. Width is deterministic, so the
                // height is just stated. (It was 1 / 1.15 — a flip-clock
                // tile with a split rule across the middle — which is a
                // second departure from the standard's plain white squares.)
                width: `${digitBoxSide * k}mm`,
                height: `${digitBoxSide * k}mm`,
                flexShrink: 0,
                boxSizing: 'border-box',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: '#ffffff',
                border: `${1.5 * k}mm solid #1a1a1a`,
                borderRadius: `${8 * k}mm`,
                fontWeight: 800,
                fontSize: `${64 * k}mm`,
                color: '#1a1a1a',
              }}
            >
              {digit}
            </div>
          ))}
        </div>
      )}

      <div
        style={{
          marginTop: `${(phoneDigits.length > 0 ? 20 : 40) * k}mm`,
          width: `${1240 * k}mm`,
          border: `${4 * k}mm solid ${BRAND_RED}`,
          borderRadius: `${10 * k}mm`,
          padding: `${16 * k}mm ${20 * k}mm`,
          boxSizing: 'border-box',
          textAlign: 'center',
          fontWeight: 800,
          fontSize: `${58 * k}mm`,
          color: '#1a1a1a',
          textTransform: 'uppercase',
        }}
      >
        {merchantName}
      </div>

      <div
        style={{
          marginTop: `${30 * k}mm`,
          width: `${1240 * k}mm`,
          display: 'flex',
          alignItems: 'baseline',
          justifyContent: 'center',
          gap: `${16 * k}mm`,
          fontSize: `${42 * k}mm`,
          color: '#1a1a1a',
        }}
      >
        <span style={{ fontWeight: 400 }}>CODE:</span>
        <span style={{ fontWeight: 800, letterSpacing: `${8 * k}mm`, fontVariantNumeric: 'tabular-nums' }}>
          {codeDigits.join(' ')}
        </span>
      </div>

      {/* Footer lockup — the "Acquired by" tab floats ON the red band's top
          edge rather than sitting above it as a separate pill, which is what
          makes it read as a tab attached to the band.
          `marginBottom: -half its height` pulls the band up under it; the
          band then reserves that much extra top padding so the three marks
          below still clear it. Done with margins and static positioning
          rather than `position: absolute` + `translateX(-50%)` because this
          subtree is also rasterised by html2canvas for the mobile PDF path,
          where transforms on absolutely-positioned children are the least
          reliable thing to hand it. */}
      <div
        style={{
          marginTop: `${30 * k}mm`,
          marginBottom: `${-19 * k}mm`,
          position: 'relative',
          zIndex: 1,
          backgroundColor: BRAND_BRONZE,
          color: '#ffffff',
          fontWeight: 800,
          fontSize: `${38 * k}mm`,
          padding: `${10 * k}mm ${40 * k}mm`,
          borderRadius: `${18 * k}mm`,
        }}
      >
        Acquired by
      </div>

      {/* Footer band — the three marks the brand standard fixes to three
          positions: the acquiring location bottom LEFT, the acquiring wallet
          (m-pesa) CENTRED, and the national switch that clears the payment
          (EthSwitch) bottom RIGHT. Styled text rather than image logos, see
          the header comment.
          The two outer slots are `flex: 1 1 0` so they take equal width and
          the m-pesa mark is centred against the BAND, not against whatever
          the side text happens to measure — a long city name ("DIRE DAWA")
          would otherwise shove the wallet mark off centre. `minWidth: 0`
          lets those slots shrink below their text width on a 105mm A6 trim
          instead of forcing the band wider than the card. */}
      <div
        style={{
          width: '100%',
          backgroundColor: BRAND_RED,
          display: 'flex',
          alignItems: 'center',
          boxSizing: 'border-box',
          padding: `${30 * k}mm ${32 * k}mm ${22 * k}mm`,
        }}
      >
        <span
          style={{
            flex: '1 1 0',
            minWidth: 0,
            textAlign: 'left',
            color: '#ffffff',
            fontWeight: 900,
            fontSize: `${34 * k}mm`,
            letterSpacing: `${2 * k}mm`,
            textTransform: 'uppercase',
            lineHeight: 1.1,
          }}
        >
          {location}
        </span>
        <span
          style={{
            flexShrink: 0,
            color: '#ffffff',
            fontWeight: 900,
            fontStyle: 'italic',
            fontSize: `${44 * k}mm`,
            whiteSpace: 'nowrap',
            padding: `0 ${16 * k}mm`,
          }}
        >
          m-pesa
        </span>
        <span
          style={{
            flex: '1 1 0',
            minWidth: 0,
            textAlign: 'right',
            color: '#ffffff',
            fontWeight: 700,
            fontSize: `${24 * k}mm`,
            letterSpacing: `${1 * k}mm`,
            lineHeight: 1.2,
          }}
        >
          Powered by
          <br />
          <strong style={{ fontWeight: 900, letterSpacing: `${2 * k}mm` }}>ETHSWITCH</strong>
        </span>
      </div>
    </div>
  );
}
