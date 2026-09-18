/**
 * A realistic restaurant menu, used wherever a template has to be shown
 * without a merchant's real data behind it: the admin template builder's live
 * preview, the admin full-screen preview in Sample mode, and the merchant's
 * template picker in Menu Builder.
 *
 * WHY A SHARED CONSTANT: the previous preview was a single "Sample Dish" card
 * with lorem text, which could not show whether a layout, an image position or
 * an aspect ratio actually worked - every template looked the same in it. A
 * preview is only useful if it is dense and varied enough to break a bad
 * layout, so this set deliberately includes a long dish name, a two-line
 * description, an item with no image, an unavailable item, and a discounted
 * price.
 *
 * SHAPE: SampleProduct is a superset of DigitalMenuProduct (src/lib/digitalMenu.ts)
 * so one renderer can draw both this and a live branch menu. The extra fields
 * are presentation-only.
 *
 * IMAGERY: remote Unsplash URLs. The eight photo IDs are the ones already in
 * use in MenuBuilderPage's image presets - reused rather than replaced so this
 * file introduces no new third-party URL that has not already been exercised
 * in this app. Every consumer must still render an image failure gracefully:
 * these are third-party URLs on a network the admin may not have, and a dead
 * image must degrade to a placeholder, never break the preview.
 */

/** Illustrative only — see SAMPLE_BADGES_ARE_ILLUSTRATIVE below. */
export type SampleBadge = 'Popular' | 'Vegetarian' | 'Chef Choice' | 'New' | 'Spicy';

/**
 * Badges have NO backend equivalent: DigitalMenuProduct carries no badge
 * field, and nothing in menu-service can produce one. They exist here to show
 * how a card copes with extra chips at each layout and image position.
 *
 * Consequence, and it matters: a badge that appears in Sample mode will NOT
 * appear on a real customer menu. Any preview that renders badges has to say
 * so, or an admin will pick a template for a feature the product does not
 * have. TemplateManagement's preview labels Sample mode for exactly this
 * reason.
 */
export const SAMPLE_BADGES_ARE_ILLUSTRATIVE = true;

export interface SampleProduct {
  id: number;
  name: string;
  description: string | null;
  price: number;
  /** Mirrors the server-computed field: below `price` when a promotion is live. */
  effectivePrice: number;
  image: string | null;
  available: boolean;
  preparationTime: number;
  badges?: SampleBadge[];
}

export interface SampleCategory {
  id: number;
  name: string;
  items: SampleProduct[];
}

export interface SampleRestaurant {
  name: string;
  tagline: string;
  /** Wide banner, used when a template has showCoverImage on. */
  coverImage: string;
  logoImage: string;
  businessHours: string;
  currency: string;
  categories: SampleCategory[];
}

const UNSPLASH = (id: string, w = 800) =>
  `https://images.unsplash.com/photo-${id}?w=${w}&auto=format&fit=crop&q=80`;

