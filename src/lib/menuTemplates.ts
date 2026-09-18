/**
 * Resolves a MenuTemplateEntity's (backend) admin-editable definition into
 * the actual Tailwind/token classes the customer digital menu (and the
 * admin/merchant previews) render with.
 *
 * Every value below is a complete, literal class string, never built by
 * interpolating a suffix onto a class name - Tailwind only generates CSS
 * for class names it can see as literal text at build time (see the same
 * note in src/components/ui/States.tsx). A `${...}-${accent}` pattern here
 * would silently produce no styling at all. That is why the structural
 * lookups below are exhaustive Records of literals rather than a builder,
 * and why the 4x3 card matrix is written out longhand.
 */

export type BackgroundMode = 'LIGHT' | 'TINTED' | 'DARK';
export type AccentToken = 'BRAND' | 'INFO' | 'WARN' | 'DANGER';
export type LayoutStructure = 'GRID_2' | 'GRID_3' | 'SINGLE_COLUMN' | 'TWO_COLUMN' | 'COMPACT_LIST';
export type ItemCardStyle = 'CARD_BORDERED' | 'CARD_FLAT' | 'ELEVATED_SHADOW' | 'MINIMAL_DIVIDER';
export type ImagePosition = 'TOP' | 'LEFT' | 'RIGHT' | 'NONE';
export type ImageAspectRatio = 'SQUARE_1_1' | 'LANDSCAPE_16_9' | 'ROUNDED_AVATAR';
export type TemplateFontFamily = 'SANS_SERIF' | 'SERIF' | 'MODERN_MONO';
export type HeaderAlignment = 'LEFT' | 'CENTER';

export interface MenuTemplateDefinition {
  key: string;
  displayName: string;
  backgroundMode: BackgroundMode;
  accentToken: AccentToken;
  layoutStructure?: LayoutStructure | null;
  itemCardStyle?: ItemCardStyle | null;
  showImages?: boolean | null;
  imagePosition?: ImagePosition | null;
  imageAspectRatio?: ImageAspectRatio | null;
  fontFamily?: TemplateFontFamily | null;
  headerAlignment?: HeaderAlignment | null;
  showCoverImage?: boolean | null;
}

/** The structural half of a definition, with every field decided. */
export interface TemplateStructure {
  layoutStructure: LayoutStructure;
  itemCardStyle: ItemCardStyle;
  showImages: boolean;
  imagePosition: ImagePosition;
  imageAspectRatio: ImageAspectRatio;
  fontFamily: TemplateFontFamily;
  headerAlignment: HeaderAlignment;
  showCoverImage: boolean;
}

/**
 * Mirrors MenuTemplateEntity.normalised() field for field. A definition read
 * from the API can have nulls in every structural column until
 * db/manual/001-menu-template-structure.sql has run, and these defaults must
 * match the backend's or a menu would change appearance when it does.
 */
export const STRUCTURE_DEFAULTS: TemplateStructure = {
  layoutStructure: 'SINGLE_COLUMN',
  itemCardStyle: 'CARD_BORDERED',
  showImages: true,
  imagePosition: 'LEFT',
  imageAspectRatio: 'SQUARE_1_1',
  fontFamily: 'SANS_SERIF',
  headerAlignment: 'CENTER',
  showCoverImage: true,
};

export function normaliseStructure(def: Partial<MenuTemplateDefinition>): TemplateStructure {
  return {
    layoutStructure: def.layoutStructure ?? STRUCTURE_DEFAULTS.layoutStructure,
    itemCardStyle: def.itemCardStyle ?? STRUCTURE_DEFAULTS.itemCardStyle,
    showImages: def.showImages ?? STRUCTURE_DEFAULTS.showImages,
    imagePosition: def.imagePosition ?? STRUCTURE_DEFAULTS.imagePosition,
    imageAspectRatio: def.imageAspectRatio ?? STRUCTURE_DEFAULTS.imageAspectRatio,
    fontFamily: def.fontFamily ?? STRUCTURE_DEFAULTS.fontFamily,
    headerAlignment: def.headerAlignment ?? STRUCTURE_DEFAULTS.headerAlignment,
    showCoverImage: def.showCoverImage ?? STRUCTURE_DEFAULTS.showCoverImage,
  };
}

