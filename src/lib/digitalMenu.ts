/**
 * Client for the phase-1 Digital Menu URL family
 * (/m/{merchant-slug}[/{branch-slug}]). Deliberately separate from
 * src/lib/tenant.ts and src/lib/api.ts's table-scoped menu calls — this is
 * the new, read-only public surface; the existing table-scoped flow
 * (CustomerMenuPage, resolveMenuTarget) is untouched.
 */

import { API_BASE_URL } from './api';

// API_BASE_URL (src/lib/api.ts) already carries a trailing "/api" segment in
// both dev ("/api") and production ("https://api.qrserve.com/api") — see
// .env.development / .env.production. This module's own paths (below) are
// already rooted at "/api/..." too, so naively prepending API_BASE_URL as-is
// would double up into "/api/api/...". Strip that trailing segment to get
// just the gateway origin (empty string in dev, where a bare "/api/..." path
// already resolves correctly via the Vite proxy).
const API_ORIGIN = API_BASE_URL.replace(/\/api\/?$/, '');

export interface DigitalMenuResolution {
  merchantId: string;
  merchantSlug: string;
  branchId: number;
  branchSlug: string;
  branchName: string;
}

export interface DigitalMenuProduct {
  id: number;
  name: string;
  description: string | null;
  price: number;
  /** Server-computed: discounted price if a promotion is active right now, else equal to price. */
  effectivePrice: number;
  image: string | null;
  available: boolean;
  preparationTime: number;
}

export interface DigitalMenuCategory {
  id: number;
  name: string;
  items: DigitalMenuProduct[];
}

/** A MenuTemplateEntity key (see src/lib/menuTemplates.ts) — admin-managed, not a fixed set. */
export type MenuTemplateStyle = string;

export interface DigitalMenuResponse {
  templateStyle: MenuTemplateStyle;
  categories: DigitalMenuCategory[];
}

// `encodeURIComponent` leaves !'()* unescaped (they're valid in a URI per
// RFC 2396 but not RFC 3986), so a literal apostrophe in a slug would pass
// through untouched. Encode those too for a fully RFC 3986-safe path segment.
function encodeSlug(slug: string): string {
  return encodeURIComponent(slug).replace(
    /[!'()*]/g,
    (char) => `%${char.charCodeAt(0).toString(16).toUpperCase()}`,
  );
}

export function buildDigitalMenuApiPath(merchantSlug: string, branchSlug?: string): string {
  const base = `/api/v1/public/digital-menu/${encodeSlug(merchantSlug)}`;
  return branchSlug ? `${base}/${encodeSlug(branchSlug)}` : base;
}

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_ORIGIN}${path}`);
  if (!response.ok) {
    throw new Error(`Request to ${path} failed with ${response.status}`);
  }
  return response.json() as Promise<T>;
}

async function postJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${API_ORIGIN}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`Request to ${path} failed with ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export function resolvePrimaryBranch(merchantSlug: string): Promise<DigitalMenuResolution> {
  return getJson<DigitalMenuResolution>(buildDigitalMenuApiPath(merchantSlug));
}

export function resolveBranch(merchantSlug: string, branchSlug: string): Promise<DigitalMenuResolution> {
  return getJson<DigitalMenuResolution>(buildDigitalMenuApiPath(merchantSlug, branchSlug));
}

export function fetchBranchMenu(branchId: number): Promise<DigitalMenuResponse> {
  return getJson<DigitalMenuResponse>(`/api/menu/branch/${branchId}`);
}

export interface ReviewSummary {
  averageRating: number;
  count: number;
}

export function fetchReviewSummary(branchId: number): Promise<ReviewSummary> {
  return getJson<ReviewSummary>(`/api/v1/public/branches/${branchId}/reviews/summary`);
}

export function submitReview(
  branchId: number,
  data: { rating: number; comment?: string; customerName?: string },
): Promise<void> {
  return postJson<void>(`/api/v1/public/branches/${branchId}/reviews`, data);
}
