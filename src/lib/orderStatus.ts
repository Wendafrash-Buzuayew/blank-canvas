/**
 * Order lifecycle states — the single frontend mirror of the backend contract
 * (`com.qrserve.shared.common.OrderStatus`).
 *
 * Status names were previously written as inline string literals at every call
 * site, and drifted from the backend in four separate places: the kitchen board's
 * "Incoming" column keyed on `CREATED` (never emitted, so always empty), its
 * action wrote `SERVED` (rejected), the waiter dashboard filtered on `CREATED`
 * (missed every new order), and the customer progress tracker had a `SERVED` step
 * that could never activate. Import from here so a stale name is a type error
 * instead of a silent no-op.
 */
export const ORDER_STATUS = {
  PENDING: 'PENDING',
  ACCEPTED: 'ACCEPTED',
  PREPARING: 'PREPARING',
  READY: 'READY',
  DELIVERED: 'DELIVERED',
  PAID: 'PAID',
  CANCELLED: 'CANCELLED',
} as const;

export type OrderStatus = (typeof ORDER_STATUS)[keyof typeof ORDER_STATUS];

/** Statuses a kitchen still has work to do on. */
export const KITCHEN_ACTIVE: readonly OrderStatus[] = [
  ORDER_STATUS.PENDING,
  ORDER_STATUS.ACCEPTED,
  ORDER_STATUS.PREPARING,
  ORDER_STATUS.READY,
];

/** Terminal states — no further transition is accepted by the backend. */
export const TERMINAL: readonly OrderStatus[] = [ORDER_STATUS.PAID, ORDER_STATUS.CANCELLED];

export function isTerminal(status: string | null | undefined): boolean {
  return !!status && (TERMINAL as readonly string[]).includes(status);
}

/** Table occupancy states — mirrors `com.qrserve.shared.common.TableStatus`. */
export const TABLE_STATUS = {
  AVAILABLE: 'AVAILABLE',
  OCCUPIED: 'OCCUPIED',
  RESERVED: 'RESERVED',
} as const;

export type TableStatus = (typeof TABLE_STATUS)[keyof typeof TABLE_STATUS];

/**
 * Lifecycle position of a status, used to pick the furthest-along of several
 * reports of the same order.
 *
 * <p>The customer tracker learns its status from two independent channels — a
 * WebSocket push and a REST poll — which can disagree for a few seconds in either
 * direction: a push lands before the poll refetches, or a poll answers with a
 * value the socket has already superseded. Taking the maximum rank means neither
 * channel can walk the guest's progress backwards. CANCELLED ranks last because
 * the backend accepts no transition out of it, so once seen it is the truth.
 */
const RANK: Record<string, number> = {
  [ORDER_STATUS.PENDING]: 0,
  [ORDER_STATUS.ACCEPTED]: 1,
  [ORDER_STATUS.PREPARING]: 2,
  [ORDER_STATUS.READY]: 3,
  [ORDER_STATUS.DELIVERED]: 4,
  [ORDER_STATUS.PAID]: 5,
  [ORDER_STATUS.CANCELLED]: 6,
};

/** @returns the lifecycle position, or -1 for an unknown or absent status. */
export function statusRank(status: string | null | undefined): number {
  if (!status) return -1;
  const rank = RANK[status.toUpperCase()];
  return rank === undefined ? -1 : rank;
}

/**
 * The furthest-along of the given statuses, or null if none is recognised.
 *
 * Unknown values are ignored rather than preferred: a status name this build does
 * not know about must not outrank one it does.
 */
export function mostAdvancedStatus(...statuses: (string | null | undefined)[]): string | null {
  let best: string | null = null;
  let bestRank = -1;
  for (const status of statuses) {
    const rank = statusRank(status);
    if (rank > bestRank) {
      bestRank = rank;
      best = status!.toUpperCase();
    }
  }
  return best;
}
