import React from 'react';
import { Minus, Plus, Trash2, ShoppingBag, AlertTriangle } from 'lucide-react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';

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
  const total = lines.reduce((s, l) => s + l.price * l.quantity, 0);
  const count = lines.reduce((s, l) => s + l.quantity, 0);

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Review your order"
      presentation="sheet"
      footer={
        <>
          {errorMessage && (
            <div className="mb-3 flex items-start gap-2 rounded-control bg-danger-soft px-3 py-2.5 text-label-s text-ink">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-danger" aria-hidden />
              <span>{errorMessage}</span>
            </div>
          )}
          <div className="mb-3 flex items-baseline justify-between">
            <span className="text-body-m text-muted">Total</span>
            <span className="font-display text-title-l [font-variant-numeric:tabular-nums]">
              {total.toLocaleString()} <span className="text-body-m text-muted">{currency}</span>
            </span>
          </div>
          <Button
            onClick={onConfirm}
            loading={submitting}
            disabled={lines.length === 0}
            fullWidth
            size="lg"
          >
            {submitting ? 'Sending to the kitchen…' : 'Confirm order'}
          </Button>
          <p className="mt-2 text-center text-label-s text-muted">
            Pay at the table — your waiter will bring the bill when you ask.
          </p>
        </>
      }
    >
      <p className="-mt-2 mb-3 text-label-s text-muted">
        Table {tableNumber} · {count} item{count === 1 ? '' : 's'}
      </p>

      {lines.length === 0 ? (
        <div className="py-10 text-center">
          <ShoppingBag className="mx-auto h-10 w-10 stroke-[1.25] text-line" aria-hidden />
          <p className="mt-3 text-label-m text-ink">Your cart is empty</p>
          <p className="mt-1 text-body-m text-muted">Add a dish from the menu to get started.</p>
        </div>
      ) : (
        <ul className="space-y-3">
          {lines.map((l) => (
            <li key={l.productId} className="flex items-center gap-3">
              <div className="min-w-0 flex-1">
                <p className="truncate text-label-m text-ink">{l.name}</p>
                <p className="text-label-s text-muted">
                  {l.price.toLocaleString()} {currency} each
                </p>
              </div>
              {/* 44x44 minimum in the customer context (DESIGN.md §5.7). */}
              <div className="flex items-center gap-1 rounded-pill border border-line p-1">
                <button
                  onClick={() => (l.quantity === 1 ? onRemove(l.productId) : onChangeQty(l.productId, -1))}
                  aria-label={l.quantity === 1 ? `Remove ${l.name}` : `Decrease ${l.name}`}
                  className="flex h-11 w-11 items-center justify-center rounded-pill text-ink transition-colors hover:bg-canvas"
                >
                  {l.quantity === 1 ? <Trash2 className="h-4 w-4" /> : <Minus className="h-4 w-4" />}
                </button>
                <span className="w-6 text-center text-label-m [font-variant-numeric:tabular-nums]">{l.quantity}</span>
                <button
                  onClick={() => onChangeQty(l.productId, 1)}
                  aria-label={`Increase ${l.name}`}
                  className="flex h-11 w-11 items-center justify-center rounded-pill bg-brand-dark text-brand-fg transition-transform active:scale-90"
                >
                  <Plus className="h-4 w-4" />
                </button>
              </div>
              <span className="w-20 text-right text-label-m [font-variant-numeric:tabular-nums]">
                {(l.price * l.quantity).toLocaleString()}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  );
};
