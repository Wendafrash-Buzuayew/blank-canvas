import React, { useEffect, useMemo, useState } from 'react';
import { Bell, CheckCircle2, Clock, Radio, Table as TableIcon, RefreshCw } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState } from '../components/ui/States';
import { useAuth } from '../context/AuthContext';
import { useWaiterTasks, useResolveRequest, useBranches } from '../hooks/useApiData';
import { useWaiterStream } from '../hooks/useRealtime';
import { URGENCY_BADGE, URGENCY_CARD, byLongestWaiting, urgencyOf, waitedLabel } from '../lib/urgency';

const REQUEST_LABEL: Record<string, string> = {
  CALL_WAITER: 'Call waiter',
  REQUEST_WATER: 'Water requested',
  REQUEST_BILL: 'Bill requested',
};

const ConnectionBadge: React.FC<{ status: string }> = ({ status }) => {
  const map: Record<string, string> = {
    connected: 'bg-success-soft text-success border-success/30',
    connecting: 'bg-warn-soft text-ink border-warn/30',
    disconnected: 'bg-danger-soft text-danger border-danger/30',
    error: 'bg-danger-soft text-danger border-danger/30',
    idle: 'bg-canvas text-muted border-line',
  };
  // A dead socket means alerts stop arriving, so it is called out rather than
  // rendered as quiet grey text.
  const shout = status !== 'connected' && status !== 'idle';
  return (
    <span
      role="status"
      className={`inline-flex items-center gap-1.5 rounded-pill border px-3 py-1.5 text-xs font-bold uppercase tracking-wide ${map[status] || map.idle} ${
        shout ? 'animate-breathe' : ''
      }`}
    >
      <Radio className="h-3.5 w-3.5" aria-hidden="true" />
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

  // Urgency is a function of elapsed time, so it has to be recomputed even when
  // no data arrives — otherwise a card frozen at "fresh" never escalates.
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 15_000);
    return () => window.clearInterval(id);
  }, []);

  const pending = useMemo(
    // Oldest first. The previous newest-first order buried the guest who had been
    // waiting longest at the bottom of the list.
    () => (data?.pendingRequests || []).slice().sort(byLongestWaiting),
    [data]
  );
  const assigned = data?.assignedTables || [];
  const overdueCount = pending.filter((r) => urgencyOf(r.createdAt, now) === 'overdue').length;

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
                aria-label="Branch"
                value={effectiveBranchId ?? ''}
                onChange={(e) => setBranchId(Number(e.target.value))}
                className="rounded-xl border border-line bg-surface px-3 py-2 text-sm font-bold"
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
            className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-line bg-surface px-4 text-sm font-bold hover:bg-canvas"
          >
            <RefreshCw className={`h-4 w-4 ${isFetching ? 'animate-spin' : ''}`} aria-hidden="true" />
            Refresh
          </button>
        </div>

        {isLoading ? (
          <Spinner label="Loading waiter tasks..." />
        ) : error ? (
          <ErrorState message={`Failed to load waiter tasks: ${(error as Error).message}`} />
        ) : (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <section className="card-surface p-6 lg:col-span-2">
              <h3 className="mb-4 flex items-center gap-2 text-base font-bold">
                <Bell className="h-4 w-4 text-brand" aria-hidden="true" />
                Pending requests
                <span className="ml-1 rounded-pill bg-brand-soft px-2.5 py-0.5 text-xs font-black text-brand-dark tabular-nums">
                  {pending.length}
                </span>
                {overdueCount > 0 && (
                  <span className="rounded-pill bg-danger px-2.5 py-0.5 text-xs font-black text-white tabular-nums">
                    {overdueCount} overdue
                  </span>
                )}
              </h3>

              {pending.length === 0 ? (
                <p className="py-8 text-center text-sm text-muted">No pending customer requests.</p>
              ) : (
                <ul className="space-y-3">
                  {pending.map((req) => {
                    const tier = urgencyOf(req.createdAt, now);
                    return (
                      <li
                        key={req.id}
                        className={`flex flex-wrap items-center justify-between gap-3 rounded-card border p-4 ${URGENCY_CARD[tier]}`}
                      >
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="text-base font-bold">
                              {REQUEST_LABEL[req.requestType] || req.requestType}
                            </span>
                            <span
                              className={`rounded-pill px-2 py-0.5 text-[11px] font-black uppercase tracking-wide tabular-nums ${URGENCY_BADGE[tier]}`}
                            >
                              {waitedLabel(req.createdAt, now)}
                            </span>
                          </div>
                          <div className="mt-1 flex items-center gap-2 text-sm text-muted">
                            <TableIcon className="h-3.5 w-3.5" aria-hidden="true" />
                            <span className="tabular-nums">Table {req.tableId}</span>
                            <Clock className="ml-2 h-3.5 w-3.5" aria-hidden="true" />
                            <span className="tabular-nums">
                              {new Date(req.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                            </span>
                          </div>
                          {req.note && <p className="mt-1 text-sm italic text-muted">“{req.note}”</p>}
                        </div>
                        {/* 56px targets: tapped fast, often with wet hands. */}
                        <div className="flex gap-2">
                          <button
                            disabled={resolveRequest.isPending}
                            onClick={() =>
                              resolveRequest.mutate({ requestId: req.id, status: 'ACKNOWLEDGED', merchantId })
                            }
                            className="min-h-14 rounded-xl border border-line bg-surface px-4 text-sm font-bold hover:bg-canvas disabled:opacity-50"
                          >
                            Acknowledge
                          </button>
                          <button
                            disabled={resolveRequest.isPending}
                            onClick={() =>
                              resolveRequest.mutate({ requestId: req.id, status: 'COMPLETED', merchantId })
                            }
                            className="inline-flex min-h-14 items-center gap-1.5 rounded-xl bg-success px-4 text-sm font-bold text-white hover:brightness-95 disabled:opacity-50"
                          >
                            <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                            Complete
                          </button>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              )}
            </section>

            <div className="space-y-6">
              <section className="card-surface p-6">
                <h3 className="mb-4 flex items-center gap-2 text-base font-bold">
                  <TableIcon className="h-4 w-4 text-brand" aria-hidden="true" />
                  My tables
                </h3>
                {assigned.length === 0 ? (
                  <p className="text-sm text-muted">No active table assignments.</p>
                ) : (
                  <div className="grid grid-cols-3 gap-2">
                    {assigned.map((a) => (
                      <div
                        key={a.assignmentId ?? `${a.tableId}`}
                        className="rounded-xl border border-line bg-canvas p-3 text-center"
                      >
                        <div className="text-lg font-black tabular-nums">{a.tableNumber || `#${a.tableId}`}</div>
                        <div className="text-[11px] font-bold uppercase tracking-wide text-muted">
                          {a.shift || 'ACTIVE'}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </section>

              <section className="card-surface p-6">
                <h3 className="mb-4 flex items-center gap-2 text-base font-bold">
                  <Radio className="h-4 w-4 text-success" aria-hidden="true" />
                  Live alerts
                </h3>
                {events.length === 0 ? (
                  <p className="text-sm text-muted">Waiting for real-time alerts…</p>
                ) : (
                  <ul className="max-h-72 space-y-2 overflow-y-auto">
                    {events.map((e) => (
                      <li key={e.id} className="rounded-xl border border-line bg-canvas p-2.5 text-sm">
                        <span className="font-black">{e.eventType}</span>
                        <span className="ml-2 text-muted tabular-nums">
                          {new Date(e.receivedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </span>
                        {e.payload?.message && <p className="mt-0.5 text-muted">{e.payload.message}</p>}
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
