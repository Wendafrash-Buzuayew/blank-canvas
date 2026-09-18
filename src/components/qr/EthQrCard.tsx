import React from 'react';
import { ETH_QR_TITLES, toLocalPhoneDigits, toCodeDigits, type EthQrLanguage } from '../../lib/ethQr';

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

export interface EthQrCardProps {
  /** Safaricom's bare QR module image (data: URL) — see the header comment above. */
  qrImageSrc: string;
  merchantName: string;
  /** The merchant's Safaricom short code — the "CODE:" row. */
  accountNumber: string;
  /** Safaricom's own `mobileNumber` field, e.g. "+251718788479". Renders nothing if absent. */
  phone?: string | null;
  language: EthQrLanguage;
  widthMm: number;
}

export function EthQrCard({ qrImageSrc, merchantName, accountNumber, phone, language, widthMm }: EthQrCardProps) {
  // The real card (per the brand guideline PDF referenced in git history) is
  // ~1440x1708px — kept here only to size the elements below proportionately
  // to whatever widthMm the caller (StandeeBackSheet) hands this, not to
  // claim this recreates that exact reference pixel-for-pixel.
  const k = widthMm / 1440;
  const phoneDigits = toLocalPhoneDigits(phone);
  const codeDigits = toCodeDigits(accountNumber);

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
        <div style={{ display: 'flex', gap: `${6 * k}mm`, marginTop: `${40 * k}mm`, width: `${1240 * k}mm`, justifyContent: 'center' }}>
          {phoneDigits.map((digit, i) => (
            <div
              key={i}
              style={{
                position: 'relative',
                flex: 1,
                aspectRatio: '1 / 1.15',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: '#f2f2f2',
                border: `${1.5 * k}mm solid #1a1a1a`,
                borderRadius: `${4 * k}mm`,
                fontWeight: 800,
                fontSize: `${70 * k}mm`,
                color: '#1a1a1a',
              }}
            >
              {digit}
              {/* Flip-clock/odometer split line, matching the official
                  template's digit strip. */}
              <span
                aria-hidden="true"
                style={{
                  position: 'absolute',
                  left: 0,
                  right: 0,
                  top: '50%',
                  height: `${1 * k}mm`,
                  backgroundColor: '#1a1a1a',
                  opacity: 0.5,
                }}
              />
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

      <div
        style={{
          marginTop: `${30 * k}mm`,
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

      {/* Footer band — the three acquirer/scheme marks the official template
          carries, left to right: the acquiring wallet (m-pesa), the acquiring
          bank (ADDIS), and the national switch that clears the payment
          (EthSwitch). Styled text rather than image logos, see header
          comment. Laid out as one row separated by hairline rules, which is
          how the reference card groups them — stacking them instead made the
          band tall enough to push the red footer off a 105mm-wide A6 trim. */}
      <div
        style={{
          marginTop: `${12 * k}mm`,
          width: '100%',
          backgroundColor: BRAND_RED,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: `${24 * k}mm`,
          boxSizing: 'border-box',
          padding: `${22 * k}mm ${32 * k}mm`,
        }}
      >
        <span style={{ color: '#ffffff', fontWeight: 900, fontStyle: 'italic', fontSize: `${44 * k}mm`, whiteSpace: 'nowrap' }}>
          m-pesa
        </span>
        <span aria-hidden="true" style={{ width: `${2 * k}mm`, alignSelf: 'stretch', backgroundColor: '#ffffff', opacity: 0.5 }} />
        <span
          style={{
            color: '#ffffff',
            fontWeight: 900,
            fontSize: `${40 * k}mm`,
            letterSpacing: `${3 * k}mm`,
            whiteSpace: 'nowrap',
          }}
        >
          ADDIS
        </span>
        <span aria-hidden="true" style={{ width: `${2 * k}mm`, alignSelf: 'stretch', backgroundColor: '#ffffff', opacity: 0.5 }} />
        <span
          style={{
            color: '#ffffff',
            fontWeight: 700,
            fontSize: `${26 * k}mm`,
            letterSpacing: `${1 * k}mm`,
            textAlign: 'center',
            lineHeight: 1.2,
          }}
        >
          Powered by
          <br />
          ETHSWITCH
        </span>
      </div>
    </div>
  );
}
