import React, { useState } from 'react';
import { ImageOff } from 'lucide-react';
import type { ResolvedTemplateClasses } from '../../lib/menuTemplates';

/**
 * The one renderer for a templated menu.
 *
 * WHY THIS EXISTS: the admin preview, the merchant's template picker and the
 * live customer page must be the same code. If a preview draws its own
 * approximation, then "preview before you apply" is worthless the moment the
 * two drift - and an admin picking a template for a restaurant would be
 * choosing against a lie. DigitalMenuPage, TemplateManagement's in-modal
 * preview, and TemplateManagement's full-screen preview all render THIS.
 *
 * It takes already-resolved classes rather than a definition, so it holds no
 * opinion about colour or layout: every visual decision was made by
 * resolveTemplateClasses (src/lib/menuTemplates.ts).
 */

/**
 * The minimum an item needs to render. DigitalMenuProduct
 * (src/lib/digitalMenu.ts) and SampleProduct (src/constants/sampleMenuData.ts)
 * both satisfy it, which is what lets one renderer serve live and sample data.
 */
export interface TemplatedMenuItem {
  id: number;
  name: string;
  description: string | null;
  price: number;
  effectivePrice: number;
  image: string | null;
  available: boolean;
  /** Sample data only — there is no backend badge field. See sampleMenuData.ts. */
  badges?: readonly string[];
}

export interface TemplatedMenuCategory {
  id: number;
  name: string;
  items: readonly TemplatedMenuItem[];
}

export interface TemplatedMenuProps {
  template: ResolvedTemplateClasses;
  /** Menu title — a branch name for live data, a restaurant name for sample. */
  title: string;
  tagline?: string | null;
  /**
   * An explicit banner. When absent and the template asks for a cover, the
   * renderer falls back to the menu's own best dish photo — see
   * `firstDishImage` below.
   */
  coverImage?: string | null;
  /** Round brand lockup over the banner. Merchant logo for live data. */
  logoImage?: string | null;
  categories: readonly TemplatedMenuCategory[];
  currency?: string;
  /**
   * Maps a stored image reference to a fetchable URL. Live data needs
   * resolveMediaUrl; sample data is already absolute. Defaults to identity so
   * a caller cannot accidentally double-resolve.
   */
  resolveImageUrl?: (raw: string) => string;
  /**
   * Whether to draw item badges. Off by default BECAUSE badges are sample-only:
   * rendering them for live data would show an admin a feature that does not
   * reach customers.
   */
  showBadges?: boolean;
  /**
   * Rendered under the header title — the live page puts its rating there.
   *
   * A FUNCTION, not a node, because the header sits on one of two very
   * different grounds: the page ground, or a dark scrim over the cover photo.
   * Secondary text is `muted` on the first and `on-ink-muted` on the second,
   * and only this component knows which one applied (the cover can come from
   * a fallback the caller never saw). Handing the slot the right class is what
   * stops a rating badge from rendering grey-on-dark.
   */
  headerSlot?: (secondaryTextClass: string) => React.ReactNode;
  /** Rendered after the last category — the live page puts its review form there. */
  footerSlot?: React.ReactNode;
}

const identity = (raw: string) => raw;

/**
 * An image that degrades to a placeholder instead of a broken icon.
 *
 * Sample imagery is third-party (Unsplash) and the admin may be offline or
 * behind a proxy that blocks it; a live item's image may have been deleted
 * from storage. In both cases the layout must hold its shape, because the
 * whole point of the preview is judging the layout.
 */
function ItemImage({
  src,
  alt,
  boxClass,
  placeholderClass,
}: {
  src: string | null;
  alt: string;
  boxClass: string;
  placeholderClass: string;
}) {
  const [failed, setFailed] = useState(false);

  // Reset on src change so navigating between templates/branches re-tries.
  const [lastSrc, setLastSrc] = useState(src);
  if (lastSrc !== src) {
    setLastSrc(src);
    setFailed(false);
  }

  if (!src || failed) {
    return (
      <div className={`${boxClass} ${placeholderClass} flex items-center justify-center`}>
        <ImageOff className="h-5 w-5 opacity-40" aria-hidden="true" />
      </div>
    );
  }

  return (
    <div className={boxClass}>
      <img
        src={src}
        alt={alt}
        loading="lazy"
        onError={() => setFailed(true)}
        className="h-full w-full object-cover"
      />
    </div>
  );
}

