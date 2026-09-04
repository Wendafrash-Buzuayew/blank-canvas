# QRServe Design System

**Status:** normative. This document is the single source of truth for colour, type, spacing and
component behaviour across the QRServe frontend. Where this document and the code disagree, the
code is wrong.

**Scope:** the React frontend in `src/`. It does not govern backend payload shapes, but it does
govern how backend enum values (`OrderStatus`, `TableStatus`, `FulfilmentType`, `SettlementMode`)
are rendered — see §3.7 and §6.10.

**Why it exists.** An audit of the current frontend found two disjoint visual systems with zero
file overlap: seven files use the token system in `src/index.css`, and thirty-two use stock
Tailwind palettes plus 115 raw hex literals. The token system is the better of the two and is
extended here; the second one is retired in §3.10.

**No code in this document.** This is a specification. Token *names* are given so implementation
has a target, but no CSS, no component source, and no copy-pasteable snippets. Implementation is a
separate task.

---

## 1. Principles

One product family, four physical contexts. A screen must declare which context it belongs to,
because the contexts have genuinely different requirements and a shared component that ignores them
will be wrong in at least one.

| Context | Read at | Device | Register |
|---|---|---|---|
| **Customer** | 30cm, one-handed, in a restaurant, possibly on cellular | own phone | inviting, generous, image-led |
| **Waiter** | 30cm, moving, glanced at between tasks | own phone | triage — urgency first, density second |
| **Kitchen** | **1.5–3m**, often no touch, hot and bright | wall screen | glanceable, dark ground, fill-carries-state |
| **Admin / merchant** | 50cm, seated, sustained | desktop or mini-app frame | dense and controlled, no decoration |

Four rules that follow from the table and are not negotiable:

1. **Colour is a signal, not decoration.** A hue that means "preparing" in the kitchen may not
   appear as an accent on a settings page. §3.7 assigns every semantic colour exactly one meaning.
2. **Fill before text at distance.** In the kitchen register, state is carried by the whole card's
   background. The text label is confirmation, not the signal.
3. **The customer context is the only one allowed to be expressive.** Gradients, brand-tinted
   elevation, and the Fresh Green accent are scoped to it. Everywhere else, restraint is the style.
4. **Every colour pairing in this document has a measured contrast ratio.** No pairing enters the
   system on the strength of looking fine.

---

## 2. Contrast method

Every ratio in this document was computed under WCAG 2.1 (sRGB relative luminance, `(L1+0.05)/(L2+0.05)`)
against the two real page grounds — `canvas` `#F5F6F7` and `surface` `#FFFFFF` — not against
theoretical white. Thresholds used:

| Requirement | Ratio | Applies to |
|---|---|---|
| AA body text | **4.5:1** | anything below 18.66px bold / 24px regular |
| AA large text | **3.0:1** | ≥18.66px bold or ≥24px regular |
| AA non-text | **3.0:1** | icons, borders that carry meaning, focus rings, chart marks |

A token that clears 3.0 but not 4.5 is a **large-text-or-fill-only** token and is marked as such.
On the Safaricom palette these are `brand-dark`/`success` (4.4966 — a hair under) and
`brand-accent`/`danger-fill` (4.11 with ink, 3.87 with white). A token below 3.0 is **fill-only**:
`brand` (2.88), `warn-fill` (1.76), and every KDS ticket fill. Between them these are the most
common source of the failures catalogued in §3.10 — including the ones this palette introduces,
since the hero colour is one of them.

---

## 3. Colour

The palette is Safaricom's, taken from the brand's primary and secondary colour sheets. Two
consequences run through everything below and are worth stating before the tables:

**A green brand inverts the collision map.** The previous palette reasoned that "hue is spoken for
by the brand" because the brand was red, which pushed `danger` to a dark red separated by
lightness. With a green hero colour the pressure moves: a distinct `success` green becomes
impossible, red becomes available for errors — except that Safaricom Red is itself a *primary*
brand colour — and the yellow-greens start colliding with the warm accents. Every assignment in §3.3
was re-derived from measurement, not carried over.

**Safaricom Green is a fill, not an ink.** It measures 2.88 against white. It cannot be text at any
size, it cannot carry white text or icons, and it cannot be a focus ring. This is the single most
important constraint in the system and it is not a matter of taste.

### 3.0 The brand sheet disagrees with itself

Both primaries are printed with a HEX code and an RGB triple that do not match:

| Colour | Printed HEX | Printed RGB | RGB as hex | OKLab apart |
|---|---|---|---|---|
| Safaricom Green | `#3DAE2B` | R44 G179 B74 | `#2CB34A` | 0.024 |
| Safaricom Red | `#F5333F` | R239 G55 B62 | `#EF373E` | 0.011 |

Both differences are far below the ~0.10 perceptual threshold, so either value *is* the brand to
the eye. **This system uses the printed HEX values**, because hex is the digital reference and a
single source avoids two "correct" greens drifting through the codebase. If the brand team confirms
the RGB triples are authoritative, changing the two token values is a one-line edit each and nothing
else in this document moves.

### 3.1 Brand

| Token | Hex | Pantone | On canvas | White on it | Ink on it | Role |
|---|---|---|---|---|---|---|
| `--color-brand` | `#3DAE2B` | 361 C | 2.66 | **2.88 ✗** | 5.52 | hero fill, brand marks, large surfaces, KDS ready |
| `--color-brand-dark` | `#00833E` | 348 C | **4.4966** | 4.87 | 3.28 | **the interactive green** — buttons, links, active nav, focus ring |
| `--color-brand-press` | `#00612E` | derived | 7.06 | 7.64 | — | hover/pressed, and brand-coloured **body** text |
| `--color-brand-soft` | `#E8F6E5` | tint | — | — | 14.20 | brand banner/chip ground |
| `--color-brand-fg` | `#FFFFFF` | — | — | — | — | foreground on `brand-dark` / `brand-press` fills |
| `--color-brand-accent` | `#F5333F` | Red 032 C | 3.87 | 3.87 | 4.11 | the second primary — brand moments, **not** errors |

Four rules, each from the numbers above:

1. **Never white on `--color-brand`** (2.88). The one exception is the QRServe brand mark, which
   WCAG exempts as a logotype under both 1.4.3 and 1.4.11 — and even there the product name sits
   beside it as real text. Anywhere the pair would *label* or *signal*, use `brand-dark`.
2. **`--color-brand` is never text and never a focus ring.** As a ring it bottoms out at 2.44 on
   `danger-soft` and misses 3.0 on all three light grounds.
3. **`--color-brand-dark` is large-text-or-fill-only.** It measures **4.4966** on `canvas` — under
   4.5, so it fails AA body text by a hair. It is the right fill and the right ring; for brand-
   coloured body copy at 14px use `--color-brand-press` (7.06).