/**
 * The single predicate for "does this template render dish images?" - the
 * frontend twin of MenuTemplateEntity.imagesVisible().
 *
 * showImages and imagePosition can both express "off", so no caller may test
 * either alone.
 */
export function imagesVisible(s: TemplateStructure): boolean {
  return s.showImages && s.imagePosition !== 'NONE';
}

// ---- Option lists for the admin form --------------------------------------

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

export const LAYOUT_OPTIONS: { value: LayoutStructure; label: string; hint: string }[] = [
  { value: 'SINGLE_COLUMN', label: 'Single column', hint: 'One dish per row — the most readable' },
  { value: 'GRID_2', label: 'Grid · 2 up', hint: 'Photo-led cards, two per row' },
  { value: 'GRID_3', label: 'Grid · 3 up', hint: 'Photo-led cards, three per row on desktop' },
  { value: 'TWO_COLUMN', label: 'Two-column list', hint: 'Printed-menu style, text flows in two columns' },
  { value: 'COMPACT_LIST', label: 'Compact list', hint: 'Dense rows separated by hairlines' },
];

export const CARD_STYLE_OPTIONS: { value: ItemCardStyle; label: string }[] = [
  { value: 'CARD_BORDERED', label: 'Bordered card' },
  { value: 'CARD_FLAT', label: 'Flat card' },
  { value: 'ELEVATED_SHADOW', label: 'Elevated shadow' },
  { value: 'MINIMAL_DIVIDER', label: 'Minimal divider' },
];

export const IMAGE_POSITION_OPTIONS: { value: ImagePosition; label: string }[] = [
  { value: 'LEFT', label: 'Left of text' },
  { value: 'RIGHT', label: 'Right of text' },
  { value: 'TOP', label: 'Above text' },
  { value: 'NONE', label: 'No images' },
];

export const IMAGE_ASPECT_OPTIONS: { value: ImageAspectRatio; label: string }[] = [
  { value: 'SQUARE_1_1', label: 'Square (1:1)' },
  { value: 'LANDSCAPE_16_9', label: 'Landscape (16:9)' },
  { value: 'ROUNDED_AVATAR', label: 'Round' },
];

export const FONT_FAMILY_OPTIONS: { value: TemplateFontFamily; label: string; hint: string }[] = [
  { value: 'SANS_SERIF', label: 'Sans (Proxima Nova)', hint: 'The brand face' },
  { value: 'SERIF', label: 'Serif', hint: 'System serif — not a brand face' },
  { value: 'MODERN_MONO', label: 'Mono', hint: 'System monospace — not a brand face' },
];

export const HEADER_ALIGNMENT_OPTIONS: { value: HeaderAlignment; label: string }[] = [
  { value: 'CENTER', label: 'Centred' },
  { value: 'LEFT', label: 'Left' },
];

// ---- Colour ---------------------------------------------------------------

interface BackgroundClasses {
  page: string;
  headerBorder: string;
  heading: string;
  itemName: string;
  secondaryText: string;
  swatchBg: string;
  /** Ground for an item card sitting on this page ground. */
  cardBg: string;
  cardBorder: string;
  divider: string;
  /** Placeholder box shown when an image is missing or fails to load. */
  imagePlaceholder: string;
}

