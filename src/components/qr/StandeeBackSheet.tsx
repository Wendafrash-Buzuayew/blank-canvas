import React from 'react';
import { STANDEE_SIZES, BLEED_MM, type StandeeSize } from '../../lib/standee';

/**
 * The back side of a printed table stand — same physical trim size as the
 * front (StandeeSheet), printed on the reverse so a merchant gets one
 * double-sided card per table rather than two QRs squeezed onto one face.
 *
 * Purely a trim-box/scale wrapper — it renders `children` life-size and
 * centered, and lets the caller (StandeeStudio) decide WHAT goes on the
 * back. In practice that is always <EthQrCard> wrapped around the bare
 * reusable QR module Safaricom returns under `qrImageUrl`: the response
 * describes a merchant, not a finished card, so the banner, digit strip,
 * name box and footer marks are composed locally — which is also what lets
 * the merchant pick the card's language.
 *
 * This deliberately does NOT special-case a pre-composed provider image.
 * Asking Safaricom for a fixed amount used to return its own fully composed
 * 1440x1708 branded card in a different field, and handling both shapes
 * meant this component had two modes; the fixed-amount request is gone (see
 * SafaricomEthQrService's class comment). Note for anyone reintroducing one:
 * a pre-composed card must be rendered verbatim, never passed to EthQrCard,
 * which nests it inside its own QR slot — that was a real bug here once.
 */
export interface StandeeBackSheetProps {
  size: StandeeSize;
  showCropMarks: boolean;
  scale?: number;
  children: React.ReactNode;
}

export function StandeeBackSheet({ size, showCropMarks, scale = 1, children }: StandeeBackSheetProps) {
  const trim = STANDEE_SIZES[size];
  const pad = showCropMarks ? BLEED_MM : 0;
  const widthMm = trim.widthMm + pad * 2;
  const heightMm = trim.heightMm + pad * 2;

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
        <div
          style={{
            position: 'absolute',
            top: `${pad}mm`,
            left: `${pad}mm`,
            width: `${trim.widthMm}mm`,
            height: `${trim.heightMm}mm`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: '#ffffff',
            overflow: 'hidden',
          }}
        >
          {children}
        </div>
      </div>
    </div>
  );
}
