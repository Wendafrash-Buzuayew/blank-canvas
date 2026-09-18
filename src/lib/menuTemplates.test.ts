// src/lib/menuTemplates.test.ts
import {
  resolveTemplateClasses,
  normaliseStructure,
  imagesVisible,
  STRUCTURE_DEFAULTS,
  LAYOUT_OPTIONS,
  CARD_STYLE_OPTIONS,
  IMAGE_POSITION_OPTIONS,
  IMAGE_ASPECT_OPTIONS,
  FONT_FAMILY_OPTIONS,
  HEADER_ALIGNMENT_OPTIONS,
  BACKGROUND_MODE_OPTIONS,
  ACCENT_TOKEN_OPTIONS,
  type BackgroundMode,
  type MenuTemplateDefinition,
} from './menuTemplates';

function ok(name: string, condition: boolean) {
  console.log(`  ${condition ? 'ok' : 'FAIL'}  ${name}`);
  if (!condition) process.exitCode = 1;
}

const base: Pick<MenuTemplateDefinition, 'backgroundMode' | 'accentToken'> = {
  backgroundMode: 'LIGHT',
  accentToken: 'BRAND',
};

// ---- null tolerance -------------------------------------------------------
// A definition read from the API has null in every structural column until
// db/manual/001-menu-template-structure.sql has run.

ok(
  'a definition with no structural fields resolves to the defaults',
  JSON.stringify(normaliseStructure({})) === JSON.stringify(STRUCTURE_DEFAULTS),
);

ok(
  'explicit nulls resolve to the defaults too',
  normaliseStructure({ layoutStructure: null, fontFamily: null, showImages: null }).layoutStructure ===
    'SINGLE_COLUMN',
);

ok(
  'resolveTemplateClasses never throws on a bare definition',
  typeof resolveTemplateClasses(base).itemsContainer === 'string',
);

ok(
  'the frontend defaults match the backend backfill (SINGLE_COLUMN/CARD_BORDERED/LEFT/SQUARE_1_1/SANS_SERIF/CENTER)',
  STRUCTURE_DEFAULTS.layoutStructure === 'SINGLE_COLUMN' &&
    STRUCTURE_DEFAULTS.itemCardStyle === 'CARD_BORDERED' &&
    STRUCTURE_DEFAULTS.imagePosition === 'LEFT' &&
    STRUCTURE_DEFAULTS.imageAspectRatio === 'SQUARE_1_1' &&
    STRUCTURE_DEFAULTS.fontFamily === 'SANS_SERIF' &&
    STRUCTURE_DEFAULTS.headerAlignment === 'CENTER' &&
    STRUCTURE_DEFAULTS.showImages === true &&
    STRUCTURE_DEFAULTS.showCoverImage === true,
);

// ---- imagesVisible: two switches, one predicate ---------------------------

ok(
  'images are hidden when the toggle is off',
  !imagesVisible(normaliseStructure({ showImages: false, imagePosition: 'LEFT' })),
);

ok(
  'images are hidden when the position is NONE',
  !imagesVisible(normaliseStructure({ showImages: true, imagePosition: 'NONE' })),
);

ok(
  'images are visible only when both agree',
  imagesVisible(normaliseStructure({ showImages: true, imagePosition: 'TOP' })),
);

ok(
  'withImages on the resolved result agrees with imagesVisible',
  resolveTemplateClasses({ ...base, showImages: false }).withImages === false &&
    resolveTemplateClasses({ ...base, imagePosition: 'NONE' }).withImages === false &&
    resolveTemplateClasses({ ...base, imagePosition: 'RIGHT' }).withImages === true,
);

ok(
  'an imageless template still lays its card out as a column',
  resolveTemplateClasses({ ...base, imagePosition: 'NONE' }).itemInner === 'flex flex-col',
);

// ---- no interpolated class names -----------------------------------------
// Tailwind only emits CSS for class names it can read as literal text. A
// resolved string containing "${" or "undefined" means a lookup missed.