const BACKGROUND_CLASSES: Record<BackgroundMode, BackgroundClasses> = {
  LIGHT: {
    page: 'bg-canvas text-ink',
    headerBorder: 'border-b border-line',
    heading: 'text-ink',
    itemName: 'text-ink',
    secondaryText: 'text-muted',
    swatchBg: 'bg-surface',
    cardBg: 'bg-surface',
    cardBorder: 'border-line',
    divider: 'divide-line',
    imagePlaceholder: 'bg-surface-2',
  },
  TINTED: {
    page: 'bg-brand-soft text-ink',
    headerBorder: '',
    heading: 'text-ink',
    itemName: 'text-ink',
    secondaryText: 'text-muted',
    swatchBg: 'bg-brand-soft',
    cardBg: 'bg-surface',
    cardBorder: 'border-line',
    divider: 'divide-line',
    imagePlaceholder: 'bg-surface-2',
  },
  DARK: {
    page: 'bg-ink text-on-ink',
    headerBorder: 'border-b border-ink-2',
    heading: 'text-on-ink',
    itemName: 'text-on-ink',
    secondaryText: 'text-on-ink-muted',
    swatchBg: 'bg-ink',
    // ink-2 is the only DESIGN.md ground that reads as a raised surface on an
    // ink page (10.47 for on-ink text over it). There is no "dark surface"
    // token, and bg-surface here would be a white card on a black page.
    cardBg: 'bg-ink-2',
    cardBorder: 'border-ink-2',
    divider: 'divide-ink-2',
    imagePlaceholder: 'bg-ink-2',
  },
};

/** Safe as TEXT on a LIGHT/TINTED ground (DESIGN.md §3 — measured against canvas/surface). */
const ACCENT_TEXT: Record<AccentToken, string> = {
  BRAND: 'text-brand-press',
  INFO: 'text-info',
  WARN: 'text-warn',
  DANGER: 'text-danger',
};

/** Safe as a FILL (ink foreground) — used on a DARK ground instead of raw accent text, which has no measured dark-ground pairing. */
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

// ---- Structure ------------------------------------------------------------

/**
 * Every layout collapses to one column in a narrow container. A two-up grid of
 * dishes on a 375px phone is unreadable, and the customer context is
 * phone-first (DESIGN.md §1).
 *
 * CONTAINER QUERIES, NOT BREAKPOINTS. These use `@sm:` / `@xl:`, which respond
 * to the width of the nearest `@container` ancestor (TemplatedMenu's root),
 * not to the viewport. Two reasons, and the first is a correctness bug the
 * viewport variants would cause:
 *
 *  1. The admin device toggle renders the menu inside a 375px-wide frame in a
 *     desktop browser. With `sm:grid-cols-2` the viewport is still wide, so
 *     the "mobile" preview would show a two-up grid — the exact thing the
 *     toggle exists to rule out.
 *  2. The live customer page is capped at 640px (DESIGN.md §5.4 frame A) but
 *     sits in a viewport that can be far wider, so viewport variants describe
 *     the window rather than the column the menu actually occupies.
 *
 * Container breakpoints: @sm = 24rem (384px), @xl = 36rem (576px). So a 375px
 * frame stays single-column, and the 640px page column gets 2-up for GRID_2
 * and 3-up for GRID_3.
 */
const LAYOUT_CONTAINER: Record<LayoutStructure, string> = {
  GRID_2: 'grid grid-cols-1 gap-3 @sm:grid-cols-2',
  GRID_3: 'grid grid-cols-1 gap-3 @sm:grid-cols-2 @xl:grid-cols-3',
  SINGLE_COLUMN: 'flex flex-col gap-3',
  // A real CSS multi-column flow, not a 2-up grid: this is the printed-menu
  // look, where items fill the left column then continue down the right.
  TWO_COLUMN: 'columns-1 gap-x-6 @sm:columns-2',
  COMPACT_LIST: 'flex flex-col',
};

/**
 * Per-item classes the LAYOUT (not the card style) demands. Only the
 * multi-column flow needs them: without break-inside a card splits across the
 * column boundary, and without a bottom margin the flow has no gutter.
 */
const LAYOUT_ITEM_EXTRA: Record<LayoutStructure, string> = {
  GRID_2: '',
  GRID_3: '',
  SINGLE_COLUMN: '',
  TWO_COLUMN: 'mb-3 [break-inside:avoid]',
  COMPACT_LIST: '',
};

