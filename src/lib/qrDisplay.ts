/**
 * Display gating for a provisioned table QR.
 *
 * The payload is minted, stored and rendered by the backend. The frontend's only job
 * is to show the image it was given, or show nothing — never to construct a code. A
 * client-side render of an EMVCo payload is a second renderer for a money instrument,
 * and a fabricated URL is a sticker that resolves to a 404 once it is laminated.
 */
export interface QrMetadata {
  base64Content?: string | null;
  payloadRaw?: string | null;
  terminalLabel?: string | null;
  profile?: string | null;
  qrUrl?: string | null;
}

export function canRenderQr(metadata?: QrMetadata | null): boolean {
  return !!metadata?.base64Content;
}

/** @returns the server-rendered image, or null. Never a generated fallback. */
export function qrImageSrc(metadata?: QrMetadata | null): string | null {
  return canRenderQr(metadata) ? metadata!.base64Content! : null;
}

export function qrCaption(metadata?: QrMetadata | null): string {
  if (!canRenderQr(metadata)) return 'QR not provisioned yet';
  if (metadata!.profile === 'MENU_URL') return 'Menu link code';
  return `Payment code · terminal ${metadata!.terminalLabel ?? 'unknown'}`;
}