const allCombinations = (() => {
  const out: string[] = [];
  for (const bg of BACKGROUND_MODE_OPTIONS) {
    for (const accent of ACCENT_TOKEN_OPTIONS) {
      for (const layout of LAYOUT_OPTIONS) {
        for (const card of CARD_STYLE_OPTIONS) {
          for (const pos of IMAGE_POSITION_OPTIONS) {
            for (const aspect of IMAGE_ASPECT_OPTIONS) {
              for (const font of FONT_FAMILY_OPTIONS) {
                for (const align of HEADER_ALIGNMENT_OPTIONS) {
                  const resolved = resolveTemplateClasses({
                    backgroundMode: bg.value,
                    accentToken: accent.value,
                    layoutStructure: layout.value,
                    itemCardStyle: card.value,
                    imagePosition: pos.value,
                    imageAspectRatio: aspect.value,
                    fontFamily: font.value,
                    headerAlignment: align.value,
                    showImages: pos.value !== 'NONE',
                    showCoverImage: true,
                  });
                  for (const [key, value] of Object.entries(resolved)) {
                    if (typeof value === 'string') out.push(`${key}=${value}`);
                  }
                }
              }
            }
          }
        }
      }
    }
  }
  return out;
})();

ok(
  `every class string across all ${BACKGROUND_MODE_OPTIONS.length}x${ACCENT_TOKEN_OPTIONS.length}x${LAYOUT_OPTIONS.length}x${CARD_STYLE_OPTIONS.length}x${IMAGE_POSITION_OPTIONS.length}x${IMAGE_ASPECT_OPTIONS.length}x${FONT_FAMILY_OPTIONS.length}x${HEADER_ALIGNMENT_OPTIONS.length} combinations is a literal`,
  allCombinations.every((s) => !s.includes('${') && !s.includes('undefined') && !s.includes('null')),
);

ok(
  'no resolved class string has a doubled or trailing space',
  allCombinations.every((s) => {
    const value = s.slice(s.indexOf('=') + 1);
    return !value.includes('  ') && value === value.trim();
  }),
);

// ---- structural classes actually differ ----------------------------------
// The bug this guards: a layout option that silently resolves to the same
// classes as another, so the admin picker has options that do nothing.

const layoutContainers = LAYOUT_OPTIONS.map(
  (o) => resolveTemplateClasses({ ...base, layoutStructure: o.value }).itemsContainer,
);
ok(
  'every layout option produces a distinct container class',
  new Set(layoutContainers).size === LAYOUT_OPTIONS.length,
);

const cardClasses = CARD_STYLE_OPTIONS.map(
  (o) => resolveTemplateClasses({ ...base, itemCardStyle: o.value }).item,
);
ok('every card style produces a distinct card class', new Set(cardClasses).size === CARD_STYLE_OPTIONS.length);

const aspectBoxes = IMAGE_ASPECT_OPTIONS.map(
  (o) => resolveTemplateClasses({ ...base, imagePosition: 'LEFT', imageAspectRatio: o.value }).imageBox,
);
ok('every image shape produces a distinct box class', new Set(aspectBoxes).size === IMAGE_ASPECT_OPTIONS.length);

const fonts = FONT_FAMILY_OPTIONS.map((o) => resolveTemplateClasses({ ...base, fontFamily: o.value }).fontClass);
ok('every typeface produces a distinct font class', new Set(fonts).size === FONT_FAMILY_OPTIONS.length);

// ---- container queries, not viewport breakpoints -------------------------
// The device toggle is only truthful if the grids respond to the container.

ok(
  'grid layouts use container-query variants, never viewport breakpoints',
  layoutContainers.every((c) => !/(^|\s)(sm|md|lg|xl):/.test(c)),
);

ok(
  'the 2-up grid stays single-column until the container reaches @sm',
  resolveTemplateClasses({ ...base, layoutStructure: 'GRID_2' }).itemsContainer.includes('grid-cols-1') &&
    resolveTemplateClasses({ ...base, layoutStructure: 'GRID_2' }).itemsContainer.includes('@sm:grid-cols-2'),
);

ok(
  'the multi-column list guards against a card splitting across columns',
  resolveTemplateClasses({ ...base, layoutStructure: 'TWO_COLUMN' }).item.includes('[break-inside:avoid]'),
);

ok(
  'only the divider card style draws hairlines on the container',
  resolveTemplateClasses({ ...base, itemCardStyle: 'MINIMAL_DIVIDER' }).itemsContainer.includes('divide-y') &&
    !resolveTemplateClasses({ ...base, itemCardStyle: 'CARD_BORDERED' }).itemsContainer.includes('divide-y'),
);

