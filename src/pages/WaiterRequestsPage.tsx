import React, { useMemo, useState } from 'react';
import { Bell, CheckCircle2, Clock, Radio, Table as TableIcon, RefreshCw } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState } from '../components/ui/States';
import { useAuth } from '../context/AuthContext';
import { useWaiterTasks, useResolveRequest, useBranches } from '../hooks/useApiData';
import { useWaiterStream } from '../hooks/useRealtime';

const REQUEST_LABEL: Record<string, string> = {
  CALL_WAITER: 'Call waiter',
  REQUEST_WATER: 'Water requested',
  REQUEST_BILL: 'Bill requested',
};

const ConnectionBadge: React.FC<{ status: string }> = ({ status }) => {
  const map: Record<string, string> = {
    connected: 'bg-emerald-100 text-emerald-700 border-emerald-200',
    connecting: 'bg-amber-100 text-amber-700 border-amber-200',
    disconnected: 'bg-slate-100 text-slate-600 border-slate-200',
    error: 'bg-red-100 text-red-700 border-red-200',
    idle: 'bg-slate-100 text-slate-500 border-slate-200',
  };
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-[10px] font-bold uppercase tracking-wider ${map[status] || map.idle}`}>
      <Radio className="w-3 h-3" />
      Live {status}
    </span>
  );
};

export const WaiterRequestsPage: React.FC = () => {
  const { user } = useAuth();
  const merchantId = user?.merchantId ?? null;
  const [branchId, setBranchId] = useState<number | null>(user?.branchId ?? null);

  const { data: branches } = useBranches(merchantId ?? undefined);

  const effectiveBranchId = branchId ?? branches?.[0]?.id ?? null;

  const { data, isLoading, error, refetch, isFetching } = useWaiterTasks({
    merchantId,
    branchId: effectiveBranchId,
    userId: user?.role === 'WAITER' ? user.id : undefined,
  });

  const { events, status } = useWaiterStream(merchantId, effectiveBranchId);
  const resolveRequest = useResolveRequest();

  const pending = useMemo(
    () => (data?.pendingRequests || []).slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1)),
    [data]
  );
  const assigned = data?.assignedTables || [];

  if (!merchantId) {
    return (
      <DashboardLayout title="Waiter Requests">
        <ErrorState message="Your account is not linked to a merchant, so waiter tasks cannot be loaded." />
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout title="Waiter Requests">
      <div className="space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <ConnectionBadge status={status} />
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
            <RefreshCw className={`w-3.5 h-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>

        {isLoading ? (
          <Spinner label="Loading waiter tasks..." />
        ) : error ? (
          <ErrorState message={`Failed to load waiter tasks: ${(error as Error).message}`} />
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Pending customer requests */}
            <section className="lg:col-span-2 bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
              <h3 className="flex items-center gap-2 text-sm font-bold text-slate-900 mb-4">
                <Bell className="w-4 h-4 text-[#E60028]" />
                Pending Requests
                <span className="ml-1 px-2 py-0.5 rounded-full bg-red-50 text-red-600 text-[10px] font-black">
                  {pending.length}
                </span>
              </h3>

              {pending.length === 0 ? (
                <p className="text-xs text-slate-400 py-6 text-center">No pending customer requests.</p>
              ) : (
                <ul className="space-y-3">
                  {pending.map((req) => (
                    <li
                      key={req.id}
                      className="flex flex-wrap items-center justify-between gap-3 p-4 rounded-xl border border-slate-200 bg-slate-50"
                    >
                      <div>
                        <div className="text-sm font-bold text-slate-900">
                          {REQUEST_LABEL[req.requestType] || req.requestType}
                        </div>
                        <div className="text-[11px] text-slate-500 flex items-center gap-2 mt-0.5">
                          <TableIcon className="w-3 h-3" />
                          Table #{req.tableId}
                          <Clock className="w-3 h-3 ml-2" />
                          {new Date(req.createdAt).toLocaleTimeString()}
                        </div>
                        {req.note && <p className="text-[11px] text-slate-600 mt-1 italic">“{req.note}”</p>}
                      </div>
                      <div className="flex gap-2">
                        <button
                          disabled={resolveRequest.isPending}
                          onClick={() =>
                            resolveRequest.mutate({
                              requestId: req.id,
                              status: 'ACKNOWLEDGED',
                              merchantId,
                            })
                          }
                          className="text-[11px] font-bold px-3 py-1.5 rounded-lg border border-amber-200 bg-amber-50 text-amber-700 hover:bg-amber-100 disabled:opacity-50"
                        >
                          Acknowledge
                        </button>
                        <button
                          disabled={resolveRequest.isPending}
                          onClick={() =>
                            resolveRequest.mutate({
                              requestId: req.id,
                              status: 'COMPLETED',
                              merchantId,
                            })
                          }
                          className="inline-flex items-center gap-1 text-[11px] font-bold px-3 py-1.5 rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50"
                        >
                          <CheckCircle2 className="w-3.5 h-3.5" />
                          Complete
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {/* Assigned tables + live feed */}
            <div className="space-y-6">
              <section className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
                <h3 className="flex items-center gap-2 text-sm font-bold text-slate-900 mb-4">
                  <TableIcon className="w-4 h-4 text-indigo-500" />
                  My Tables
                </h3>
                {assigned.length === 0 ? (
                  <p className="text-xs text-slate-400">No active table assignments.</p>
                ) : (
                  <div className="grid grid-cols-3 gap-2">
                    {assigned.map((a) => (
                      <div
                        key={a.assignmentId ?? `${a.tableId}`}
                        className="p-3 rounded-xl border border-indigo-200 bg-indigo-50 text-center"
                      >
                        <div className="text-sm font-black text-slate-900">
                          {a.tableNumber || `#${a.tableId}`}
                        </div>
                        <div className="text-[10px] text-indigo-600 font-bold">{a.shift || 'ACTIVE'}</div>
                      </div>
                    ))}
                  </div>
                )}
              </section>

              <section className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
                <h3 className="flex items-center gap-2 text-sm font-bold text-slate-900 mb-4">
                  <Radio className="w-4 h-4 text-emerald-500" />
                  Live Alerts
                </h3>
                {events.length === 0 ? (
                  <p className="text-xs text-slate-400">Waiting for real-time alerts…</p>
                ) : (
                  <ul className="space-y-2 max-h-72 overflow-y-auto">
                    {events.map((e) => (
                      <li key={e.id} className="text-[11px] p-2 rounded-lg bg-slate-50 border border-slate-200">
                        <span className="font-black text-slate-700">{e.eventType}</span>
                        <span className="text-slate-400 ml-2">
                          {new Date(e.receivedAt).toLocaleTimeString()}
                        </span>
                        {e.payload?.message && (
                          <p className="text-slate-600 mt-0.5">{e.payload.message}</p>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </div>
          </div>
        )}
      </div>
    </DashboardLayout>
  );
};
