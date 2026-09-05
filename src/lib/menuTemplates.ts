/**
 * Resolves a MenuTemplateEntity's (backend) admin-editable definition into
 * the actual Tailwind/token classes the customer digital menu (and the
 * merchant's template picker preview) render with.
 *
 * Every value below is a complete, literal class string, never built by
 * interpolating a suffix onto a class name - Tailwind only generates CSS
 * for class names it can see as literal text at build time (see the same
 * note in src/components/ui/States.tsx). A `${...}-${accent}` pattern here
 * would silently produce no styling at all.
 */

export type BackgroundMode = 'LIGHT' | 'TINTED' | 'DARK';
export type AccentToken = 'BRAND' | 'INFO' | 'WARN' | 'DANGER';

export interface MenuTemplateDefinition {
  key: string;
  displayName: string;
  backgroundMode: BackgroundMode;
  accentToken: AccentToken;
}

export const BACKGROUND_MODE_OPTIONS: { value: BackgroundMode; label: string }[] = [
  { value: 'LIGHT', label: 'Light' },
  { value: 'TINTED', label: 'Tinted' },
  { value: 'DARK', label: 'Dark' },
];

export const ACCENT_TOKEN_OPTIONS: { value: AccentToken; label: string }[] = [
  { value: 'BRAND', label: 'Brand green' },
  { value: 'INFO', label: 'Teal' },
  { value: 'WARN', label: 'Amber' },
  { value: 'DANGER', label: 'Red' },
];

interface BackgroundClasses {
  page: string;
  headerBorder: string;
  heading: string;
  itemName: string;
  secondaryText: string;
  swatchBg: string;
}

const BACKGROUND_CLASSES: Record<BackgroundMode, BackgroundClasses> = {
  LIGHT: {
    page: 'bg-canvas text-ink',
    headerBorder: 'border-b border-line',
    heading: 'text-ink',
    itemName: 'text-ink',
    secondaryText: 'text-muted',
    swatchBg: 'bg-surface',
  },
  TINTED: {
    page: 'bg-brand-soft text-ink',
    headerBorder: '',
    heading: 'text-ink',
    itemName: 'text-ink',
    secondaryText: 'text-muted',
    swatchBg: 'bg-brand-soft',
  },
  DARK: {
    page: 'bg-ink text-on-ink',
    headerBorder: 'border-b border-ink-2',
    heading: 'text-on-ink',
    itemName: 'text-on-ink',
    secondaryText: 'text-on-ink-muted',
    swatchBg: 'bg-ink',
  },
};

/** Safe as TEXT on a LIGHT/TINTED ground (DESIGN.md 3 - measured against canvas/surface). */
const ACCENT_TEXT: Record<AccentToken, string> = {
  BRAND: 'text-brand-press',
  INFO: 'text-info',
  WARN: 'text-warn',
  DANGER: 'text-danger',
};

/** Safe as a FILL (ink foreground) - used on a DARK ground instead of raw accent text, which has no measured dark-ground pairing. */
const ACCENT_FILL: Record<AccentToken, string> = {
  BRAND: 'bg-brand',
  INFO: 'bg-info-fill',
  WARN: 'bg-warn-fill',
  DANGER: 'bg-danger-fill',
};

const ACCENT_SWATCH_BORDER: Record<AccentToken, string> = {
  BRAND: 'border-brand-dark',
  INFO: 'border-info',
  WARN: 'border-warn',
  DANGER: 'border-danger',
};

export interface ResolvedTemplateClasses {
  page: string;
  header: string;
  title: string;
  categoryHeading: string;
  itemName: string;
  priceWrap: string;
  price: string;
  strikePrice: string;
  description: string;
  /** A small preview circle/swatch for a template picker - not used on the customer page itself. */
  swatch: string;
}

export function resolveTemplateClasses(def: Pick<MenuTemplateDefinition, 'backgroundMode' | 'accentToken'>): ResolvedTemplateClasses {
  const bg = BACKGROUND_CLASSES[def.backgroundMode];
  const isDark = def.backgroundMode === 'DARK';

  return {
    page: bg.page,
    header: `px-4 py-8 text-center ${bg.headerBorder}`,
    title: `font-display text-title-l ${bg.heading}`,
    categoryHeading: `text-label-s uppercase mt-8 mb-2 px-4 ${isDark ? bg.secondaryText : ACCENT_TEXT[def.accentToken]}`,
    itemName: `text-title-s ${bg.itemName}`,
    priceWrap: isDark ? `rounded-pill ${ACCENT_FILL[def.accentToken]} px-2 py-0.5` : '',
    price: isDark
      ? 'text-label-m text-ink [font-variant-numeric:tabular-nums]'
      : `text-label-m ${ACCENT_TEXT[def.accentToken]} [font-variant-numeric:tabular-nums]`,
    strikePrice: `text-label-s ${bg.secondaryText} line-through [font-variant-numeric:tabular-nums]`,
    description: `text-body-m ${bg.secondaryText} mt-1 line-clamp-2`,
    swatch: `h-4 w-4 rounded-pill border-2 ${bg.swatchBg} ${ACCENT_SWATCH_BORDER[def.accentToken]}`,
  };
}
