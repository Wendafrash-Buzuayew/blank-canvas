import React, { useMemo, useState } from 'react';
import { ChefHat, Clock, Radio, RefreshCw } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState } from '../components/ui/States';
import { useAuth } from '../context/AuthContext';
import { useKitchenOrders, useUpdateOrderStatus, useBranches } from '../hooks/useApiData';
import { useKitchenStream } from '../hooks/useRealtime';

const COLUMNS: { key: string; label: string; next?: string; accent: string }[] = [
  { key: 'CREATED', label: 'Incoming', next: 'PREPARING', accent: 'border-blue-200 bg-blue-50' },
  { key: 'PREPARING', label: 'Preparing', next: 'READY', accent: 'border-amber-200 bg-amber-50' },
  { key: 'READY', label: 'Ready', next: 'SERVED', accent: 'border-emerald-200 bg-emerald-50' },
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

  const grouped = useMemo(() => {
    const map: Record<string, typeof orders> = {};
    COLUMNS.forEach((c) => (map[c.key] = []));
    (orders || []).forEach((o) => {
      if (map[o.status]) map[o.status]!.push(o);
    });
    return map;
  }, [orders]);

  return (
    <DashboardLayout title="Kitchen Display">
      <div className="space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-[10px] font-bold uppercase tracking-wider border-slate-200 bg-slate-50 text-slate-600">
              <Radio className="w-3 h-3" /> Live {status}
            </span>
            {branches && branches.length > 0 && (
              <select
                value={effectiveBranchId ?? ''}
                onChange={(e) => setBranchId(Number(e.target.value))}
                className="text-xs font-bold border border-slate-200 rounded-lg px-3 py-2 bg-white"
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
            className="inline-flex items-center gap-2 text-xs font-bold px-3 py-2 rounded-lg border border-slate-200 bg-white hover:bg-slate-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isFetching ? 'animate-spin' : ''}`} /> Refresh
          </button>
        </div>

        {isLoading ? (
          <Spinner label="Loading kitchen orders..." />
        ) : error ? (
          <ErrorState message={`Failed to load kitchen orders: ${(error as Error).message}`} />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {COLUMNS.map((col) => (
              <section key={col.key} className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4">
                <h3 className="flex items-center gap-2 text-xs font-black uppercase tracking-wider text-slate-500 mb-3">
                  <ChefHat className="w-4 h-4" />
                  {col.label}
                  <span className="ml-auto text-slate-900">{grouped[col.key]?.length || 0}</span>
                </h3>
                <div className="space-y-3">
                  {(grouped[col.key] || []).length === 0 && (
                    <p className="text-xs text-slate-400 text-center py-6">Nothing here.</p>
                  )}
                  {(grouped[col.key] || []).map((order) => (
                    <article key={order.id} className={`rounded-xl border p-3 ${col.accent}`}>
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-black text-slate-900">#{order.orderNumber}</span>
                        <span className="text-[10px] text-slate-500 flex items-center gap-1">
                          <Clock className="w-3 h-3" />
                          {new Date(order.createdAt).toLocaleTimeString()}
                        </span>
                      </div>
                      <div className="text-[11px] text-slate-600 mt-0.5">
                        Table {order.tableNumber || order.tableId}
                        {order.customerName ? ` · ${order.customerName}` : ''}
                      </div>
                      <ul className="mt-2 space-y-0.5">
                        {order.items?.map((it, i) => (
                          <li key={i} className="text-[11px] text-slate-700">
                            <span className="font-bold">{it.quantity}×</span> {it.productName}
                            {it.notes && <span className="text-slate-400 italic"> — {it.notes}</span>}
                          </li>
                        ))}
                      </ul>
                      {col.next && (
                        <button
                          disabled={updateStatus.isPending}
                          onClick={() => updateStatus.mutate({ id: order.id, status: col.next! })}
                          className="mt-3 w-full text-[11px] font-bold px-3 py-1.5 rounded-lg bg-slate-900 text-white hover:bg-slate-800 disabled:opacity-50"
                        >
                          Mark {col.next}
                        </button>
                      )}
                    </article>
                  ))}
                </div>
              </section>
            ))}
          </div>
        )}
      </div>
    </DashboardLayout>
  );
};
