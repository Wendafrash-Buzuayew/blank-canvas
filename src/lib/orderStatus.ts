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
