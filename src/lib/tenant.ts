/**
 * Tenant addressing on the client.
 *
 * Each restaurant is served at {merchantSlug}.{PUBLIC_BASE_DOMAIN}, and the same
 * app also has to keep working on a bare host — the landing page's demo link
 * (/menu/demo/main/1) and local development both use the path form.
 *
 * The rule, and it is one rule: the host label when present, the path parameter
 * otherwise.
 */

/**
 * Kept in step with the backend's PUBLIC_BASE_DOMAIN. An empty value disables
 * host-based tenancy and everything falls back to the path form, which is the
 * correct behaviour for a build that has not been told its domain.
 */
export const PUBLIC_BASE_DOMAIN: string = readBaseDomain();

function readBaseDomain(): string {
  // `import.meta.env` only exists under Vite. This module is also imported by
  // `tenant.test.ts`, which runs under plain node via tsx, so reading it
  // unguarded throws at import time and takes the whole test file with it.
  const env = (import.meta as ImportMeta & { env?: Record<string, string> }).env;
  return env?.VITE_PUBLIC_BASE_DOMAIN || '';
}

/** Labels that are never a tenant. Must match Slugs.RESERVED_LABELS on the backend. */
const RESERVED_LABELS = new Set([
  'admin', 'api', 'app', 'www', 'static', 'assets', 'ws', 'mail', 'status',
]);

const LABEL_PATTERN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/;

/**
 * The tenant label in a hostname, or null if there is none.
 *
 * Null is the ordinary answer for localhost, an IP, the apex, a reserved label
 * and a multi-level label — all of which must fall back to the path form rather
 * than error.
 */
export function tenantSlugFromHost(
  hostname: string,
  baseDomain: string = PUBLIC_BASE_DOMAIN,
): string | null {
  if (!hostname || !baseDomain) return null;

  const host = stripPort(hostname.trim().toLowerCase());
  const base = stripPort(baseDomain.trim().toLowerCase()).replace(/^\./, '');
  if (!host || !base) return null;

  const suffix = `.${base}`;
  if (!host.endsWith(suffix)) return null;

  const label = host.slice(0, -suffix.length);
  // A dot means a multi-level label, which a single-label wildcard certificate
  // cannot serve, so treating the first segment as the tenant would be a guess.
  if (!label || label.includes('.')) return null;
  if (!LABEL_PATTERN.test(label) || RESERVED_LABELS.has(label)) return null;

  return label;
}

function stripPort(value: string): string {
  if (value.startsWith('[')) return '';
  const colon = value.lastIndexOf(':');
  return colon >= 0 ? value.slice(0, colon) : value;
}

/** The tenant for the page currently being served, if any. */
export function currentTenantSlug(): string | null {
  if (typeof window === 'undefined') return null;
  return tenantSlugFromHost(window.location.hostname);
}

export type MenuRouteParams = {
  merchantSlug?: string;
  branchSlug?: string;
  tableNumber?: string;
};

export type MenuTarget = {
  merchantSlug: string;
  branchSlug: string;
  tableNumber: string;
  /**
   * True when the URL named a merchant that is not the one in the address bar.
   * The host wins for the actual request, but the page should say so rather than
   * quietly serve a different restaurant than the link promised.
   */
  hostMismatch: boolean;
};

/** Default branch for the legacy two-segment path form, which omits it. */
const DEFAULT_BRANCH_SLUG = 'main';

/**
 * Turns the router's params plus the host label into the merchant, branch and
 * table to resolve.
 */
export function resolveMenuTarget(
  params: MenuRouteParams,
  hostSlug: string | null,
): MenuTarget | null {
  // Read the params positionally, because the route parameter NAMES lie on a
  // tenant host: React Router matches /menu/main/1 against
  // /menu/:merchantSlug/:tableNumber and binds merchantSlug="main", which on
  // sunrise.qrserve.safaricom.et is the branch, not the merchant. `branchSlug` is
  // bound only by the three-segment route, so its presence is what distinguishes
  // the two URL shapes.
  const { merchantSlug: first, branchSlug: second, tableNumber: third } = params;

  if (hostSlug) {
    if (second !== undefined) {
      // Three-segment route: the path also names a merchant, so check it agrees.
      if (!third) return null;
      return {
        merchantSlug: hostSlug,
        branchSlug: second || DEFAULT_BRANCH_SLUG,
        tableNumber: third,
        hostMismatch: Boolean(first) && first !== hostSlug,
      };
    }
    // Two-segment route: (first, third) is really (branchSlug, tableNumber).
    if (!third) return null;
    return {
      merchantSlug: hostSlug,
      branchSlug: first || DEFAULT_BRANCH_SLUG,
      tableNumber: third,
      hostMismatch: false,
    };
  }

  // No tenant host: the path is authoritative, exactly as before this change.
  if (!first || !third) return null;
  return {
    merchantSlug: first,
    branchSlug: second || DEFAULT_BRANCH_SLUG,
    tableNumber: third,
    hostMismatch: false,
  };
}