/**
 * Card style x background mode, written out longhand because each cell is a
 * literal Tailwind class string. MINIMAL_DIVIDER carries no ground of its own
 * - the hairline comes from the container's divide-* class.
 */
const CARD_CLASSES: Record<ItemCardStyle, Record<BackgroundMode, string>> = {
  CARD_BORDERED: {
    LIGHT: 'rounded-card border border-line bg-surface p-3',
    TINTED: 'rounded-card border border-line bg-surface p-3',
    DARK: 'rounded-card border border-ink-2 bg-ink-2 p-3',
  },
  CARD_FLAT: {
    LIGHT: 'rounded-card bg-surface p-3',
    TINTED: 'rounded-card bg-surface p-3',
    DARK: 'rounded-card bg-ink-2 p-3',
  },
  ELEVATED_SHADOW: {
    LIGHT: 'rounded-card bg-surface p-3 shadow-card',
    TINTED: 'rounded-card bg-surface p-3 shadow-card',
    // No shadow on an ink ground - a shadow over near-black reads as nothing.
    // The raised ground itself is the elevation cue.
    DARK: 'rounded-card bg-ink-2 p-3',
  },
  MINIMAL_DIVIDER: {
    LIGHT: 'py-3',
    TINTED: 'py-3',
    DARK: 'py-3',
  },
};

/** The container only draws hairlines for the divider card style. */
function dividerClass(style: ItemCardStyle, mode: BackgroundMode): string {
  return style === 'MINIMAL_DIVIDER' ? `divide-y ${BACKGROUND_CLASSES[mode].divider}` : '';
}

/** Flex direction of a card's inner row, from where the image sits. */
const ITEM_INNER: Record<ImagePosition, string> = {
  TOP: 'flex flex-col gap-3',
  LEFT: 'flex flex-row gap-3',
  RIGHT: 'flex flex-row-reverse gap-3',
  NONE: 'flex flex-col',
};

/**
 * The image box. Side-mounted images take a fixed width so text lines up down
 * the column; a top image spans the card.
 */
const IMAGE_BOX: Record<ImagePosition, Record<ImageAspectRatio, string>> = {
  TOP: {
    SQUARE_1_1: 'w-full aspect-square overflow-hidden rounded-[var(--radius-xl2)]',
    LANDSCAPE_16_9: 'w-full aspect-video overflow-hidden rounded-[var(--radius-xl2)]',
    ROUNDED_AVATAR: 'mx-auto w-24 aspect-square overflow-hidden rounded-pill',
  },
  LEFT: {
    SQUARE_1_1: 'w-20 shrink-0 aspect-square overflow-hidden rounded-[var(--radius-xl2)]',
    LANDSCAPE_16_9: 'w-28 shrink-0 aspect-video overflow-hidden rounded-[var(--radius-xl2)]',
    ROUNDED_AVATAR: 'w-16 shrink-0 aspect-square overflow-hidden rounded-pill',
  },
  RIGHT: {
    SQUARE_1_1: 'w-20 shrink-0 aspect-square overflow-hidden rounded-[var(--radius-xl2)]',
    LANDSCAPE_16_9: 'w-28 shrink-0 aspect-video overflow-hidden rounded-[var(--radius-xl2)]',
    ROUNDED_AVATAR: 'w-16 shrink-0 aspect-square overflow-hidden rounded-pill',
  },
  // Never rendered — imagesVisible() gates it — but the Record must be total
  // so a future position cannot be added without a box for it.
  NONE: {
    SQUARE_1_1: 'hidden',
    LANDSCAPE_16_9: 'hidden',
    ROUNDED_AVATAR: 'hidden',
  },
};

/**
 * SERIF and MODERN_MONO are system stacks, declared as theme tokens in
 * src/index.css. The product licenses Proxima Nova only (DESIGN.md §4.1); the
 * customer digital menu is the single context §1 rule 3 permits to be
 * expressive, which is why a non-brand face is allowed here and nowhere else.
 */
