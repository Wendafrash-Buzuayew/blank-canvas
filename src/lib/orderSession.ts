/**
 * Persistence for the one order a seated guest is currently tracking.
 *
 * <h2>Why this exists</h2>
 * The tracker used to live entirely in React state, so a browser refresh — a
 * pull-to-refresh on a phone, a tab restore, a dropped connection — erased the
 * order id, the order number and the stream token. The guest lost the tracker
 * with no way back, even though the backend token stays valid for four hours
 * precisely so they can "watch an order through to PAID".
 *
 * <h2>Why localStorage rather than sessionStorage</h2>
 * sessionStorage dies when the tab closes, and a guest reopening the page from
 * their history mid-meal is exactly the case worth surviving. The record is
 * scoped to one merchant and table, and expires with the token, so a stale one
 * cannot resurface at a different table.
 *
 * <h2>What it holds</h2>
 * The stream token is a capability for one order and nothing else — it cannot
 * authenticate API calls (see JwtAuthenticationFilter). Storing it is the same
 * exposure as storing the order id it names.
 */

const STORAGE_KEY = 'qrserve.tracked-order.v1';

/**
 * Mirrors JwtTokenProvider.ORDER_STREAM_EXPIRATION_MS. Past this point the token
 * no longer authenticates, so a restored record would only produce 401s.
 */
export const TRACKING_TTL_MS = 4 * 60 * 60 * 1000;

export interface TrackedOrder {
  orderId: string;
  orderNumber: string;
  /** Last status we know of, so a refresh renders progress before the fetch lands. */
  status: string | null;
  /** Anonymous token scoped to this order; needed for both the socket and the poll. */
  streamToken: string | null;
  merchantId: string;
  tableId: number;
  /** Epoch ms, used for expiry. */
  savedAt: number;
}

export interface TrackedOrderScope {
  merchantId?: string | null;
  tableId?: number | null;
  now?: number;
}

/**
 * Validates a stored record against the table being viewed.
 *
 * Separate from the storage call so the rules — shape, expiry, scope — are
 * testable without a DOM.
 *
 * @returns the record, or null if it is malformed, expired, or belongs elsewhere
 */
export function parseTrackedOrder(raw: string | null, scope: TrackedOrderScope): TrackedOrder | null {
  if (!raw) return null;

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    // Someone else's key, a truncated write, or a format from an older build.
    return null;
  }

  if (!parsed || typeof parsed !== 'object') return null;
  const record = parsed as Partial<TrackedOrder>;

  if (typeof record.orderId !== 'string' || !record.orderId) return null;
  if (typeof record.merchantId !== 'string' || !record.merchantId) return null;
  if (typeof record.tableId !== 'number') return null;
  if (typeof record.savedAt !== 'number') return null;

  const now = scope.now ?? Date.now();
  // A clock that moved backwards must not resurrect an expired record either.
  if (now - record.savedAt >= TRACKING_TTL_MS || record.savedAt > now + 60_000) return null;

  // Scope: a guest who scans a different table gets that table's page, not the
  // previous table's order. Absent scope means the caller does not know yet.
  if (scope.merchantId != null && record.merchantId !== scope.merchantId) return null;
  if (scope.tableId != null && record.tableId !== scope.tableId) return null;

  return {
    orderId: record.orderId,
    orderNumber: typeof record.orderNumber === 'string' ? record.orderNumber : '',
    status: typeof record.status === 'string' ? record.status : null,
    streamToken: typeof record.streamToken === 'string' ? record.streamToken : null,
    merchantId: record.merchantId,
    tableId: record.tableId,
    savedAt: record.savedAt,
  };
}

/** localStorage is unavailable in Safari private mode and when cookies are blocked. */
function storage(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

export function readTrackedOrder(scope: TrackedOrderScope): TrackedOrder | null {
  const store = storage();
  if (!store) return null;
  try {
    return parseTrackedOrder(store.getItem(STORAGE_KEY), scope);
  } catch {
    return null;
  }
}

export function writeTrackedOrder(order: Omit<TrackedOrder, 'savedAt'> & { savedAt?: number }): void {
  const store = storage();
  if (!store) return;
  try {
    store.setItem(STORAGE_KEY, JSON.stringify({ ...order, savedAt: order.savedAt ?? Date.now() }));
  } catch {
    // A full or blocked quota must not break ordering; the tracker just will not
    // survive a refresh.
  }
}

export function clearTrackedOrder(): void {
  const store = storage();
  if (!store) return;
  try {
    store.removeItem(STORAGE_KEY);
  } catch {
    // Nothing to do — the record expires on its own.
  }
}
