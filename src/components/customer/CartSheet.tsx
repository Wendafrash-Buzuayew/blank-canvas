import React from 'react';
import { X, Minus, Plus, Trash2, ShoppingBag, Loader2, AlertTriangle } from 'lucide-react';

export interface CartLine {
  productId: number;
  name: string;
  price: number;
  quantity: number;
}

interface Props {
  open: boolean;
  onClose: () => void;
  lines: CartLine[];
  currency: string;
  onChangeQty: (productId: number, delta: number) => void;
  onRemove: (productId: number) => void;
  onConfirm: () => void;
  submitting: boolean;
  errorMessage?: string | null;
  tableNumber: string;
}

export const CartSheet: React.FC<Props> = ({
  open,
  onClose,
  lines,
  currency,
  onChangeQty,
  onRemove,
  onConfirm,
  submitting,
  errorMessage,
  tableNumber,
}) => {
  if (!open) return null;
  const total = lines.reduce((s, l) => s + l.price * l.quantity, 0);
  const count = lines.reduce((s, l) => s + l.quantity, 0);

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
      <button
        aria-label="Close cart"
        onClick={onClose}
        className="absolute inset-0 bg-ink/50 backdrop-blur-[2px]"
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Review your order"
        className="relative w-full max-w-lg animate-slide-up rounded-t-3xl bg-surface shadow-lift sm:animate-rise sm:rounded-3xl"
      >
        <div className="flex items-center justify-between border-b border-line px-5 py-4">
          <div>
            <h2 className="font-display text-lg font-extrabold">Review your order</h2>
            <p className="text-xs text-muted">Table {tableNumber} · {count} item{count === 1 ? '' : 's'}</p>
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            className="rounded-full p-2 text-muted transition-colors hover:bg-canvas hover:text-ink"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="max-h-[45vh] overflow-y-auto px-5 py-4">
          {lines.length === 0 ? (
            <div className="py-10 text-center">
              <ShoppingBag className="mx-auto h-10 w-10 stroke-[1.25] text-line" aria-hidden />
              <p className="mt-3 text-sm font-semibold">Your cart is empty</p>
              <p className="mt-1 text-xs text-muted">Add a dish from the menu to get started.</p>
            </div>
          ) : (
            <ul className="space-y-3">
              {lines.map((l) => (
                <li key={l.productId} className="flex items-center gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold">{l.name}</p>
                    <p className="text-xs text-muted">
                      {l.price.toLocaleString()} {currency} each
                    </p>
                  </div>
                  <div className="flex items-center gap-1 rounded-full border border-line p-1">
                    <button
                      onClick={() => (l.quantity === 1 ? onRemove(l.productId) : onChangeQty(l.productId, -1))}
                      aria-label={l.quantity === 1 ? `Remove ${l.name}` : `Decrease ${l.name}`}
                      className="flex h-8 w-8 items-center justify-center rounded-full text-ink transition-colors hover:bg-canvas"
                    >
                      {l.quantity === 1 ? <Trash2 className="h-4 w-4" /> : <Minus className="h-4 w-4" />}
                    </button>
                    <span className="w-6 text-center text-sm font-bold tabular-nums">{l.quantity}</span>
                    <button
                      onClick={() => onChangeQty(l.productId, 1)}
                      aria-label={`Increase ${l.name}`}
                      className="flex h-8 w-8 items-center justify-center rounded-full bg-ink text-white transition-transform active:scale-90"
                    >
                      <Plus className="h-4 w-4" />
                    </button>
                  </div>
                  <span className="w-20 text-right text-sm font-bold tabular-nums">
                    {(l.price * l.quantity).toLocaleString()}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        {errorMessage && (
          <div className="mx-5 mb-3 flex items-start gap-2 rounded-2xl bg-danger-soft px-3 py-2.5 text-xs text-danger">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
            <span>{errorMessage}</span>
          </div>
        )}

        <div className="safe-b border-t border-line px-5 pt-4">
          <div className="mb-3 flex items-baseline justify-between">
            <span className="text-sm text-muted">Total</span>
            <span className="font-display text-2xl font-extrabold tabular-nums">
              {total.toLocaleString()} <span className="text-sm font-semibold text-muted">{currency}</span>
            </span>
          </div>
          <button
            onClick={onConfirm}
            disabled={submitting || lines.length === 0}
            className="flex w-full items-center justify-center gap-2 rounded-2xl bg-brand py-4 text-base font-bold text-brand-fg shadow-lift transition-all active:scale-[0.99] disabled:opacity-50"
          >
            {submitting && <Loader2 className="h-5 w-5 animate-spin" aria-hidden />}
            {submitting ? 'Sending to the kitchen…' : 'Confirm order'}
          </button>
          <p className="mt-2 text-center text-[11px] text-muted">
            Pay at the table — your waiter will bring the bill when you ask.
          </p>
        </div>
      </div>
    </div>
  );
};