const FONT_CLASS: Record<TemplateFontFamily, string> = {
  SANS_SERIF: 'font-sans',
  SERIF: 'font-template-serif',
  MODERN_MONO: 'font-template-mono',
};

/**
 * The heading face, kept as its own Record rather than letting the page root's
 * FONT_CLASS cascade into the title.
 *
 * `font-display` and `font-template-serif` are both font-family utilities, so
 * they have equal specificity and the CSS source order decides which wins -
 * NOT the order they appear in a className. A title carrying a hardcoded
 * `font-display` would therefore snap back to Proxima Nova on a serif
 * template, unpredictably. Naming the face per option removes the collision
 * instead of relying on cascade luck.
 */
const TITLE_FONT_CLASS: Record<TemplateFontFamily, string> = {
  SANS_SERIF: 'font-display',
  SERIF: 'font-template-serif',
  MODERN_MONO: 'font-template-mono',
};

const HEADER_ALIGN: Record<HeaderAlignment, string> = {
  LEFT: 'text-left items-start',
  CENTER: 'text-center items-center',
};

// ---- Result ---------------------------------------------------------------

export interface ResolvedTemplateClasses {
  page: string;
  header: string;
  title: string;
  tagline: string;
  categoryHeading: string;
  itemName: string;
  priceWrap: string;
  price: string;
  strikePrice: string;
  description: string;
  /** A small preview circle/swatch for a template picker — not used on the customer page itself. */
  swatch: string;

  // Structure
  /** Wraps the items of one category. */
  itemsContainer: string;
  /** One item card. */
  item: string;
  /** A card's inner row/column, set by image position. */
  itemInner: string;
  /** The image box, sized by position and aspect ratio. */
  imageBox: string;
  /** Ground shown when an item has no image, or its image fails to load. */
  imagePlaceholder: string;
  /** Applied to the page root so headings and item names take the template face. */
  fontClass: string;
  /** Header block alignment. */
  headerAlign: string;
  /** Cover banner box (positioning context for the image, scrim and title). */
  coverBox: string;
  /** The banner image itself. */
  coverImg: string;
  /**
   * Gradient scrim between the photo and the title.
   *
   * A cover is an arbitrary photograph, so no text/ground pairing over it can
   * be measured in advance - the usual DESIGN.md §2 method does not apply. The
   * scrim removes the variable instead of guessing: at the bottom, where the
   * title sits, it is --color-ink at 85% opacity, so the compositied ground is
   * at worst rgb(67,67,67) (the scrim over a pure-white photo) and white text
   * measures 9.87:1 there. Over a darker photo it only improves, approaching
   * 15.91:1. That is why the cover title is always on-ink, whatever the
   * template's background mode.
   */
  coverScrim: string;
  /** The title block laid over the bottom of the banner. */
  coverContent: string;
  /** Title when it sits over the banner rather than on the page ground. */
  coverTitle: string;
  /** Tagline when it sits over the banner. */
  coverTagline: string;
  /** Round logo lockup. */
  logoBox: string;
  /** Secondary text on the PAGE ground — for a caller filling a header slot. */
  secondaryTextClass: string;
  /** An illustrative badge chip (sample data only — see sampleMenuData.ts). */
  badge: string;
  /** Treatment for an item that is not currently available. */
  unavailable: string;

  /** Decided structure, so a renderer does not re-derive it. */
  structure: TemplateStructure;
  /** True when dish images should render at all. */
  withImages: boolean;
}

/**
 * Joins class fragments, dropping empties.
 *
 * Several lookups are legitimately blank - TINTED contributes no header
 * border, a LIGHT price needs no chip wrapper - and template-literal joining
 * leaves a doubled or trailing space behind each one. That is harmless to a
 * browser but it defeats the whitespace assertion in menuTemplates.test.ts,
 * which is there to catch a lookup that returned nothing when it should have
 * returned a class.
 */