/**
 * The first dish photo in the menu, used as a banner when the merchant has no
 * cover image of their own.
 *
 * A restaurant menu that opens on a bare text heading looks unfinished, and
 * nothing in the backend gives a branch a cover image yet — but every product
 * can carry a photo, and in practice they do. Borrowing the first one is a
 * far better default than an empty header, and it disappears the moment a real
 * cover exists.
 */
function firstDishImage(categories: readonly TemplatedMenuCategory[]): string | null {
  for (const category of categories) {
    for (const item of category.items) {
      if (item.image) return item.image;
    }
  }
  return null;
}

export function TemplatedMenu({
  template,
  title,
  tagline,
  coverImage,
  logoImage,
  categories,
  currency = 'ETB',
  resolveImageUrl = identity,
  showBadges = false,
  headerSlot,
  footerSlot,
}: TemplatedMenuProps) {
  const { structure } = template;

  // An explicit cover wins; otherwise borrow a dish photo. Both go through
  // resolveImageUrl only when they came from stored data — an explicit
  // coverImage is already a full URL, a dish image is a stored reference.
  const coverSrc = coverImage ?? (() => {
    const dish = firstDishImage(categories);
    return dish ? resolveImageUrl(dish) : null;
  })();
  const showCover = structure.showCoverImage && !!coverSrc;

  return (
    // `@container` is load-bearing, not decoration: the layout classes use
    // container variants (@sm/@xl) so a grid responds to the width of THIS
    // element rather than the browser window. Without it the admin's 375px
    // mobile preview would render desktop grids. See LAYOUT_CONTAINER in
    // src/lib/menuTemplates.ts.
    //
    // fontClass sits here too, so the template's face is inherited by item
    // names, descriptions and prices. The title names its own face — see
    // TITLE_FONT_CLASS in menuTemplates.ts for why it cannot inherit.
    <div className={`@container min-h-full ${template.page} ${template.fontClass}`}>
      {showCover ? (
        // Title over the banner, on a scrim that guarantees its contrast
        // whatever the photograph is (see coverScrim in menuTemplates.ts).
        <header className={template.coverBox}>
          <img src={coverSrc!} alt="" className={template.coverImg} />
          <div className={template.coverScrim} aria-hidden="true" />
          <div className={template.coverContent}>
            {logoImage && (
              <img src={logoImage} alt="" className={template.logoBox} loading="lazy" />
            )}
            <h1 className={template.coverTitle}>{title}</h1>
            {tagline && <p className={template.coverTagline}>{tagline}</p>}
            {headerSlot?.('text-on-ink-muted')}
          </div>
        </header>
      ) : (
        <header className={template.header}>
          {logoImage && <img src={logoImage} alt="" className={template.logoBox} loading="lazy" />}
          <h1 className={template.title}>{title}</h1>
          {tagline && <p className={template.tagline}>{tagline}</p>}
          {headerSlot?.(template.secondaryTextClass)}
        </header>
      )}

      {categories.map((category) => (
        <section key={category.id}>
          <h2 className={template.categoryHeading}>{category.name}</h2>
          <div className={`px-4 ${template.itemsContainer}`}>
            {category.items.map((item) => {
              const discounted = item.effectivePrice < item.price;
              return (
                <article
                  key={item.id}
                  className={`${template.item} ${item.available ? '' : template.unavailable}`}
                >
                  <div className={template.itemInner}>
                    {template.withImages && (
                      <ItemImage
                        src={item.image ? resolveImageUrl(item.image) : null}
                        alt=""
                        boxClass={template.imageBox}
                        placeholderClass={template.imagePlaceholder}
                      />
                    )}
                    <div className="min-w-0 flex-1">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className={template.itemName}>{item.name}</div>
                          {showBadges && item.badges && item.badges.length > 0 && (
                            <div className="mt-1 flex flex-wrap gap-1">
                              {item.badges.map((badge) => (
                                <span key={badge} className={template.badge}>
                                  {badge}
                                </span>
                              ))}
                            </div>
                          )}
                        </div>
                        <div className={`shrink-0 text-right ${template.priceWrap}`}>
                          {discounted ? (
                            <>
                              <div className={template.strikePrice}>
                                {item.price} {currency}
                              </div>
                              <div className={template.price}>
                                {item.effectivePrice} {currency}
                              </div>
                            </>
                          ) : (
                            <div className={template.price}>
                              {item.price} {currency}
                            </div>
                          )}
                        </div>
                      </div>
                      {item.description && <p className={template.description}>{item.description}</p>}
                      {!item.available && (
                        <p className={`mt-1 ${template.strikePrice} no-underline`}>Unavailable today</p>
                      )}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        </section>
      ))}

      {footerSlot}
    </div>
  );
}