4. **`--color-brand-accent` carries no small text either way** — 3.87 with white, 4.11 with ink. It
   is for brand expression at size, not for UI labels, and it is not the error colour (§3.3).

### 3.2 Neutral ramp

Built on Neutral Black C and PANTONE 48 C.

| Token | Hex | Pantone | On canvas | On surface | Role |
|---|---|---|---|---|---|
| `--color-ink` | `#222222` | Neutral Black C | 14.70 | 15.91 | headings, primary body text, dark fills |
| `--color-ink-2` | `#3C4043` | derived | 9.67 | 10.47 | secondary text, dark-surface borders |
| `--color-muted` | `#5F6368` | derived | **5.59** | 6.05 | secondary and metadata text |
| `--color-line` | `#DFE2E4` | 48 C tint | — | — | hairline borders, dividers, skeleton fill |
| `--color-line-strong` | `#C1C5C8` | 48 C | — | — | defined borders, chip outlines, table rules |
| `--color-canvas` | `#F5F6F7` | — | — | — | page ground |
| `--color-surface` | `#FFFFFF` | White (primary) | — | — | cards, sheets, modals, table rows |
| `--color-surface-2` | `#FAFBFB` | — | — | — | inset panels, hover ground, table header |

> **The previous ramp's card-only restriction on `muted` is lifted.** The old `#6b7280` measured
> 4.47 on canvas and failed; `#5F6368` measures **5.59**, so secondary text may now sit directly on
> the page ground. Code written against the old rule used `ink-2` as a workaround — that is no
> longer necessary.

**Text on a dark ground** (the console sidebar, `ink` / `ink-2`) still needs its own pair, because
`--color-muted` measures **2.63 on `ink`** and 1.73 on `ink-2`:

| Token | Hex | Pantone | On ink | On ink-2 | Role |
|---|---|---|---|---|---|
| `--color-on-ink` | `#FFFFFF` | White | 15.91 | 10.47 | primary and active text on a dark ground |
| `--color-on-ink-muted` | `#C1C5C8` | 48 C | 9.16 | 6.02 | secondary and inactive text on a dark ground |

`on-ink-muted` is Cool Grey itself — the brand's own grey lands exactly where a dark-ground
secondary needs to be.

### 3.3 Semantic status

Because no saturated Safaricom colour can be both readable text and a legible fill, **each meaning
gets a text value, a fill value, and a soft ground.** The measured collisions that forced these
choices:

| Pair | OKLab | Consequence |
|---|---|---|
| any palette green ↔ brand green | **0.08** | a separate `success` green is impossible — success *is* the brand green |
| Orange ↔ Safaricom Red | **0.06** | one signal, not two — only one of them may appear in a given register |
| Fresh Green ↔ Yellow | **0.11** | Fresh Green cannot stand beside `warn`; it is not a status colour |
| Turquoise ↔ brand green | **0.13** | the tightest pair that ships — see the caveat below |

| Meaning | Text | Fill | Soft ground |
|---|---|---|---|
| **success** | `--color-success` `#00833E` (4.50 — large only) | `--color-success-fill` `#3DAE2B` (ink 5.52) | `--color-success-soft` `#E8F6E5` (ink 14.20) |
| **warn** | `--color-warn` `#8A5A00` (5.48) | `--color-warn-fill` `#FFB600` · 7549 C (ink 9.05) | `--color-warn-soft` `#FFF3D6` (ink 14.43) |
| **danger** | `--color-danger` `#A81622` (6.92) | `--color-danger-fill` `#F5333F` · Red 032 C (ink 4.11 — large only) | `--color-danger-soft` `#FDE7E8` (ink 13.46) |
| **info** | `--color-info` `#00695C` (6.11) | `--color-info-fill` `#00AF9A` · 3275 C (ink 5.76) | `--color-info-soft` `#E0F5F2` (ink 14.03) |

**Success and the brand are the same colour, deliberately.** Every green in the Safaricom palette
sits 0.08 OKLab from the hero green, i.e. below the threshold at which two colours read as
different. Rather than ship two greens that users cannot tell apart and pretend they mean different
things, success *is* the brand green. In a green-branded product this is coherent: a completed
payment being Safaricom Green is on-brand and unambiguous.

**`--color-danger` is a derived dark shade of Safaricom Red, not the brand red.** The brand red
clears neither white (3.87) nor ink (4.11) at 4.5, so it cannot label a destructive button. `#A81622`
takes white at 7.49. The brand red remains available as `--color-danger-fill` for large fills.

**Yellow can never be text** (1.76 on white). Fill or icon only, with ink over it.

**Default to `ink` on every soft ground.** Only `warn` (5.37), `danger` (6.34) and `info` (5.83) may
be used as coloured text on their own soft ground. `success`/`brand-dark` over `brand-soft` measures
**4.34 and fails** — brand and success soft grounds take ink text only.

**The Turquoise caveat.** `info-fill` sits 0.13 from the hero green — distinguishable, but the
tightest pair in the system, and the Safaricom palette contains no blue to escape to. This is
tolerable only because §7.3 already requires every chip to state its state in text; colour is never
the sole carrier. If `info` and `success` chips are ever placed adjacent as the only
differentiator, that layout is wrong regardless of the palette.

### 3.4 Kitchen register

A separate, self-contained palette for the wall display. It is **not a dark theme** — the app has no
dark mode (§3.8) — it is a different physical context with its own ground.

| Token | Hex | Pantone | Role | Ink on it | White on it |
|---|---|---|---|---|---|
| `--color-kds-bg` | `#101314` | — | screen ground | — | 18.66 |
| `--color-kds-panel` | `#1C2022` | — | column panel, controls | — | 16.42 |
| `--color-kds-line` | `#6B7479` | — | panel borders | — | — |
| `--color-kds-new` | `#FF4D00` | 1655 C | Incoming ticket fill | **4.78 ✓** | 3.33 ✗ |
| `--color-kds-prep` | `#FFB600` | 7549 C | Preparing ticket fill | **9.05 ✓** | 1.76 ✗ |
| `--color-kds-ready` | `#3DAE2B` | 361 C | Ready ticket fill | **5.52 ✓** | 2.88 ✗ |
| `--color-kds-fg` | `#222222` | Neutral Black C | text on any ticket fill | — | — |

**Text on a KDS ticket is always `kds-fg` (ink), never white** — white measures 3.33 / 1.76 / 2.88
against the three fills, all failing. White *is* correct on `kds-bg` and `kds-panel`.

The three fills are well separated: new↔prep 0.22, prep↔ready 0.25, new↔ready 0.33.

