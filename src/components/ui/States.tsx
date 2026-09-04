import React from 'react';
import { Loader2, AlertTriangle, Inbox, RefreshCw } from 'lucide-react';

/**
 * The three data states every data-backed view owes the user — DESIGN.md §6.7.
 *
 * Note on consumers: only Spinner, ErrorState and EmptyState are wired up
 * today. Skeleton, SkeletonCards, TableLoading and ButtonSpinner have no call
 * sites yet, and are kept because §6.7 makes skeletons the specified loading
 * pattern for a view with a known shape — a bare centred spinner is the
 * fallback, not the default. They get used as each page migrates.
 */

// ============ Loading States ============

export const Spinner: React.FC<{ label?: string; className?: string }> = ({ label, className = '' }) => (
  // role="status" so a screen reader is told the view is busy rather than
  // finding an empty region (§7.10).
  <div
    role="status"
    aria-live="polite"
    className={`flex flex-col items-center justify-center py-12 text-muted ${className}`}
  >
    {/* brand-dark, not the hero green: a spinner is a meaningful graphic and
        needs 3.0 non-text, which the hero green misses at 2.88 on white. */}
    <Loader2 className="h-8 w-8 animate-spin text-brand-dark" aria-hidden="true" />
    {label && <p className="mt-3 text-body-m text-muted">{label}</p>}
    {!label && <span className="sr-only">Loading</span>}
  </div>
);

export const Skeleton: React.FC<{ className?: string }> = ({ className = '' }) => (
  <div className={`animate-pulse rounded-control bg-line/70 ${className}`} aria-hidden="true" />
);

/**
 * Static column classes, because Tailwind resolves class names at build time
 * from source text. The previous implementation built `lg:grid-cols-${cols}`
 * by interpolation, which the compiler cannot see — so the class was never
 * generated and the `cols` prop silently did nothing.
 */
const SKELETON_COLS: Record<number, string> = {
  1: 'lg:grid-cols-1',
  2: 'lg:grid-cols-2',
  3: 'lg:grid-cols-3',
  4: 'lg:grid-cols-4',
  5: 'lg:grid-cols-5',
  6: 'lg:grid-cols-6',
};

export const SkeletonCards: React.FC<{ count?: number; cols?: number }> = ({ count = 4, cols = 4 }) => (
  <div
    className={`grid grid-cols-1 gap-6 sm:grid-cols-2 ${SKELETON_COLS[cols] ?? SKELETON_COLS[4]}`}
    aria-hidden="true"
  >
    {Array.from({ length: count }).map((_, i) => (
      // Skeletons match the destination card's real dimensions so the layout
      // does not jump when data lands.
      <div key={i} className="card-surface space-y-3 p-5">
        <Skeleton className="h-3 w-24" />
        <Skeleton className="h-7 w-32" />
        <Skeleton className="h-3 w-20" />
      </div>
    ))}
  </div>
);

export const TableLoading: React.FC<{ rows?: number }> = ({ rows = 4 }) => (
  <div className="space-y-3" aria-hidden="true">
    {Array.from({ length: rows }).map((_, i) => (
      <div
        key={i}
        className="flex min-h-12 items-center gap-4 rounded-surface-sm border border-line bg-surface p-3"
      >
        <Skeleton className="h-10 w-10" />
        <div className="flex-1 space-y-2">
          <Skeleton className="h-3 w-1/3" />
          <Skeleton className="h-3 w-1/2" />
        </div>
      </div>
    ))}
  </div>
);

// ============ Error States ============

export const ErrorState: React.FC<{
  message?: string;
  onRetry?: () => void;
  status?: number;
  /**
   * Technical detail — an exception message, a correlation id. Rendered in a
   * collapsed disclosure, never in the headline: §6.7 requires the visible
   * copy to be plain language and a raw exception is not that.
   */
  details?: string;
}> = ({ message = 'Something went wrong. Please try again.', onRetry, status, details }) => (
  <div
    role="alert"
    className="flex flex-col items-center justify-center rounded-card bg-danger-soft px-4 py-12 text-center"
  >
    {/* danger-soft ground with ink text measures 15.68; the icon carries the
        colour so meaning is not colour-only (§7.3). */}
    <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-pill bg-surface">
      <AlertTriangle className="h-6 w-6 text-danger" aria-hidden="true" />
    </div>
    {status && <span className="mb-1 text-label-s uppercase text-danger">Error {status}</span>}
    <p className="max-w-sm text-body-m text-ink">{message}</p>
    {details && (
      <details className="mt-3 max-w-sm text-left">
        <summary className="cursor-pointer text-label-s uppercase text-danger">Details</summary>
        <p className="mt-1 break-words text-body-m text-ink-2">{details}</p>
      </details>
    )}
    {onRetry && (
      <button
        onClick={onRetry}
        className="mt-4 flex min-h-11 items-center gap-2 rounded-control bg-brand-dark px-4 text-label-m text-brand-fg transition-colors hover:bg-brand-press"
      >
        <RefreshCw className="h-4 w-4" aria-hidden="true" />
        Try again
      </button>
    )}
  </div>
);

// ============ Empty States ============

export const EmptyState: React.FC<{
  title: string;
  description?: string;
  action?: React.ReactNode;
}> = ({ title, description, action }) => (
  <div className="flex flex-col items-center justify-center px-4 py-12 text-center">
    <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-pill border border-line bg-surface-2">
      <Inbox className="h-6 w-6 text-muted" aria-hidden="true" />
    </div>
    <p className="text-title-s text-ink">{title}</p>
    {/* muted is safe here on the Safaricom ramp: #5F6368 measures 5.59 on
        --color-canvas. The previous ramp's muted was 4.47 and this line had to
        fall back to ink-2 (§3.2). */}
    {description && <p className="mt-1 max-w-sm text-body-m text-muted">{description}</p>}
    {action && <div className="mt-4">{action}</div>}
  </div>
);

// ============ Button Loading ============

export const ButtonSpinner: React.FC = () => (
  <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
);
