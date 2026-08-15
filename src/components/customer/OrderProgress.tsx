import React from 'react';
import { Check, CookingPot, BellRing, ClipboardCheck, Wifi, WifiOff, Loader2 } from 'lucide-react';

const STEPS = [
  { key: 'RECEIVED', label: 'Received', icon: ClipboardCheck },
  { key: 'PREPARING', label: 'Cooking', icon: CookingPot },
  { key: 'READY', label: 'Ready', icon: BellRing },
  { key: 'SERVED', label: 'Served', icon: Check },
] as const;

/** Map any backend status string onto the 4-step guest-facing journey. */
function stepIndex(status?: string | null): number {
  const s = (status || '').toUpperCase();
  if (s.includes('SERVED') || s.includes('COMPLETE') || s.includes('PAID')) return 3;
  if (s.includes('READY')) return 2;
  if (s.includes('PREPAR') || s.includes('ACCEPT') || s.includes('COOK')) return 1;
  return 0;
}

interface Props {
  orderNumber?: string | null;
  status?: string | null;
  connection?: string;
}

export const OrderProgress: React.FC<Props> = ({ orderNumber, status, connection }) => {
  const active = stepIndex(status);
  const live = connection === 'connected';

  return (
    <section
      aria-label="Live order status"
      className="animate-rise rounded-3xl bg-ink text-white p-5 shadow-lift"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-white/50">
            Your order
          </p>
          <h2 className="font-display text-xl font-extrabold">
            #{orderNumber || '—'}
          </h2>
        </div>
        <span
          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-semibold ${
            live ? 'bg-success/20 text-white' : 'bg-white/10 text-white/70'
          }`}
          title={live ? 'Live updates connected' : 'Reconnecting to live updates'}
        >
          {live ? (
            <Wifi className="h-3.5 w-3.5 animate-breathe" aria-hidden />
          ) : connection === 'connecting' ? (
            <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden />
          ) : (
            <WifiOff className="h-3.5 w-3.5" aria-hidden />
          )}
          {live ? 'Live' : connection === 'connecting' ? 'Reconnecting' : 'Offline'}
        </span>
      </div>

      <ol className="mt-5 flex items-center">
        {STEPS.map((step, i) => {
          const done = i < active;
          const current = i === active;
          const Icon = step.icon;
          return (
            <li key={step.key} className="flex flex-1 items-center last:flex-none">
              <div className="flex flex-col items-center gap-1.5">
                <span
                  className={`relative flex h-9 w-9 items-center justify-center rounded-full transition-colors duration-500 ${
                    done
                      ? 'bg-success text-white'
                      : current
                        ? 'bg-brand text-white'
                        : 'bg-white/10 text-white/40'
                  }`}
                >
                  {current && (
                    <span className="absolute inset-0 animate-ping rounded-full bg-brand/40" aria-hidden />
                  )}
                  <Icon className="relative h-4 w-4" aria-hidden />
                </span>
                <span
                  className={`text-[10px] font-semibold ${
                    done || current ? 'text-white' : 'text-white/40'
                  }`}
                >
                  {step.label}
                </span>
              </div>
              {i < STEPS.length - 1 && (
                <span className="mx-1 h-0.5 flex-1 overflow-hidden rounded-full bg-white/10">
                  <span
                    className="block h-full rounded-full bg-success transition-all duration-700"
                    style={{ width: i < active ? '100%' : '0%' }}
                  />
                </span>
              )}
            </li>
          );
        })}
      </ol>

      <p className="mt-4 text-xs text-white/60">
        {active === 0 && 'Sent to the kitchen — hang tight, we’ll update this live.'}
        {active === 1 && 'The kitchen is cooking your order right now.'}
        {active === 2 && 'Ready! A waiter is bringing it over.'}
        {active === 3 && 'Served. Enjoy your meal — order again any time.'}
      </p>
    </section>
  );
};