`--color-kds-line` is `#6B7479` and not a darker grey because the border carries meaning and needs
3.0 non-text on **both** dark grounds. `#6B7479` gives 3.91 on `kds-bg` and 3.44 on `kds-panel`; the
obvious darker choice `#333A3D` measured 1.61 and 1.42.

**Orange appears here and nowhere else.** It is 0.06 OKLab from Safaricom Red — the same signal —
so the two may never share a register. Orange is the kitchen's "new"; the brand red lives in the
light UI as `danger-fill`. They never co-occur.

### 3.5 Customer accent

| Token | Hex | Pantone | Scope |
|---|---|---|---|
| `--customer-accent` | `#C2D500` | 382 C (Fresh Green) | **only** inside a `[data-view="customer"]` subtree |

Deliberately not a global colour token. Fresh Green sits 0.11 OKLab from Yellow — which is both
`warn-fill` and `kds-prep` — so a global `accent` utility would let someone paint it onto a warning
chip or a kitchen ticket and silently destroy the warn and prep/ready distinctions. Scoping it to
the customer subtree means the utility does not exist anywhere it could collide.

It is also the third stop of the brand gradient, which is why the gradient is the plain two-stop
Safaricom green everywhere except the customer surface, where §1 permits expression.

### 3.6 Partner colours

| Token | Hex | On canvas | Ink on it | Scope |
|---|---|---|---|---|
| `--color-partner-mpesa` | `#0DA64B` | 2.95 | 5.99 | co-branding and attribution **only** |

M-PESA green may appear only in a logo lockup, a payment-method row, or a "powered by"
attribution. It may never indicate state, selection, or success.

On the Safaricom palette this is doubly true: M-PESA green now sits **0.06 OKLab from the brand
greens**, so as an independent signal it would not read as "M-PESA" — it would read as the brand
itself. Active navigation state is `--color-brand-dark`, in every shell.

### 3.7 Status → colour assignment

The only sanctioned mapping. `OrderStatus` and `TableStatus` mirror backend enums; do not invent
values.

**Order status** (`src/lib/orderStatus.ts`). Two registers, because the same order is shown to a
guest on a phone and to a cook on a wall.

| `OrderStatus` | Customer / console | Kitchen ticket | Guest-facing label |
|---|---|---|---|
| `PENDING` | `info-soft` + ink | `kds-new` (flashing) | "Received" |
| `ACCEPTED` | `info-soft` + ink | `kds-new` | "Received" |
| `PREPARING` | `warn-soft` + ink | `kds-prep` | "Cooking" |
| `READY` | `success-soft` + ink | `kds-ready` | "Ready" |
| `DELIVERED` | `success-soft` + ink | — (leaves board) | "Served" |
| `PAID` | `surface-2` + `muted` | — | "Paid" |
| `CANCELLED` | `danger-soft` + ink | — | "Cancelled" |

`PENDING` and `ACCEPTED` share a presentation on purpose: the distinction is operational, not
something a guest or a cook acts on differently.

**Table status** (`TABLE_STATUS`):

| Value | Ground | Text |
|---|---|---|
| `AVAILABLE` | `success-soft` | `ink` |
| `OCCUPIED` | `warn-soft` | `ink` |
| `RESERVED` | `info-soft` | `ink` |

**Fulfilment type** (`FulfilmentType`) is **identity, not status** — render as a neutral chip
(§6.2), never coloured: `DINE_IN` "Dine-in", `TAKEOUT` "Takeout", `DELIVERY` "Delivery".
Same for `SettlementMode`: `PREPAID` "Prepaid", `TAB` "Open tab".

### 3.8 No dark mode

The app has no dark theme and this document does not introduce one. There are currently zero `dark:`
variants in the codebase, and the kitchen register is a context, not a theme. **Do not add `dark:`
variants ad hoc** — a half-themed app is worse than an unthemed one. If dark mode becomes a
requirement it is a project with its own token pass, not a per-component decision.

### 3.9 Secondary palette: where each of the eight is used

The brand sheet is explicit that the secondary palette supports the primary, is used "in moderation",
"should never be used in isolation", and is for "accents only, in illustrations, badges, price points,
icons, sub icons, identifiers, signifiers, info-graphics". Status chips are badges and signifiers, so
status use is sanctioned — but every one of the eight has a single assigned home, and colours with no
home are not available for improvisation.

| Secondary | Hex | Assigned to | Notes |
|---|---|---|---|
| BLACK | `#222222` | `--color-ink`, `--color-kds-fg` | the product's text colour |
| COOL GREY | `#C1C5C8` | `--color-line-strong`, `--color-on-ink-muted` | plus `--color-line` as a tint |
| DARK GREEN | `#00833E` | `--color-brand-dark`, `--color-success` | the interactive green |
| YELLOW | `#FFB600` | `--color-warn-fill`, `--color-kds-prep` | never text (1.76) |
| ORANGE | `#FF4D00` | `--color-kds-new` **only** | 0.06 from Safaricom Red — see §3.4 |
| TURQUOISE | `#00AF9A` | `--color-info-fill` | 0.13 from brand green — see §3.3 |
| FRESH GREEN | `#C2D500` | `--customer-accent`, scoped | 0.11 from Yellow — not a status colour |
| BURNT SIENNA | `#7B4942` | **unassigned** | illustration and photography only; no UI role |

Burnt Sienna has no UI role on purpose: it has no meaning to carry, and 7.31 on white would make it
a tempting body-text colour that would then compete with `ink` for the same job.

### 3.10 Retired colours and migration map

Everything here is **non-compliant** and must not appear in new code. Counts are from the current
tree.

The whole previous palette is retired, not adjusted. The old brand crimson is not a Safaricom
colour at all.

| Retired | Uses | Replacement | Why |
|---|---|---|---|
| `#E60028` literal (old brand) | 94 | `--color-brand` / `--color-brand-dark` per role | **wrong brand** — crimson is not in the Safaricom palette |
| `#CC0024` literal | 21 | `--color-brand-dark` | wrong brand; matched no token even before |
| `#0DA64B` literal | 2 | `--color-partner-mpesa`, and only per §3.6 | 2.95 contrast; now 0.06 from the brand greens |
| `#1E1E1E` literal | 2 | `--color-ink` (`#222222`) | Neutral Black C is the brand's black |
| `bg-indigo-600` badges | 40 (indigo/blue family) | neutral identity chip (§6.2) | not a Safaricom colour, and role is identity not status |
| `text-slate-400` on light | — | `--color-muted` | **2.56:1 on white — a live AA failure**, used at 10px |
| `text-slate-400` on dark | — | `--color-on-ink-muted` | 7.47 on the old ink, so it was *correct* there — retired as a literal, not as a value |
| `text-emerald-600` | — | `--color-success-fill` as fill, `ink` as text | 3.77 as text: fails AA |
| `text-amber-600` | — | `--color-warn-fill` as fill, `ink` as text | 3.19 as text: fails AA |
| `text-red-500` | — | `--color-danger` | 3.76 as text: fails AA (acceptable as icon only) |
| `bg-slate-50` page ground | — | `--color-canvas` | — |
| `bg-slate-900` sidebar | — | `--color-ink` | — |
| `--color-brand-glow` | — | *removed* | it was a crimson gradient mid-stop; the Safaricom gradient is two green stops |

