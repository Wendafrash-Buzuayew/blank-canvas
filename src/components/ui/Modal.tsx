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
  /**
   * dialog: centred, console. sheet: bottom, customer/mini-app. drawer: side
   * (the Sidebar owns its own).
   *
   * fullscreen: near-viewport, for inspecting a rendered surface rather than
   * filling in a form — the admin template preview. It is a presentation of
   * THIS component rather than a separate overlay so it keeps the §6.4
   * obligations (Escape, focus trap, focus return, scroll lock) instead of
   * re-implementing and forgetting half of them.
   */
  presentation?: 'dialog' | 'sheet' | 'fullscreen';
  footer?: React.ReactNode;
  /** fullscreen only: replaces the body's default padding, so a preview can bleed to the panel edge. */
  bodyClassName?: string;
}

export const Modal: React.FC<ModalProps> = ({
  open,
  onClose,
  title,
  children,
  presentation = 'dialog',
  footer,
  bodyClassName,
}) => {
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const triggerRef = useRef<Element | null>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = `modal-title-${title.replace(/\s+/g, '-').toLowerCase()}`;

  // Callers routinely pass an inline onClose (`() => setOpen(false)`), a
  // fresh function on every render of the parent. A ref sidesteps that: the
  // effect below keys only on `open`, so typing into a form field inside the
  // modal (which re-renders the parent with a new onClose reference) can't
  // re-trigger it and steal focus back onto the close button mid-keystroke.
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!open) return;
    triggerRef.current = document.activeElement;
    closeButtonRef.current?.focus();

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onCloseRef.current();
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
  }, [open]);

  if (!open) return null;

  const isSheet = presentation === 'sheet';
  const isFullscreen = presentation === 'fullscreen';

  const panelClasses = isFullscreen
    ? 'max-h-[96dvh] h-[96dvh] max-w-[96rem] rounded-card m-2 sm:m-4'
    : isSheet
      ? 'max-h-[90dvh] rounded-t-card sm:max-w-md sm:rounded-card'
      : 'max-h-[90dvh] max-w-[35rem] rounded-card m-4';

  return (
    // z-50/z-30, not z-modal/z-scrim: DESIGN.md §5.6 documents the scale as
    // Tailwind's own numeric utilities ("never write an arbitrary z-index"),
    // but z-modal/z-scrim were never defined anywhere in the Tailwind theme
    // — they compiled to no CSS at all, leaving this wrapper at z-index:auto.
    // That let MobileBottomNav (z-40) and the sticky app header (z-20) — both
    // real, positive z-indexes — paint on top of every modal in the app,
    // dashboard chrome and all, whenever one was open over the mini-app shell.
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
      <div className="fixed inset-0 z-30 bg-ink/40" onClick={onClose} aria-hidden="true" />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className={`relative z-50 flex w-full flex-col bg-surface shadow-[var(--shadow-lift)] ${panelClasses}`}
      >
        <div className="flex items-start justify-between gap-2 border-b border-line px-4 py-4">
          <h2 id={titleId} className="min-w-0 truncate text-title-s text-ink">
            {title}
          </h2>
          <button
            ref={closeButtonRef}
            onClick={onClose}
            aria-label="Close"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-control text-muted transition-colors hover:bg-surface-2 hover:text-ink"
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
        <div className={bodyClassName ?? 'overflow-y-auto p-4'}>{children}</div>
        {footer && <div className="border-t border-line px-4 py-3">{footer}</div>}
      </div>
    </div>
  );
};
