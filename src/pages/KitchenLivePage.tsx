import React, { useMemo, useState } from 'react';
import { Clock, Radio, RefreshCw, Bell, CheckCircle2 } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState } from '../components/ui/States';
import { useAuth } from '../context/AuthContext';
import { useKitchenOrders, useUpdateOrderStatus, useBranches, useWaiterTasks, useResolveRequest } from '../hooks/useApiData';
import { useKitchenStream } from '../hooks/useRealtime';

/**
 * Kitchen Display System.
 *
 * Designed for a wall screen read from 1.5–3m, often without touch:
 *  - dark ground (--color-kds-bg) so a bright panel is not glaring in a kitchen
 *  - state is carried by the whole ticket's FILL, because fill resolves before
 *    text at distance; the label is confirmation, not the signal
 *  - order numbers at 40px+ with tabular figures so digits do not reflow
 *  - body text at 24px, action targets at 64px
 *
 * Status values match the backend exactly: orders are created PENDING (not
 * "CREATED") and the terminal serve state is DELIVERED (not "SERVED"). The
 * previous keys meant the Incoming column was always empty and "Mark SERVED"
 * wrote a status the backend does not recognise.
 */
const COLUMNS: {
  key: string;
  /** Extra statuses that belong in this column. */
  also?: string[];
  label: string;
  next?: string;
  nextLabel?: string;
  fill: string;
}[] = [
  { key: 'PENDING', also: ['ACCEPTED'], label: 'Incoming', next: 'PREPARING', nextLabel: 'Start', fill: 'kds-state-new' },
  { key: 'PREPARING', label: 'Preparing', next: 'READY', nextLabel: 'Ready', fill: 'kds-state-prep' },
  { key: 'READY', label: 'Ready', next: 'DELIVERED', nextLabel: 'Served', fill: 'kds-state-ready' },
];