**Full `slate` → token map** (453 uses to migrate):

| Stock | Token | Note |
|---|---|---|
| `slate-50` | `canvas` (page) / `surface-2` (inset) | pick by role |
| `slate-100` | `surface-2` | hover ground |
| `slate-200` | `line` | hairline |
| `slate-300` | `line-strong` | defined border |
| `slate-400` | `muted` (light) / `on-ink-muted` (dark) | **contrast fix** on light, not a like-for-like swap |
| `slate-500`, `slate-600` | `muted` | now valid on the page ground too |
| `slate-700`, `slate-800` | `ink-2` | |
| `slate-900` | `ink` | |

## 4. Typography

### 4.1 Families

**Proxima Nova** is the Safaricom brand typeface and the product's only family. It is self-hosted
from `src/fonts/` as vendor-supplied WOFF2 and fingerprinted by Vite — no CDN, no third party in the
critical path.

| Token | Stack | Role |
|---|---|---|
| `--font-display` | **Proxima Nova**, `ui-sans-serif`, `system-ui`, `sans-serif` | headings, order numbers, marketing |
| `--font-sans` | **Proxima Nova**, `ui-sans-serif`, `system-ui`, `sans-serif` | body, UI, forms, tables |

The split is retained even though both stacks name the same family: it keeps a heading's and a table
cell's fallback behaviour separable, and it is where a second family attaches if the UI is localised
to Amharic (§9.5).

#### Installed faces

Weights verified from each file's `OS/2` table, not its filename. Only faces with an assigned role
are declared — declaring the rest would add ~280 KB of fetchable weight carrying nothing.

| Face | Weight | Style | Size | Serves |
|---|---|---|---|---|
| Regular | 400 | normal | 68 KB | `body-l`, `body-m` |
| Italic | 400 | italic | 72 KB | kitchen-ticket item notes — the only italic role in the product |
| Semibold | 600 | normal | 69 KB | `title-s`, `label-m`, `label-s`, `kds-meta` |
| Bold | 700 | normal | 71 KB | `title-l`, `title-m`, `kds-item` |
| Black | 900 | normal | 70 KB | wordmark, `display-xl`, `display-l`, `kds-number`, `kds-action` |

**Not declared:** Light (300) and the Semibold / Bold / Black italics. The archive also supplies
these; they have no role in this system and are omitted deliberately rather than by oversight.

349 KB sits on disk but is never one download — a browser fetches only the faces a page uses. The
customer menu pulls 400/600/700; the kitchen display pulls 700/900.

**There is no Extrabold (800).** The archive provides 300 / 400 / 600 / 700 / 900. §4.2's display
roles therefore specify **900**, not 800: an 800 request resolves upward to the 900 face regardless,
and naming 900 keeps the declaration and the rendered result the same thing.

Every face uses `font-display: swap`, never `block` — the customer menu is opened on cellular in a
restaurant, and a blocked font request must not produce invisible text. Every stack keeps a real
system fallback for the same reason.

#### Coverage

Per face: Latin basic, Latin-1 Supplement and Latin Extended-A complete; Cyrillic 218; Greek 76;
**Ethiopic 0 of 384**. `tnum` is present, so §4.4's tabular figures are a real OpenType feature and
not browser synthesis.

> **Licensing.** The archive's WOFF2 files indicate a webfont licence, which is the right one for
> self-hosting. Worth a one-time confirmation that it covers a public-facing customer app rather
> than internal use only, since the customer menu serves the open internet.

### 4.2 Type scale

**12px is the floor.** Nothing in the product renders below it.

| Role | Size / line-height | Family | Weight | Tracking | Use |
|---|---|---|---|---|---|
| `display-xl` | 48 / 52 | display | 900 | −0.02em | landing hero only |
| `display-l` | 36 / 40 | display | 900 | −0.02em | landing section heads |
| `title-l` | 28 / 34 | display | 700 | −0.01em | customer menu header, page title on mobile |
| `title-m` | 22 / 28 | display | 700 | −0.01em | console page title |
| `title-s` | 18 / 24 | display | 600 | 0 | card and section headings |
| `body-l` | 16 / 24 | sans | 400 | 0 | **customer-facing body** — the default in the customer context |
| `body-m` | 14 / 20 | sans | 400 | 0 | **default body everywhere else** |
| `label-m` | 14 / 20 | sans | 600 | 0 | form labels, button text, nav items |
| `label-s` | 12 / 16 | sans | 600 | 0.04em | table column heads, chip text, metadata. Uppercase permitted **here only** |
| `numeric` | inherits | sans, tabular figures | 500 | 0 | money, counts, IDs, times, table numbers |

**Kitchen scale** — separate, and sized for a 1.5–3m read:

| Role | Size / line-height | Family | Weight |
|---|---|---|---|
| `kds-number` | 40 / 40 | display, tabular | 900 |
| `kds-item` | 24 / 28 | sans | 700 |
| `kds-action` | 20 / 24 | sans | 900 |
| `kds-meta` | 18 / 24 | sans | 600 |

### 4.3 Weight discipline

`h1`–`h4` inherit `--font-display` automatically. Everything else states its role.

- **Body copy is 400.** The current tree has 212 `font-bold` and 57 `font-black` uses; most are body
  text and metadata that should be regular. Bold is emphasis, and emphasis that is everywhere is
  nowhere.
- **900 (`font-black`) is reserved for `display-*`, `kds-number`, `kds-action` and the wordmark.**
  Nowhere else. It is the heaviest face installed and there is no 800 to step down to, so it carries
  the display roles outright.
- **Never combine 700+ weight with a size below 14px.** Heavy tiny type is the single loudest
  artefact of the current design and it reduces legibility rather than increasing it.
- Uppercase is permitted only at `label-s`, always with the 0.04em tracking.

### 4.4 Numerals

Money, order numbers, table numbers, counts, timers and durations use **tabular figures**. Digits
must not reflow when a value updates — a kitchen ticket whose order number jitters on every poll is
a defect, not a cosmetic issue.

Currency is **ETB**, formatted with thousands separators and the code as a suffix (`1,240 ETB`). No
`$`, and no bare numbers for money.

### 4.5 Retired type

