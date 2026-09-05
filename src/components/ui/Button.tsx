import React from 'react';
import { Loader2 } from 'lucide-react';

/** DESIGN.md §6.1 — five variants, exhaustive. */
export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'destructive' | 'link';
export type ButtonSize = 'sm' | 'md' | 'lg' | 'kds';

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  // brand-dark, not the hero green: white on --color-brand is 2.88 (§6.1 note).
  primary: 'bg-brand-dark text-brand-fg hover:bg-brand-press',
  secondary: 'bg-surface border border-line-strong text-ink hover:bg-surface-2',
  ghost: 'bg-transparent text-ink-2 hover:bg-surface-2',
  destructive: 'bg-danger text-white hover:brightness-90',
  link: 'bg-transparent text-brand-press underline-offset-2 hover:underline p-0 h-auto min-h-0',
};

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: 'h-9 px-3 text-label-s',
  md: 'h-11 px-4 text-label-m',
  lg: 'h-14 px-6 text-label-m',
  kds: 'h-16 px-6 text-kds-action',
};

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  /** Icon-only buttons must supply aria-label (§6.1, §7.5) — enforced by the type, not just a convention. */
  'aria-label'?: string;
  fullWidth?: boolean;
}

/**
 * DESIGN.md §6.1. One implementation for all five variants so the
 * "never white on the hero green" rule is encoded once, not at every call
 * site. Every button defines resting/hover/focus-visible (global ring,
 * see index.css)/disabled/loading.
 */
export const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  size = 'md',
  loading = false,
  fullWidth = false,
  disabled,
  className = '',
  children,
  ...props
}) => {
  const isLink = variant === 'link';
  return (
    <button
      type={props.type ?? 'button'}
      disabled={disabled || loading}
      aria-disabled={disabled || loading}
      className={[
        'inline-flex items-center justify-center gap-2 rounded-control font-semibold transition-colors',
        'disabled:opacity-50 disabled:pointer-events-none',
        isLink ? '' : SIZE_CLASSES[size],
        VARIANT_CLASSES[variant],
        fullWidth ? 'w-full' : '',
        className,
      ].join(' ')}
      {...props}
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
      {children}
    </button>
  );
};
