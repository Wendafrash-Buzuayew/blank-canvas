import React from 'react';

/**
 * DESIGN.md §6.2 — two kinds, and conflating them is the mistake the
 * pre-migration code makes.
 */

/** What a thing IS (role, fulfilment type, branch, tenant). Always neutral. */
export const IdentityChip: React.FC<{ children: React.ReactNode; className?: string }> = ({
  children,
  className = '',
}) => <span className={`identity-chip ${className}`}>{children}</span>;

/** §3.7's sanctioned status → ground mapping. Extend here, not per call site. */
const STATUS_GROUNDS = {
  success: 'bg-success-soft',
  warn: 'bg-warn-soft',
  danger: 'bg-danger-soft',
  info: 'bg-info-soft',
  neutral: 'bg-surface-2',
} as const;

export type ChipStatus = keyof typeof STATUS_GROUNDS;

/** What state a thing is IN. Colour is never the only carrier — always pair with text (§7.3). */
export const StatusChip: React.FC<{ status: ChipStatus; children: React.ReactNode; className?: string }> = ({
  status,
  children,
  className = '',
}) => <span className={`status-chip ${STATUS_GROUNDS[status]} ${className}`}>{children}</span>;

/** §6.2 count badge — brand-dark fill, never the hero green (white on it is 2.88). */
export const CountBadge: React.FC<{ count: number; className?: string }> = ({ count, className = '' }) => (
  <span
    className={`inline-flex min-w-5 items-center justify-center rounded-pill bg-brand-dark px-1.5 text-label-s text-brand-fg [font-variant-numeric:tabular-nums] ${className}`}
  >
    {count > 99 ? '99+' : count}
  </span>
);