| Retired | Uses | Replacement |
|---|---|---|
| `text-[10px]` | 27 | `label-s` (12px) |
| `text-[11px]` | 22 | `label-s` (12px) |
| `text-xs` as body copy | 194 | `body-m` (14px). `label-s` only where the content really is metadata |
| `font-black` outside display/KDS | 57 | `label-m` / `title-s` per role |
| `font-extrabold` | 17 | `title-*` per role |

---

## 5. Spacing and layout

### 5.1 Space scale

4px base. **Only these steps exist.** No arbitrary values.

| Step | px | Typical use |
|---|---|---|
| `1` | 4 | icon-to-label gap |
| `2` | 8 | chip padding, tight stacks |
| `3` | 12 | control inner padding, list-row gap |
| `4` | 16 | **default gap**, mobile page gutter |
| `5` | 20 | compact card padding |
| `6` | 24 | default card padding, tablet gutter, section gap |
| `8` | 32 | desktop gutter, large section gap |
| `10` | 40 | page-header bottom margin |
| `12` | 48 | major section break |
| `16` | 64 | hero padding |
| `20` | 80 | landing section rhythm |

### 5.2 Radius

| Token | px | Applies to |
|---|---|---|
| `--radius-control` | 12 | buttons, inputs, selects, small icon buttons |
| `--radius-surface-sm` | 16 | list rows, nested panels, KDS tickets |
| `--radius-xl2` | 20 | media thumbnails |
| `--radius-card` | 24 | **standard card**, modal, sheet, section panel |
| `--radius-pill` | 9999 | chips, badges, status pills, avatars, segmented controls |

The current tree uses eight competing radii (`rounded-xl` 82, `2xl` 60, `lg` 51, `full` 21, `md` 15,
`3xl` 11) against four token uses. Collapse to the five above; `rounded-md` and `rounded-3xl` are
retired.

### 5.3 Elevation

| Token | Use |
|---|---|
| `--shadow-card` | every resting card and panel |
| `--shadow-lift` | modals, sheets, dropdowns, hover on interactive cards |
| `--shadow-lift-brand` | **customer and marketing surfaces only** |

`--shadow-lift-brand` is brand-tinted elevation — the system's most distinctive move, and its most
dangerous. It is banned in the kitchen register, where a coloured shadow competes with ticket fill
for the same signal. **The KDS uses no shadows at all**; separation there comes from fill and
`kds-line` borders.

### 5.4 Layout frames

Three shells. A page belongs to exactly one and must not assume another's dimensions.

**A. Customer** — public, no chrome, own scroll context.

| Property | Value |
|---|---|
| Ground | `canvas`, with `data-view="customer"` on the root |
| Content max-width | 640 |
| Gutter | 16 |
| Bottom clearance | 144 (clears the cart bar and service dock) |
| Fixed elements | cart bar (bottom), service dock |

**B. Mini app** (merchant, phone-framed — the Phase-1 shell).

| Property | Value |
|---|---|
| Frame max-width | **430**, centred |
| Header | 56 tall, sticky, `surface` on `line` border |
| Bottom nav | 56 + `env(safe-area-inset-bottom)`, fixed |
| Gutter | 16 |
| Bottom clearance | 96 |

**C. Console** (admin / merchant / branch / waiter — the Phase-2 shell).

| Property | Value |
|---|---|
| Sidebar | **256** wide, `ink` ground, fixed; off-canvas below `lg` with a scrim |
| Header | 64 tall, sticky, `surface` |
| Content offset | 256 from `lg` up, 0 below |
| Gutter | 16 / 24 (`sm`) / 32 (`lg`) |
| Content max-width | 1280 (tables and dashboards), **720 (forms and settings)** |

**Kitchen** is not a fourth frame — it is a full-bleed region **inside** frame C. It must be
implemented as a layout that the console shell yields to, not as a page that negative-margins its
way out of the shell's padding (which is how it works today, and which breaks inside frame B).

### 5.5 Breakpoints

Stock Tailwind, unchanged: `sm` 640 · `md` 768 · `lg` 1024 · `xl` 1280 · `2xl` 1536.

`lg` is the meaningful one — it is where the console sidebar becomes permanent. Design mobile-first;
the customer and waiter contexts are phone-only in practice and their desktop rendering is a
courtesy, not a target.

### 5.6 Z-index

A closed scale. **Never write an arbitrary z-index.**

| Layer | Value | Contents |
|---|---|---|
| `base` | 0 | page content |
| `raised` | 10 | sticky table headers, floating labels |
| `header` | 20 | sticky app header |
| `scrim` | 30 | modal and drawer backdrop |
| `drawer` | 40 | off-canvas sidebar, fixed bottom nav, cart bar |
| `modal` | 50 | dialogs, sheets, popovers |
| `toast` | 60 | transient notifications |

Nothing exists above 60.

### 5.7 Touch targets

| Context | Minimum | Note |
|---|---|---|
| Customer, waiter, mini app | **44 × 44** | thumb, in motion, one-handed |
| Console (pointer) | 36 × 36 | with ≥8 spacing between adjacent targets |
| Bottom nav item | 56 tall | full-width tap area, not just the icon |
| **Kitchen** | **64 × 64** | read at distance, pressed with a knuckle or forearm |

An icon-only control smaller than its minimum must be padded up to it. Visual size and hit size are
allowed to differ; hit size is the one that must comply.

### 5.8 Motion

| Token | Duration / curve | Use |
|---|---|---|
| `--animate-pop` | 220ms, back-out | quantity change, add-to-cart confirmation |
| `--animate-rise` | 260ms, ease-out | content and card entrance |
| `--animate-slide-up` | 280ms, ease-out | bottom sheets, cart drawer |
| `--animate-breathe` | 2.2s, infinite | degraded/reconnecting realtime state |
| `--animate-flash` | 1.6s, infinite | **new kitchen ticket only** |

Rules:

- Interaction feedback is ≤280ms. Anything slower reads as lag.
- **The two infinite animations are status indicators**, not decoration. `flash` means "an unclaimed
  order is waiting"; `breathe` means "the live connection is down". Using either decoratively
  destroys a signal that a cook relies on.
- **Under `prefers-reduced-motion: reduce`, `flash` and `breathe` must degrade to a static
  high-contrast state** — a solid border or badge — not to nothing. The information they carry is
  not optional, so it must survive the preference.
- Transition `background-color`, `border-color`, `opacity`, `transform`. Never `width`, `height`,
  `top`, or `left`.

---

## 6. Component guidelines

The current tree has **two** shared UI modules (`States.tsx`, `EntitySelect.tsx`) against nine
hand-rolled modals across seven files and roughly ninety individually styled buttons. Everything
below is a component that must exist once.

### 6.1 Button

**Variants** — five, and they are exhaustive:

