/**
 * Client for the phase-1 Digital Menu URL family
 * (/m/{merchant-slug}[/{branch-slug}]). Deliberately separate from
 * src/lib/tenant.ts and src/lib/api.ts's table-scoped menu calls — this is
 * the new, read-only public surface; the existing table-scoped flow
 * (CustomerMenuPage, resolveMenuTarget) is untouched.
 */

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
  image: string | null;
  available: boolean;
  preparationTime: number;
}

export interface DigitalMenuCategory {
  id: number;
  name: string;
  items: DigitalMenuProduct[];
}

export interface DigitalMenuResponse {
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
  const response = await fetch(path);
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