export const SAMPLE_RESTAURANT: SampleRestaurant = {
  name: 'La Trattoria',
  tagline: 'Wood-fired Italian, made from scratch since 1998',
  coverImage: UNSPLASH('1604382354936-07c5d9983bd3', 1200),
  logoImage: UNSPLASH('1555507036-ab1f4038808a', 200),
  businessHours: 'Mon–Sat · 11:00 – 23:00',
  currency: 'ETB',
  categories: [
    {
      id: 1,
      name: 'Starters',
      items: [
        {
          id: 101,
          name: 'Avocado Toast',
          description: 'Sourdough, smashed avocado, chilli flakes and a poached farm egg.',
          price: 180,
          effectivePrice: 180,
          image: UNSPLASH('1525351484163-7529414344d8'),
          available: true,
          preparationTime: 10,
          badges: ['Vegetarian'],
        },
        {
          id: 102,
          name: 'Eggs Benedict',
          description: 'Toasted muffin, hollandaise, cured ham.',
          price: 210,
          effectivePrice: 175,
          image: UNSPLASH('1608039829572-78524f79c4c7'),
          available: true,
          preparationTime: 12,
          badges: ['Popular'],
        },
      ],
    },
    {
      id: 2,
      name: 'Main Courses',
      items: [
        {
          id: 201,
          // A long name on purpose: it is what wraps badly in a 3-up grid.
          name: 'Truffle Mushroom Risotto',
          description:
            'Carnaroli rice slow-stirred with wild mushrooms, aged parmesan and a finish of black truffle oil.',
          price: 280,
          effectivePrice: 280,
          image: UNSPLASH('1568901346375-23c9450c58cd'),
          available: true,
          preparationTime: 25,
          badges: ['Popular', 'Vegetarian'],
        },
        {
          id: 202,
          name: 'Artisan Margherita Pizza',
          description: 'San Marzano tomato, fior di latte, basil. Ninety seconds in the wood oven.',
          price: 320,
          effectivePrice: 320,
          image: UNSPLASH('1604382354936-07c5d9983bd3'),
          available: true,
          preparationTime: 15,
        },
        {
          id: 203,
          // No image on purpose: proves a mixed category still aligns when a
          // template shows images.
          name: 'Penne Arrabbiata',
          description: 'Garlic, chilli, tomato. Simple and hot.',
          price: 240,
          effectivePrice: 240,
          image: null,
          available: true,
          preparationTime: 14,
          badges: ['Spicy'],
        },
      ],
    },
    {
      id: 3,
      name: 'Chef Specials',
      items: [
        {
          id: 301,
          name: 'Grilled Ribeye Steak',
          description:
            'Forty-day dry-aged ribeye over open flame, bone marrow butter, charred shallot.',
          price: 650,
          effectivePrice: 650,
          image: UNSPLASH('1571877227200-a0d98ea607e9'),
          available: true,
          preparationTime: 30,
          badges: ['Chef Choice'],
        },
        {
          id: 302,
          // Unavailable on purpose: proves the sold-out treatment.
          name: 'Whole Sea Bass',
          description: 'Salt-baked, served whole for two. Ask about today’s catch.',
          price: 780,
          effectivePrice: 780,
          image: UNSPLASH('1536256263959-770b48d82b0a'),
          available: false,
          preparationTime: 35,
          badges: ['New'],
        },
      ],
    },
    {
      id: 4,
      name: 'Beverages',
      items: [
        {
          id: 401,
          name: 'Signature Negroni',
          description: 'Gin, Campari, sweet vermouth, burnt orange.',
          price: 260,
          effectivePrice: 260,
          image: UNSPLASH('1536256263959-770b48d82b0a'),
          available: true,
          preparationTime: 5,
          badges: ['Popular'],
        },
        {
          id: 402,
          name: 'Artisanal Cappuccino',
          description: 'Single-origin Yirgacheffe, double shot.',
          price: 95,
          effectivePrice: 95,
          image: UNSPLASH('1534778101976-62847782c213'),
          available: true,
          preparationTime: 4,
        },
        {
          id: 403,
          name: 'Matcha Latte',
          description: null,
          price: 120,
          effectivePrice: 120,
          image: UNSPLASH('1536256263959-770b48d82b0a'),
          available: true,
          preparationTime: 4,
          badges: ['Vegetarian'],
        },
      ],
    },
  ],
};

/**
 * A two-category, four-item slice for the in-modal preview, where the whole
 * menu would not fit and would make the modal scroll instead of showing the
 * effect of a layout change at a glance.
 */
export const SAMPLE_RESTAURANT_COMPACT: SampleRestaurant = {
  ...SAMPLE_RESTAURANT,
  categories: [
    { ...SAMPLE_RESTAURANT.categories[1], items: SAMPLE_RESTAURANT.categories[1].items.slice(0, 3) },
    { ...SAMPLE_RESTAURANT.categories[2], items: SAMPLE_RESTAURANT.categories[2].items.slice(0, 1) },
  ],
};