| Variant | Resting | Hover | Text | Measured |
|---|---|---|---|---|
| `primary` | `brand-dark` fill | `brand-press` fill | `brand-fg` | 4.87 → 7.64 |
| `secondary` | `surface` fill, `line-strong` border | `surface-2` fill | `ink` | 15.91 |
| `ghost` | transparent | `surface-2` fill | `ink-2` | 10.10 |
| `destructive` | `danger` fill | darkened `danger` | white | 7.49 |
| `link` | none | underline | `brand-press` | 7.06 |

> **`primary` is `brand-dark`, not the hero green.** White on `--color-brand` measures 2.88 — a
> Safaricom Green button with a white label is unreadable, and it is the most likely mistake anyone
> implementing this palette will make. The hero green is for brand marks and large surfaces where
> the foreground is ink; `brand-dark` is the button. `link` uses `brand-press` because link text is
> body-sized and `brand-dark` is 4.4966 on canvas.

**Sizes:** `sm` 36 tall / 12 horizontal padding · `md` 44 / 16 (default) · `lg` 56 / 24 ·
`kds` 64 / 24 with `kds-action` type.

**Radius** `--radius-control`. **Text** `label-m`. Icon-only buttons are square at their size's
height and require an `aria-label`.

**States.** Every button must define all five: resting, hover, active, **focus-visible** (2px
`brand-dark` ring at 2px offset — 4.87 on surface, 4.50 on canvas, 4.12 on `danger-soft`, 3.27 on
the ink sidebar, 3.37 on `kds-panel`; worst case 3.27, clearing the 3.0 non-text threshold
everywhere), **disabled** (50% opacity, no hover, `aria-disabled`), and **loading** (spinner replaces
the icon, label persists, width does not change, button is non-submittable).

**Rules.** One `primary` per view. Destructive actions are never `primary`. A button that triggers a
network call must show loading state — silent buttons are why users double-submit. Full-width only
inside the mini-app frame, a sheet, or a KDS ticket.

### 6.2 Chip / Badge

Two kinds, and conflating them is the mistake the current code makes.

**Status chip** — communicates state. Coloured, per the §3.7 mapping. Soft ground + `ink` text,
`label-s`, `--radius-pill`, 8 horizontal / 2 vertical padding.

**Identity chip** — communicates *what a thing is*: user role, fulfilment type, settlement mode,
branch name, tenant. **Neutral only**: `surface-2` ground, `line-strong` border, `muted` text (5.84
on that ground). Never coloured. On a dark ground it takes `ink-2` / `on-ink-muted` instead.

The pre-migration role badges use `bg-indigo-600` — not a Safaricom colour, and applied to something
that is not a status. Role is identity, so it becomes an identity chip.

**Count badge** — `brand-dark` fill, `brand-fg` text (4.87), tabular figures, `--radius-pill`, min 20
wide. Caps at `99+`. Not the hero green: a count is a label, and white on `brand` is 2.88.

Colour is never the only carrier of a chip's meaning: the label text always states it (§7).

### 6.3 Card / Surface

Standard card: `surface` ground, 1px `line` border, `--radius-card`, `--shadow-card`, padding 24
(20 in the mini-app frame, where horizontal space is 430px total).

**Interactive cards** (a menu item, a table tile) get hover `--shadow-lift` and a focus-visible ring,
and must be a real `button` or `a` — not a `div` with `onClick`. **Non-interactive cards get no hover
state**, because a hover response promises a click that does nothing.

Nested surfaces step *down*: `surface` card → `surface-2` inset panel → `line` divider. Never stack
two shadows.

### 6.4 Modal / Dialog / Sheet

