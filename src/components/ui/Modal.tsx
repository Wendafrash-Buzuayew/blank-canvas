import React, { useEffect, useRef } from 'react';
import { X } from 'lucide-react';

/**
 * DESIGN.md §6.4. One implementation, three presentations. All three:
 * scrim at z-30, Escape closes, focus moves in on open and returns to the
 * trigger on close, focus trapped while open, background scroll locked,
 * role="dialog" + aria-modal + aria-labelledby, a visible ≥44px close
 * control, content scrolls inside (max 90vh).
 */
export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  /** dialog: centred, console. sheet: bottom, customer/mini-app. drawer: side (the Sidebar owns its own). */
  presentation?: 'dialog' | 'sheet';
  footer?: React.ReactNode;
}

export const Modal: React.FC<ModalProps> = ({ open, onClose, title, children, presentation = 'dialog', footer }) => {
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const triggerRef = useRef<Element | null>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = `modal-title-${title.replace(/\s+/g, '-').toLowerCase()}`;

  useEffect(() => {
    if (!open) return;
    triggerRef.current = document.activeElement;
    closeButtonRef.current?.focus();

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
        return;
      }
      if (event.key !== 'Tab') return;
      const focusables = panelRef.current?.querySelectorAll<HTMLElement>(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
      );
      if (!focusables || focusables.length === 0) return;
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
      (triggerRef.current as HTMLElement | null)?.focus?.();
    };
  }, [open, onClose]);

  if (!open) return null;

  const isSheet = presentation === 'sheet';

  return (
    <div className="fixed inset-0 z-modal flex items-end justify-center sm:items-center">
      <div className="fixed inset-0 z-scrim bg-ink/40" onClick={onClose} aria-hidden="true" />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className={[
          'relative z-modal flex max-h-[90vh] w-full flex-col bg-surface shadow-[var(--shadow-lift)]',
          isSheet
            ? 'rounded-t-card sm:max-w-md sm:rounded-card'
            : 'max-w-[35rem] rounded-card m-4',
        ].join(' ')}
      >
        <div className="flex items-start justify-between border-b border-line px-4 py-4">
          <h2 id={titleId} className="text-title-s text-ink">
            {title}
          </h2>
          <button
            ref={closeButtonRef}
            onClick={onClose}
            aria-label="Close"
            className="flex h-11 w-11 items-center justify-center rounded-control text-muted transition-colors hover:bg-surface-2 hover:text-ink"
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
        <div className="overflow-y-auto p-4">{children}</div>
        {footer && <div className="border-t border-line px-4 py-3">{footer}</div>}
      </div>
    </div>
  );
};
