import React from 'react';
import { ETH_QR_TITLES, toLocalPhoneDigits, toCodeDigits, type EthQrLanguage } from '../../lib/ethQr';
import { ETHQR_LOGO_SRC, ETHSWITCH_POWERED_BY_SRC, MPESA_BADGE_SRC } from '../../assets/ethqrBrandMarks';

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
 * Brand marks (the ETHQR wordmark, M-PESA, EthSwitch) are real artwork, from
 * src/assets/ethqrBrandMarks.ts — they were type styled to imitate the marks
 * until approved assets existed. The ETHQR and EthSwitch marks come from the
 * guideline's own merchant-card template and are print quality. The M-PESA
 * mark is a low-resolution PLACEHOLDER cropped from a sample card: the
 * guideline does not contain it, because the acquirer supplies their own.
 * The M-PESA mark is the official green wordmark on a transparent ground,
 * which is why the footer needs no coloured field behind it. See that file
 * for each mark's provenance.
 *
 * The layout below follows that same template: white throughout, an
 * "Acquired by" bronze bar, the acquirer's mark, then the "Powered by |
 * ETHSWITCH" lockup last. Earlier revisions put the two footer marks on a
 * full-bleed red band, which the standard has no equivalent of and which
 * would require recolouring EthSwitch's mark (published dark-on-light) to
 * keep it legible.
 *
 * The card runs to roughly 190mm of content at A5's 148mm trim width, so it
 * clears all three trim sizes (A5/A6/4x6 tent) with room to spare — worth
 * re-checking against StandeeBackSheet's centred, `overflow: hidden` trim
 * box if anything here grows, since overflow there CLIPS in silence.
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
 * The ETHQR wordmark's printed width, in the same 1440-wide reference units
 * as every other measurement here (see `k`). The mark's own aspect ratio
 * supplies the height.
 *
 * This replaced a text imitation of the wordmark — "ETH" in Safaricom Dark
 * Green (#00833E, not an ETHQR colour at all) next to "QR" in a made-up
 * bordered box. Brand colours are no longer declared in this file because
 * nothing here paints them any more: the artwork carries them.
 */
const ETHQR_LOGO_WIDTH = 560;

/**
 * Printed widths of the two footer marks, same reference units as above.
 *
 * Set as a FRACTION OF CARD WIDTH measured off the guideline's own template
 * rather than by eye: there the ETHQR mark occupies ~39% of the card and the
 * EthSwitch lockup ~45%, hence 560/1440 and 620/1440. The acquirer's mark
 * sits between them at ~30% — the template only carries a "BANK/PSP LOGO"
 * text placeholder in that slot, so its size is a judgement call, but it
 * should out-weigh the EthSwitch credit line (the acquirer is the headline
 * of the "Acquired by" block) while not competing with the ETHQR mark.
 */
const MPESA_BADGE_WIDTH = 430;
const ETHSWITCH_LOCKUP_WIDTH = 620;

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

      {/* The ETHQR wordmark — the real mark (see ethqrBrandMarks.ts), not
          type styled to look like it. The tagline "ETHIOPIAN INTEROPERABLE
          PAYMENT QR CODE" is part of the artwork, so it is no longer set as
          a separate line underneath.
          Width-driven with `height: auto` so the mark keeps its own aspect
          ratio; the flag swoosh through "ETH" makes any stretch obvious. */}
      <img
        src={ETHQR_LOGO_SRC}
        alt="ETHQR — Ethiopian Interoperable Payment QR Code"
        style={{
          width: `${ETHQR_LOGO_WIDTH * k}mm`,
          height: 'auto',
          marginTop: `${20 * k}mm`,
          display: 'block',
        }}
      />

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

      {/* "Acquired by" bar — a plain bronze rectangle spanning the middle of
          the card, as the guideline's own merchant-card template sets it.
          It was a rounded pill floating on the top edge of a red footer
          band; that band is gone (see below), so there is nothing left to
          float on and nothing that needed the pill shape. */}
      <div
        style={{
          marginTop: `${30 * k}mm`,
          width: `${620 * k}mm`,
          textAlign: 'center',
          backgroundColor: BRAND_BRONZE,
          color: '#ffffff',
          fontWeight: 800,
          fontSize: `${38 * k}mm`,
          padding: `${10 * k}mm ${40 * k}mm`,
          boxSizing: 'border-box',
        }}
      >
        Acquired by
      </div>

      {/* Footer — white, exactly as the guideline's merchant-card template
          sets it: the acquirer's mark under the "Acquired by" bar, then the
          "Powered by | ETHSWITCH" lockup last.
          A full-bleed RED band used to sit here with both marks set as
          styled text. It went for two reasons: the standard's own template
          has no such band, and the published EthSwitch lockup is drawn dark
          on a light ground, so putting it on red would have meant
          recolouring another organisation's mark to keep it legible.
          There is also no location line. A bottom-left city (falling back to
          "ADDIS") was carried for a while and is not part of this lockup. */}
      <img
        src={MPESA_BADGE_SRC}
        alt="Acquired by M-PESA"
        style={{
          width: `${MPESA_BADGE_WIDTH * k}mm`,
          height: 'auto',
          marginTop: `${26 * k}mm`,
          display: 'block',
        }}
      />
      <img
        src={ETHSWITCH_POWERED_BY_SRC}
        alt="Powered by EthSwitch"
        style={{
          width: `${ETHSWITCH_LOCKUP_WIDTH * k}mm`,
          height: 'auto',
          margin: `${26 * k}mm 0 ${30 * k}mm`,
          display: 'block',
        }}
      />
    </div>
  );
}