One implementation, three presentations: **centred dialog** (console, max-width 560),
**bottom sheet** (customer and mini app, full-width, `--radius-card` on top corners only,
`--animate-slide-up`), **side drawer** (the console's off-canvas sidebar).

Required of all three, and absent from all nine current implementations:

- Backdrop at `scrim` (30), `ink` at 40% opacity, closes on click.
- **Escape closes.** **Focus moves in on open and returns to the trigger on close. Focus is trapped
  while open.** Background scroll is locked.
- `role="dialog"`, `aria-modal="true"`, and `aria-labelledby` pointing at the visible title.
- A visible close control ≥44px, in addition to Escape.
- Content scrolls inside the dialog; the dialog does not exceed 90vh.
- Destructive confirmations name the specific object ("Delete table T42-1"), never "Are you sure?",
  and their confirm button is `destructive`, never `primary`.

### 6.5 Form field

Anatomy: label (`label-m`, always visible), control, optional hint (`label-s`, `muted`), error
(`label-s`, `danger`).

Control: 44 tall, `--radius-control`, `surface` ground, `line` border, 12 horizontal padding,
`body-m` text. Focus: `brand-dark` border + the 2px focus ring. Error: `danger` border, and the message is
tied to the input via `aria-describedby` with `aria-invalid`.

**Placeholders are never labels** — a placeholder disappears on focus, so the field becomes
unlabelled exactly when it's being filled. Required fields are marked in the label, and the marker
is explained once per form rather than relying on a bare asterisk. Validation messages say what to
do ("Price must be greater than 0"), not what happened ("Invalid input"). Currency inputs are
suffixed `ETB` and use tabular figures.

### 6.6 Data table / list

Header row: `surface-2` ground, `label-s` uppercase, `muted` text, sticky at `raised` (10).
Body rows: `surface`, `line` bottom border, 48 tall minimum, hover `surface-2`. Numeric columns are
right-aligned with tabular figures.

Row actions live in a trailing column, are icon buttons with `aria-label`s, and never rely on
row-hover alone to be discoverable — hover-only actions do not exist on touch.

**Below `md`, a table becomes a card list, not a horizontal scroll.** Each card leads with the row's
identifying field and shows at most four attributes. Every table defines its empty, loading and
error state (§6.7) — the loading state is a skeleton at the real row height, so the layout does not
jump when data lands.

### 6.7 Empty / loading / error states

Three states, one component each, required of every data-backed view.

**Loading** — skeleton shapes at the destination's real dimensions, `line` fill at 70% opacity,
`animate-pulse`. Skeletons only for first load; **subsequent refetches keep the stale content and
show a subtle activity indicator**, because replacing a populated screen with skeletons on every poll
reads as a crash. Never a bare centred spinner for a full page that has a known shape.

**Empty** — an icon in a 48 neutral circle, a `title-s` statement of what's absent, a `body-m` line
on how to fill it, and the primary action. "No tables yet" plus an "Add table" button, not
"No data".

**Error** — `danger-soft` ground, `ink` text, a plain-language cause, and a retry button. It never
shows a raw exception message; the technical detail belongs in a collapsible "Details" or the
console. A 403 says the account lacks access; a 404 says the thing is gone; a network error says the
connection failed and offers retry. Note that skeleton grid columns must be static — a class name
built by string interpolation is invisible to the compiler and produces no styling at all.

### 6.8 Navigation

**Console sidebar** — 256 wide, `ink` ground. Items 44 tall, `--radius-control`, `label-m`, icon +
label with 12 gap. Active: `brand-dark` fill (3.27 on the ink ground), `brand-fg` text (4.87 on the
fill) — **not** the hero green, whose white label measures 2.88. Inactive: `on-ink-muted` (9.16),
hover lifts to `on-ink` on an `ink-2` ground. Active state is set by route match, and it is the only
element in the sidebar allowed to carry a brand fill.

**Mini-app bottom nav** — 3–5 items max, each ≥56 tall and full-height tappable. Active:
`brand-dark` icon and label (4.87 on the surface ground). Inactive: `muted` (6.05). Bottom padding
respects `env(safe-area-inset-bottom)`.

Both: `nav` with `aria-label`, active item carries `aria-current="page"`, and the active state is
never colour-only — weight also shifts from 500 to 600.

Off-canvas behaviour below `lg`: `scrim` backdrop, Escape closes, focus trapped, focus returns to the
menu trigger.

### 6.9 Realtime and connection state

The product's core promise is that the kitchen, waiters and guest stay in sync, so the sync state
must be visible rather than inferred.

| State | Presentation |
|---|---|
| `connected` | `success-soft` + ink (console) or `kds-ready` (kitchen) pill, label "Live" |
| `connecting` / `reconnecting` | `warn` pill with `--animate-breathe`, label naming the state |
| `disconnected` | `danger` pill, static, plus a manual refresh affordance |

A stale-data indicator must appear whenever the last successful update is older than twice the poll
interval. Guest-facing progress **only ever moves forward** — a status regression from a lagging
channel is never rendered.

### 6.10 Kitchen ticket

The most constrained component in the system.

Whole card carries `kds-state-*` fill per §3.7. Text is always `kds-fg`. `--radius-surface-sm`, no
shadow, 16 padding. Layout top to bottom: table number at `kds-number` (the largest thing on screen),
elapsed time top-right at `kds-meta`, order number and guest name at `kds-meta` 80% opacity, item
lines at `kds-item` with tabular quantities, item notes italic at 70%, then a single full-width 64
action button labelled with the **next** state ("Start", "Ready", "Served") — not the current one.

New tickets carry `--animate-flash` until claimed. One action per ticket; a cook does not choose
between buttons. Column headers state the count in tabular figures so a glance gives load.

### 6.11 Customer menu item

`surface` card, `--radius-card`, 12 padding, 80×80 thumbnail at `--radius-xl2` with a `line`
placeholder when absent. Name `title-s`, description `body-m` `muted` clamped to two lines, price
`label-m` in **`brand-press`** (7.06) — not `brand-dark` (4.4966) and certainly not `brand` (2.66),
both of which fail AA at that size (§3.1).

Unavailable items stay visible at 50% opacity with an "Unavailable" identity chip and no add control;
they are not hidden, because a guest looking for a dish needs to learn it is off rather than wonder
if they missed it.

Add control is ≥44, `--radius-pill`, `brand-dark` fill with a `brand-fg` glyph. On add:
`--animate-pop` on the cart count.

### 6.12 QR display

A provisioned QR is rendered from the **server-generated** image. There is no client-side
regeneration and **no fallback rendering** — a QR that does not come from the backend is not
displayed at all, because a locally-invented code either fails to scan or, worse, scans to the wrong
destination.

Anatomy: the image on a white ground with ≥16 quiet-zone padding (never on `canvas`, never on a
tinted or gradient ground, never with a logo overlaid on the matrix), the table label beneath at
`label-m` tabular, and the payload kind as an identity chip. When no QR exists, an empty state
(§6.7) offering provisioning — not a placeholder graphic that looks like a code.

Print surfaces are pure black on pure white, ≥32mm square, with the table label in text beneath so a
damaged sticker is still identifiable.

---

## 7. Accessibility requirements

Non-negotiable, and every one of these is currently violated somewhere in the tree.

1. **Contrast.** Body text 4.5:1, large text and non-text 3.0:1, measured against the actual ground.
   §3 marks every token that cannot carry text.
2. **Focus is always visible.** A global `focus-visible` ring already exists — 2px `brand-dark` at 2px
   offset. Do not remove it per-component, and do not replace it with a colour change alone.
3. **Colour is never the sole carrier of meaning.** Every status chip states its status in text.
   Every active nav item shifts weight as well as colour. Every error pairs its colour with an icon
   and a message.
4. **Every interactive element is a real interactive element.** No `div` with `onClick`. Keyboard
   operability follows from using the right element.
5. **Every icon-only control has an accessible name.** There are currently 22 `aria-label`s across 42
   components; the true requirement is closer to one per icon button.
6. **Dialogs manage focus** — trapped while open, returned to the trigger on close, Escape closes.
7. **Touch targets meet §5.7.**
8. **Type never renders below 12px**, and never at 800+ weight below 14px.
9. **`prefers-reduced-motion` is honoured**, with the §5.8 caveat that status animations degrade to a
   static signal rather than disappearing.
10. **Live regions.** A status change the user did not initiate — an order advancing, a new ticket
    arriving — is announced via a polite live region. Silent DOM updates are invisible to a screen
    reader.
11. **Language.** The document declares `lang`. Amharic content in a predominantly English page is
    marked with its own `lang` so it is pronounced correctly.

---

## 8. Decisions taken here

Recorded because each resolves a live conflict, and reversing one should be deliberate.

| # | Decision | Rationale |
|---|---|---|
| 1 | The palette is **Safaricom's**; the previous QRServe crimson is gone entirely | crimson is not a Safaricom colour. This is a rebrand, not a tint adjustment — see §3.10 |
| 2 | Brand **hex** wins over the sheet's printed RGB | the two disagree by 0.024 / 0.011 OKLab — imperceptible, but one value must be canonical (§3.0) |
| 3 | `primary` buttons, links, active nav and the focus ring are **`brand-dark`**, not the hero green | white on `#3DAE2B` is **2.88** and the hero green fails as a ring on every light ground (worst 2.44) |
| 4 | The hero green appears only as a **fill with ink over it**, plus the logotype | 5.52 with ink; 2.88 with white. WCAG exempts the brand mark |
| 5 | **`success` is the brand green** — no separate success colour exists | every palette green is 0.08 OKLab from the brand green, below the threshold at which two colours read as different |
| 6 | `danger` is a **derived dark shade** of Safaricom Red, not the brand red | the brand red clears neither white (3.87) nor ink (4.11) at 4.5, so it cannot label a destructive button |
| 7 | **Orange appears only in the kitchen register** | 0.06 OKLab from Safaricom Red — one signal, not two; the two may never share a register |
| 8 | **Fresh Green is the customer accent**, scoped to `[data-view="customer"]` | 0.11 from Yellow, so a global utility could destroy the warn and prep/ready distinctions |
| 9 | **Burnt Sienna has no UI role** | no meaning to carry, and at 7.31 it would compete with `ink` for body text |
| 10 | Every semantic meaning gets a **text value and a fill value** | no saturated Safaricom colour can be both readable text and a legible fill |
| 11 | Role badges become **neutral identity chips** | role is identity, not status; `indigo-600` is not a Safaricom colour |
| 12 | M-PESA green is a **partner** token, restricted to attribution | 2.95 contrast, and on this palette it sits 0.06 from the brand greens — it would read as the brand |
| 13 | Default body size rises 12px → **14px** | 12px was the default at 194 uses and is below comfortable reading size for sustained use |
| 14 | 10px and 11px type are **retired** | 49 uses, all metadata, all failing legibility — and mostly in `slate-400` at 2.56:1 |
| 15 | `muted` is valid **on the page ground** (reversal of the earlier rule) | the Safaricom `#5F6368` measures 5.59 on canvas; the previous ramp's `#6b7280` was 4.47 and failed |
| 16 | `ink` is the default text colour on **all** soft grounds | `success`/`brand-dark` measures 4.34 on `brand-soft` and fails |
| 17 | Kitchen is a **region inside the console shell**, not a fourth frame | it currently negative-margins out of the shell, which breaks in the 430px mini-app frame |
| 18 | **No dark mode** | zero `dark:` variants today; a half-themed app is worse than an unthemed one |
| 19 | Radii collapse from 8 values to **5 tokens** | `rounded-md` and `rounded-3xl` retired |
| 20 | **Proxima Nova is the only family**, with Outfit/Inter as interim fallbacks | it is the Safaricom brand face; it is licensed and not yet provisioned here (§4.1) |

## 9. Open questions

These need a product decision and are deliberately unresolved here.

1. **Webfont licence scope.** The archive ships WOFF2, which indicates a webfont licence — the right
   one for self-hosting. Worth a one-time confirmation that it covers a **public-facing customer**
   app and not internal use only, since the customer menu serves the open internet.
2. **Brand sign-off on the two derived colours.** `--color-danger` `#A81622` and `--color-warn`
   `#8A5A00` are shades outside the printed palette, added because no printed colour can carry
   small text for those meanings. The brand sheet permits additions that complement the primary
   palette; this asks whether these two specifically are approved.
3. **Non-ASCII merchant names.** EMVCo tag 59 is ASCII and capped at 25 characters, so a merchant
   trading under an Amharic-only name cannot be EMVCo-provisioned. Does onboarding require a
   Latin-script trade name, or does the QR fall back to a menu link?
4. **Amharic UI.** Is the interface itself localised? **Confirmed by inspecting the installed face:
   Proxima Nova carries 0 of 384 Ethiopic codepoints.** Amharic therefore renders in whatever the OS
   substitutes, with no control over its metrics or weight. Localising the UI needs a second family
   and an Ethiopic-aware type scale — a token change, not a translation task. Verified against every
   installed face, not just one.
5. **Density mode for the console.** Sustained admin use may want a compact row height. Deferred
   until there is a user asking for it.
6. **Landing page ownership.** It is the only marketing surface in the app and the only consumer of
   `display-xl`. If it moves to a separate site, `display-*` and the brand gradient leave with it.

---

## 10. Compliance ledger

The gap between this document and the current tree, so migration can be scoped rather than guessed.

> **The rebrand changed the nature of this gap.** Before it, an unmigrated page was *inconsistent*
> with the system. Now it is **the wrong brand**: 94 hardcoded `#E60028` and 21 `#CC0024` render
> crimson in a Safaricom-green product. The migration is no longer a tidiness exercise, and the
> mixed state is visible to any user who moves between a migrated shell and an unmigrated page.

| Item | Count | Where |
|---|---|---|
| **Off-brand crimson literals** | **115** | 32 files — `#E60028` ×94, `#CC0024` ×21 |
| `slate`/`gray` classes | 453 | 32 files |
| Arbitrary bracket colour values | 145 | 32 files |
| Files on the Safaricom token system | **7** (of 39) | the 4 shell files, `States`, and the 2 router grounds |
| Files still on stock Tailwind + crimson | **32** | admin, merchant, landing, login, customer, kitchen, waiter pages |
| Sub-12px type | 49 | across the console |
| Hand-rolled modals | 9 | 7 files |
| Competing radius values | 8 | across the tree |
| Shared UI primitives that exist | 2 | `States.tsx`, `EntitySelect.tsx` |
| Live AA text failures | at least 4 classes | `slate-400`, `amber-600`, `emerald-600`, `red-500` as text |
| Dead component modules | 12 (~2,340 lines) | `components/merchant/*`, `components/admin/*`, `components/kitchen/*`, `components/auth/LoginModal`, `pages/{Admin,Branch,Waiter}Dashboard` |
| Brand typeface in use | **yes** | Proxima Nova 400/400i/600/700/900 self-hosted — §4.1 |

Note that the seven files previously counted as "using tokens exclusively" included the customer,
kitchen and waiter pages. Those consume the token *names*, so they re-coloured to Safaricom
automatically with the token values — but they were written against the old palette's contrast
budget and need re-checking against §3, not just re-reading. They are counted as unmigrated above
until that check is done.

Suggested sequence, highest leverage first:

1. ~~Migrate `DashboardLayout`, `Sidebar`, `MobileBottomNav` and `States` to tokens~~ — **done**.
2. ~~Provision Proxima Nova~~ — **done**. 400 / 400i / 600 / 700 / 900 are self-hosted and both
   stacks name Proxima Nova, so the migrated shell is on-brand in colour *and* typography.
3. **Re-check the customer, kitchen and waiter pages against §3 and §4.** They inherited the new
   palette and the new family through the tokens, so they are already green and already Proxima
   Nova — but their contrast pairings were chosen for a red brand, and their weights for a scale
   that had an 800. This is the next slice, and it is where the known failures are.
4. Extract `Button`, `Chip`, `Card`, `Modal`, `FormField` per §6 — the nine modals and ninety buttons
   collapse into these, and every one of them encodes the "never white on the hero green" rule once
   instead of ninety times.
5. Delete the 12 dead modules, so the migration surface stops being inflated by files nothing renders.
6. Migrate the admin and merchant pages page by page, retiring `slate` and the crimson literals per
   the §3.10 map.
7. Fix the contrast failures and add the missing accessible names as each page is touched.
