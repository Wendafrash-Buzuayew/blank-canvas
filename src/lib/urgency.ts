/**
 * Age-driven urgency for waiter triage.
 *
 * A request that has waited ten minutes and one that arrived ten seconds ago are
 * the same colour and the same size on an undifferentiated list, so the waiter
 * has to read every timestamp to work out what to do next. Turning wait time
 * into a visual tier is the whole job of a triage view.
 *
 * Thresholds are deliberately tight: in a restaurant, two minutes of being
 * ignored is already noticeable to a seated guest.
 */
export type UrgencyTier = 'fresh' | 'ageing' | 'overdue';

export const URGENCY_AGEING_MS = 2 * 60 * 1000;
export const URGENCY_OVERDUE_MS = 5 * 60 * 1000;

export function waitedMs(createdAt: string, now: number = Date.now()): number {
  const t = new Date(createdAt).getTime();
  // An unparseable or future timestamp must not read as "overdue"; treat it as new.
  if (Number.isNaN(t) || t > now) return 0;
  return now - t;
}

export function urgencyOf(createdAt: string, now: number = Date.now()): UrgencyTier {
  const ms = waitedMs(createdAt, now);
  if (ms >= URGENCY_OVERDUE_MS) return 'overdue';
  if (ms >= URGENCY_AGEING_MS) return 'ageing';
  return 'fresh';
}

/** "just now", "3m", "12m" — short enough to sit next to a table number. */
export function waitedLabel(createdAt: string, now: number = Date.now()): string {
  const ms = waitedMs(createdAt, now);
  const minutes = Math.floor(ms / 60000);
  if (minutes < 1) return 'just now';
  return `${minutes}m`;
}

/**
 * Card styling per tier. Uses the semantic tokens rather than raw Tailwind
 * colours so the waiter view stays in step with the rest of the design system —
 * and so `danger` here is the same red as everywhere else.
 */
export const URGENCY_CARD: Record<UrgencyTier, string> = {
  fresh: 'border-line bg-surface',
  ageing: 'border-warn/40 bg-warn-soft',
  // animate-flash draws the eye without moving layout; paired with a border so
  // the state survives forced-colors mode, where background tints are dropped.
  overdue: 'border-danger/50 bg-danger-soft animate-flash',
};

export const URGENCY_BADGE: Record<UrgencyTier, string> = {
  fresh: 'bg-line text-ink',
  ageing: 'bg-warn text-ink',
  overdue: 'bg-danger text-white',
};

/** Oldest first: whoever has waited longest is served next. */
export function byLongestWaiting<T extends { createdAt: string }>(a: T, b: T): number {
  return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
}