// ---- the title cannot be overridden back to the brand face ---------------

ok(
  'a serif template titles in serif, not font-display',
  resolveTemplateClasses({ ...base, fontFamily: 'SERIF' }).title.includes('font-template-serif') &&
    !resolveTemplateClasses({ ...base, fontFamily: 'SERIF' }).title.includes('font-display'),
);

ok(
  'a sans template still titles in the display face',
  resolveTemplateClasses({ ...base, fontFamily: 'SANS_SERIF' }).title.includes('font-display'),
);

// ---- colour safety on a dark ground --------------------------------------
// DESIGN.md §3.4/§3.3: no accent family has a measured dark-ground TEXT
// pairing, so a dark template must show its price as a filled chip with ink
// text instead.

const dark: BackgroundMode = 'DARK';
for (const accent of ACCENT_TOKEN_OPTIONS) {
  const resolved = resolveTemplateClasses({ backgroundMode: dark, accentToken: accent.value });
  ok(
    `DARK + ${accent.value}: price is a filled chip with ink text, not accent text`,
    resolved.priceWrap.includes('rounded-pill') && resolved.price.includes('text-ink'),
  );
}

for (const accent of ACCENT_TOKEN_OPTIONS) {
  const resolved = resolveTemplateClasses({ backgroundMode: 'LIGHT', accentToken: accent.value });
  ok(
    `LIGHT + ${accent.value}: price is accent text with no chip`,
    resolved.priceWrap === '' && !resolved.price.includes('text-ink'),
  );
}

ok(
  'a dark template never puts a white card on the ink page',
  !resolveTemplateClasses({ backgroundMode: dark, accentToken: 'BRAND', itemCardStyle: 'CARD_FLAT' }).item.includes(
    'bg-surface',
  ),
);

ok(
  'a dark template drops the shadow, which reads as nothing over near-black',
  !resolveTemplateClasses({
    backgroundMode: dark,
    accentToken: 'BRAND',
    itemCardStyle: 'ELEVATED_SHADOW',
  }).item.includes('shadow'),
);

ok(
  'secondary text on a dark ground uses the on-ink pair, never --color-muted',
  resolveTemplateClasses({ backgroundMode: dark, accentToken: 'BRAND' }).description.includes('text-on-ink-muted'),
);

// ---- cover banner ---------------------------------------------------------
// A cover is an arbitrary photograph, so the title over it cannot be
// contrast-checked against a known ground. The scrim removes the variable:
// --color-ink at 85% is at worst rgb(67,67,67) over a pure-white photo, where
// white text measures 9.87:1. These guard that mechanism, not the look.

for (const mode of BACKGROUND_MODE_OPTIONS) {
  const resolved = resolveTemplateClasses({ backgroundMode: mode.value, accentToken: 'BRAND' });
  ok(
    `${mode.value}: the cover title is on-ink regardless of background mode`,
    resolved.coverTitle.includes('text-on-ink') && !resolved.coverTitle.includes('text-ink '),
  );
  ok(
    `${mode.value}: the cover carries an ink scrim at the opacity the measurement assumes`,
    resolved.coverScrim.includes('from-ink/85'),
  );
}

ok(
  'the cover box establishes a positioning context for the scrim',
  resolveTemplateClasses(base).coverBox.includes('relative') &&
    resolveTemplateClasses(base).coverImg.includes('absolute'),
);

ok(
  'the cover title block honours the header alignment setting',
  resolveTemplateClasses({ ...base, headerAlignment: 'LEFT' }).coverContent.includes('items-start') &&
    resolveTemplateClasses({ ...base, headerAlignment: 'CENTER' }).coverContent.includes('items-center'),
);

ok(
  'a serif template also titles its cover in serif',
  resolveTemplateClasses({ ...base, fontFamily: 'SERIF' }).coverTitle.includes('font-template-serif'),
);

ok(
  'secondaryTextClass reports the PAGE ground, so a header slot on a light page stays muted',
  resolveTemplateClasses({ ...base }).secondaryTextClass === 'text-muted' &&
    resolveTemplateClasses({ backgroundMode: 'DARK', accentToken: 'BRAND' }).secondaryTextClass ===
      'text-on-ink-muted',
);

console.log(process.exitCode ? 'FAILED' : 'all menuTemplates tests passed');