function cx(...parts: (string | false | null | undefined)[]): string {
  return parts.filter(Boolean).join(' ');
}

export function resolveTemplateClasses(
  def: Pick<MenuTemplateDefinition, 'backgroundMode' | 'accentToken'> & Partial<MenuTemplateDefinition>,
): ResolvedTemplateClasses {
  const bg = BACKGROUND_CLASSES[def.backgroundMode];
  const isDark = def.backgroundMode === 'DARK';
  const s = normaliseStructure(def);
  const withImages = imagesVisible(s);

  return {
    page: bg.page,
    header: cx('px-4 py-8 flex flex-col', HEADER_ALIGN[s.headerAlignment], bg.headerBorder),
    title: cx(TITLE_FONT_CLASS[s.fontFamily], 'text-title-l', bg.heading),
    tagline: cx('mt-1 text-body-m', bg.secondaryText),
    categoryHeading: cx(
      'text-label-s uppercase mt-8 mb-2 px-4',
      isDark ? bg.secondaryText : ACCENT_TEXT[def.accentToken],
    ),
    itemName: cx('text-title-s', bg.itemName),
    priceWrap: isDark ? cx('rounded-pill', ACCENT_FILL[def.accentToken], 'px-2 py-0.5') : '',
    price: isDark
      ? 'text-label-m text-ink [font-variant-numeric:tabular-nums]'
      : cx('text-label-m', ACCENT_TEXT[def.accentToken], '[font-variant-numeric:tabular-nums]'),
    strikePrice: cx('text-label-s', bg.secondaryText, 'line-through [font-variant-numeric:tabular-nums]'),
    description: cx('text-body-m', bg.secondaryText, 'mt-1 line-clamp-2'),
    swatch: cx('h-4 w-4 rounded-pill border-2', bg.swatchBg, ACCENT_SWATCH_BORDER[def.accentToken]),

    itemsContainer: cx(LAYOUT_CONTAINER[s.layoutStructure], dividerClass(s.itemCardStyle, def.backgroundMode)),
    item: cx(CARD_CLASSES[s.itemCardStyle][def.backgroundMode], LAYOUT_ITEM_EXTRA[s.layoutStructure]),
    itemInner: ITEM_INNER[withImages ? s.imagePosition : 'NONE'],
    imageBox: cx(IMAGE_BOX[s.imagePosition][s.imageAspectRatio], bg.imagePlaceholder),
    imagePlaceholder: bg.imagePlaceholder,
    fontClass: FONT_CLASS[s.fontFamily],
    headerAlign: HEADER_ALIGN[s.headerAlignment],
    coverBox: 'relative w-full aspect-video overflow-hidden',
    coverImg: 'absolute inset-0 h-full w-full object-cover',
    // to-transparent at the top so the photo reads; from-ink/85 at the bottom
    // where the title sits — see the coverScrim doc comment for the measurement.
    coverScrim: 'absolute inset-0 bg-gradient-to-t from-ink/85 via-ink/40 to-transparent',
    coverContent: cx('absolute inset-x-0 bottom-0 flex flex-col p-4', HEADER_ALIGN[s.headerAlignment]),
    coverTitle: cx(TITLE_FONT_CLASS[s.fontFamily], 'text-title-l text-on-ink'),
    coverTagline: 'mt-1 text-body-m text-on-ink-muted',
    logoBox:
      'mb-2 h-14 w-14 overflow-hidden rounded-pill border-2 border-on-ink/80 bg-surface object-cover',
    secondaryTextClass: bg.secondaryText,
    // Ink on a soft ground is the only pairing DESIGN.md §3.3 sanctions for a
    // status-style chip, and it stays legible on a dark page too because the
    // chip carries its own light ground.
    badge: 'inline-flex items-center rounded-pill bg-surface-2 px-2 py-0.5 text-label-s text-ink',
    unavailable: 'opacity-50',

    structure: s,
    withImages,
  };
}
