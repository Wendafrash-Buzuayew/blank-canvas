import React, { useState } from 'react';
import { Bell, Droplets, Receipt, HandPlatter, X, Check, Loader2 } from 'lucide-react';
import type { WaiterRequestType } from '../../lib/api';

const ACTIONS: { type: WaiterRequestType; label: string; icon: React.ElementType }[] = [
  { type: 'CALL_WAITER', label: 'Call waiter', icon: Bell },
  { type: 'REQUEST_WATER', label: 'Water', icon: Droplets },
  { type: 'REQUEST_BILL', label: 'Bill', icon: Receipt },
];

interface Props {
  onRequest: (type: WaiterRequestType) => void;
  pending: boolean;
  sentType: WaiterRequestType | null;
  failed?: boolean;
  offsetClass?: string;
}

/**
 * Persistent, unobtrusive service dock: a single small FAB that expands into
 * the three service requests. Reachable without scrolling, never competing
 * with the primary "browse and order" task.
 */
export const ServiceDock: React.FC<Props> = ({ onRequest, pending, sentType, failed, offsetClass = 'bottom-24' }) => {
  const [open, setOpen] = useState(false);

  return (
    <div className={`fixed left-4 z-40 ${offsetClass} flex flex-col items-start gap-2`}>
      {open &&
        ACTIONS.map(({ type, label, icon: Icon }) => (
          <button
            key={type}
            onClick={() => {
              onRequest(type);
              setOpen(false);
            }}
            disabled={pending}
            className="animate-rise flex items-center gap-2 rounded-full bg-surface py-2.5 pl-3 pr-4 text-sm font-semibold shadow-card ring-1 ring-line disabled:opacity-60"
          >
            <Icon className="h-4 w-4 text-brand" aria-hidden />
            {label}
          </button>
        ))}

      <button
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-label={open ? 'Close service requests' : 'Request service'}
        className={`flex h-12 w-12 items-center justify-center rounded-full shadow-lift transition-transform active:scale-90 ${
          open ? 'bg-ink text-white' : 'bg-surface text-ink ring-1 ring-line'
        }`}
      >
        {pending ? (
          <Loader2 className="h-5 w-5 animate-spin" aria-hidden />
        ) : open ? (
          <X className="h-5 w-5" aria-hidden />
        ) : (
          <HandPlatter className="h-5 w-5" aria-hidden />
        )}
      </button>

      {sentType && !open && (
        <div
          role="status"
          className="animate-rise flex items-center gap-1.5 rounded-full bg-success-soft px-3 py-1.5 text-[11px] font-semibold text-success"
        >
          <Check className="h-3.5 w-3.5" aria-hidden />
          Waiter notified
        </div>
      )}
      {failed && !open && (
        <div role="status" className="rounded-full bg-danger-soft px-3 py-1.5 text-[11px] font-semibold text-danger">
          Couldn’t send — tap to retry
        </div>
      )}
    </div>
  );
};
