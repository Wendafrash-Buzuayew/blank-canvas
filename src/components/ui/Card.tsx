import React from 'react';

/**
 * DESIGN.md §6.3. Standard card: card-surface (index.css) is the shape —
 * surface ground, line border, radius-card, shadow-card. Padding is 24 by
 * default, 20 inside the mini-app frame where horizontal space is 430px
 * total (pass `compact`).
 *
 * Interactive cards (a menu item, a table tile) must be a real button/a,
 * not a div with onClick — pass `as="button"`/`as="a"` plus `interactive`
 * for the hover lift; non-interactive cards get no hover state.
 */
export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  compact?: boolean;
  interactive?: boolean;
  as?: 'div' | 'button' | 'a';
  href?: string;
  target?: string;
  rel?: string;
  type?: string;
  disabled?: boolean;
}

export const Card: React.FC<CardProps> = ({
  compact = false,
  interactive = false,
  as = 'div',
  className = '',
  children,
  ...props
}) => {
  const Tag = as as any;
  return (
    <Tag
      className={[
        'card-surface',
        compact ? 'p-5' : 'p-6',
        interactive ? 'text-left transition-shadow hover:shadow-[var(--shadow-lift)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark' : '',
        className,
      ].join(' ')}
      {...props}
    >
      {children}
    </Tag>
  );
};