export const KitchenLivePage: React.FC = () => {
  const { user } = useAuth();
  const merchantId = user?.merchantId ?? null;
  const [branchId, setBranchId] = useState<number | null>(user?.branchId ?? null);

  const { data: branches } = useBranches(merchantId ?? undefined);
  const effectiveBranchId = branchId ?? branches?.[0]?.id ?? null;

  const { data: orders, isLoading, error, refetch, isFetching } = useKitchenOrders({
    merchantId: merchantId ?? undefined,
    branchId: effectiveBranchId ?? undefined,
  });

  const { status } = useKitchenStream(merchantId, effectiveBranchId);
  const updateStatus = useUpdateOrderStatus();

  const { data: waiterTasks } = useWaiterTasks({
    merchantId,
    branchId: effectiveBranchId,
    refetchInterval: 15000,
  });
  const resolveRequest = useResolveRequest();

  const pendingRequests = useMemo(
    () => (waiterTasks?.pendingRequests || []).slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1)),
    [waiterTasks]
  );

  const grouped = useMemo(() => {
    const map: Record<string, typeof orders> = {};
    COLUMNS.forEach((c) => (map[c.key] = []));
    (orders || []).forEach((o) => {
      const col = COLUMNS.find((c) => c.key === o.status || c.also?.includes(o.status));
      if (col) map[col.key]!.push(o);
    });
    return map;
  }, [orders]);

  const live = status === 'connected';

  return (
    <DashboardLayout title="Kitchen Display">
      {/* Full-bleed dark register. The rest of the console is light; the kitchen
          screen deliberately is not. */}
      <div className="-mx-4 -my-4 min-h-screen bg-kds-bg p-5 text-white sm:-mx-6 sm:p-6">
        <div className="space-y-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <span
                className={`inline-flex items-center gap-2 rounded-full px-4 py-2 text-base font-bold uppercase tracking-wide ${
                  live ? 'bg-kds-ready text-kds-fg' : 'bg-kds-new text-kds-fg animate-breathe'
                }`}
              >
                <Radio className="h-4 w-4" aria-hidden="true" />
                {live ? 'Live' : status}
              </span>
              {branches && branches.length > 0 && (
                <select
                  aria-label="Branch"
                  value={effectiveBranchId ?? ''}
                  onChange={(e) => setBranchId(Number(e.target.value))}
                  className="rounded-xl border border-kds-line bg-kds-panel px-4 py-2 text-base font-bold text-white"
                >
                  {branches.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.name}
                    </option>
                  ))}
                </select>
              )}
            </div>
            <button
              onClick={() => refetch()}
              className="inline-flex min-h-12 items-center gap-2 rounded-xl border border-kds-line bg-kds-panel px-4 text-base font-bold text-white hover:brightness-125"
            >
              <RefreshCw className={`h-4 w-4 ${isFetching ? 'animate-spin' : ''}`} aria-hidden="true" /> Refresh
            </button>
          </div>

          {isLoading ? (
            <Spinner label="Loading kitchen orders..." />
          ) : error ? (
            <ErrorState message={`Failed to load kitchen orders: ${(error as Error).message}`} />
          ) : (
            <>
              {pendingRequests.length > 0 && (
                <section className="rounded-card border border-kds-line bg-kds-panel p-4">
                  <h3 className="mb-3 flex items-center gap-2 text-lg font-black uppercase tracking-wide text-kds-prep">
                    <Bell className="h-5 w-5" aria-hidden="true" /> Customer calls
                    <span className="ml-auto rounded-full bg-kds-prep px-3 py-0.5 text-base font-black text-kds-fg">
                      {pendingRequests.length}
                    </span>
                  </h3>
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                    {pendingRequests.map((req) => (
                      <div key={req.id} className="rounded-xl border border-kds-line bg-kds-bg p-4">
                        <div className="flex items-baseline justify-between gap-2">
                          <span className="text-xl font-black">
                            {req.requestType === 'CALL_WAITER'
                              ? 'Call waiter'
                              : req.requestType === 'REQUEST_WATER'
                                ? 'Water'
                                : 'Bill'}
                          </span>
                          <span className="text-base text-white/60 tabular-nums">
                            {new Date(req.createdAt).toLocaleTimeString()}
                          </span>
                        </div>
                        <div className="mt-1 text-lg text-white/80 tabular-nums">Table {req.tableId}</div>
                        <button
                          onClick={() =>
                            resolveRequest.mutate({ requestId: req.id, status: 'COMPLETED', merchantId: merchantId ?? undefined })
                          }
                          disabled={resolveRequest.isPending}
                          className="mt-3 min-h-16 w-full rounded-xl bg-kds-ready text-lg font-black text-kds-fg disabled:opacity-50"
                        >
                          <CheckCircle2 className="mr-2 inline h-5 w-5" aria-hidden="true" /> Done
                        </button>
                      </div>
                    ))}
                  </div>
                </section>
              )}

              <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                {COLUMNS.map((col) => {
                  const tickets = grouped[col.key] || [];
                  return (
                    <section key={col.key} className="rounded-card border border-kds-line bg-kds-panel p-4">
                      <h3 className="mb-3 flex items-center gap-2 text-xl font-black uppercase tracking-wide text-white/70">
                        {col.label}
                        <span className="ml-auto text-2xl text-white tabular-nums">{tickets.length}</span>
                      </h3>
                      <div className="space-y-3">
                        {tickets.length === 0 && (
                          <p className="py-8 text-center text-lg text-white/40">Nothing here.</p>
                        )}
                        {tickets.map((order) => (
                          <article key={order.id} className={`kds-ticket ${col.fill} p-4`}>
                            <div className="flex items-baseline justify-between gap-2">
                              <span className="text-4xl font-black leading-none tabular-nums">
                                {order.tableNumber || order.tableId}
                              </span>
                              <span className="flex items-center gap-1 text-base font-bold tabular-nums">
                                <Clock className="h-4 w-4" aria-hidden="true" />
                                {new Date(order.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                              </span>
                            </div>
                            <div className="mt-1 text-base font-semibold opacity-80 tabular-nums">
                              #{order.orderNumber}
                              {order.customerName ? ` · ${order.customerName}` : ''}
                            </div>
                            <ul className="mt-3 space-y-1">
                              {order.items?.map((it, i) => (
                                <li key={i} className="text-2xl font-bold leading-tight">
                                  <span className="tabular-nums">{it.quantity}×</span> {it.productName}
                                  {it.notes && <span className="block text-lg font-medium italic opacity-70">{it.notes}</span>}
                                </li>
                              ))}
                            </ul>
                            {col.next && (
                              <button
                                disabled={updateStatus.isPending}
                                onClick={() => updateStatus.mutate({ id: order.id, status: col.next! })}
                                className="mt-4 min-h-16 w-full rounded-xl bg-kds-fg text-xl font-black text-white disabled:opacity-50"
                              >
                                {col.nextLabel ?? col.next}
                              </button>
                            )}
                          </article>
                        ))}
                      </div>
                    </section>
                  );
                })}
              </div>
            </>
          )}
        </div>
      </div>
    </DashboardLayout>
  );
};
