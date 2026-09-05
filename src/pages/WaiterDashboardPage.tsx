import React, { useEffect, useMemo, useState } from 'react';
import { Bell, CheckCircle2, Clock, Radio, Table as TableIcon, RefreshCw, CookingPot, HandPlatter } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { StatusChip, CountBadge } from '../components/ui/Chip';
import { useAuth } from '../context/AuthContext';
import { useWaiterTasks, useResolveRequest, useBranches, useKitchenOrders } from '../hooks/useApiData';
import { useWaiterStream } from '../hooks/useRealtime';
import { ORDER_STATUS, type OrderStatus } from '../lib/orderStatus';
import { URGENCY_BADGE, URGENCY_CARD, byLongestWaiting, urgencyOf, waitedLabel } from '../lib/urgency';

const REQUEST_LABEL: Record<string, string> = {
  CALL_WAITER: 'Call waiter',
  REQUEST_WATER: 'Water requested',
  REQUEST_BILL: 'Bill requested',
};

const REQUEST_ICON: Record<string, React.ElementType> = {
  CALL_WAITER: Bell,
  REQUEST_WATER: HandPlatter,
  REQUEST_BILL: CheckCircle2,
};

/** DESIGN.md 3.7 order-status -> chip mapping, console register. */
const ORDER_STATUS_CHIP: Record<OrderStatus, { status: 'info' | 'warn' | 'success' | 'neutral' | 'danger'; label: string }> = {
  [ORDER_STATUS.PENDING]: { status: 'info', label: 'Received' },
  [ORDER_STATUS.ACCEPTED]: { status: 'info', label: 'Received' },
  [ORDER_STATUS.PREPARING]: { status: 'warn', label: 'Cooking' },
  [ORDER_STATUS.READY]: { status: 'success', label: 'Ready' },
  [ORDER_STATUS.DELIVERED]: { status: 'success', label: 'Served' },
  [ORDER_STATUS.PAID]: { status: 'neutral', label: 'Paid' },
  [ORDER_STATUS.CANCELLED]: { status: 'danger', label: 'Cancelled' },
};

const ConnectionBadge: React.FC<{ status: string }> = ({ status }) => {
  const map: Record<string, string> = {
    connected: 'bg-success-soft text-success border-success/30',
    connecting: 'bg-warn-soft text-ink border-warn/30',
    disconnected: 'bg-danger-soft text-danger border-danger/30',
    error: 'bg-danger-soft text-danger border-danger/30',
    idle: 'bg-canvas text-muted border-line',
  };
  return (
    <span role="status" className={`inline-flex items-center gap-1.5 rounded-pill border px-3 py-1.5 text-label-s uppercase tracking-wide ${map[status] || map.idle} ${
      status !== 'connected' && status !== 'idle' ? 'animate-breathe' : ''
    }`}>
      <Radio className="h-3.5 w-3.5" aria-hidden="true" /> Live {status}
    </span>
  );
};

export const WaiterDashboardPage: React.FC = () => {
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

  const { data: kitchenOrders } = useKitchenOrders({
    merchantId: merchantId ?? undefined,
    branchId: effectiveBranchId ?? undefined,
  });

  const { events, status } = useWaiterStream(merchantId, effectiveBranchId);
  const resolveRequest = useResolveRequest();

  // Urgency depends on elapsed time, so it must be recomputed on a timer or a
  // card frozen at "fresh" would never escalate.
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 15_000);
    return () => window.clearInterval(id);
  }, []);

  const pending = useMemo(
    // Oldest first: whoever has waited longest is served next.
    () => (data?.pendingRequests || []).slice().sort(byLongestWaiting),
    [data]
  );
  const assigned = data?.assignedTables || [];

  const myTableIds = useMemo(() => new Set(assigned.map((a) => a.tableId)), [assigned]);
  const myOrders = useMemo(
    () => (kitchenOrders || []).filter((o) => myTableIds.has(o.tableId) && ['PENDING', 'ACCEPTED', 'PREPARING', 'READY'].includes(o.status)),
    [kitchenOrders, myTableIds]
  );

  if (!merchantId) {
    return (
      <DashboardLayout title="Waiter Dashboard">
        <ErrorState message="Your account is not linked to a merchant, so waiter tasks cannot be loaded." />
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout title="Waiter Dashboard">
      <div className="space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <ConnectionBadge status={status} />
            {branches && branches.length > 0 && (
              <select
                aria-label="Branch"
                value={effectiveBranchId ?? ''}
                onChange={(e) => setBranchId(Number(e.target.value))}
                className="h-11 rounded-control border border-line bg-surface px-3 text-label-m text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark"
              >
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>{b.name}</option>
                ))}
              </select>
            )}
          </div>
          <Button variant="secondary" onClick={() => refetch()}>
            <RefreshCw className={`h-4 w-4 ${isFetching ? 'animate-spin' : ''}`} aria-hidden="true" /> Refresh
          </Button>
        </div>

        {isLoading ? (
          <Spinner label="Loading waiter tasks..." />
        ) : error ? (
          <ErrorState message={`Failed to load waiter tasks: ${(error as Error).message}`} />
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Pending customer requests */}
            <Card compact className="lg:col-span-2">
              <h3 className="flex items-center gap-2 text-label-m text-ink mb-4">
                <Bell className="w-4 h-4 text-brand-press" aria-hidden="true" />
                Customer Calls
                <CountBadge count={pending.length} />
              </h3>

              {pending.length === 0 ? (
                <EmptyState title="No customer calls" description="When a customer taps Call Waiter, Water, or Bill, it appears here instantly." />
              ) : (
                <ul className="space-y-3">
                  {pending.map((req) => {
                    const Icon = REQUEST_ICON[req.requestType] || Bell;
                    const tier = urgencyOf(req.createdAt, now);
                    return (
                      <li key={req.id} className={`flex flex-wrap items-center justify-between gap-3 rounded-card border p-4 ${URGENCY_CARD[tier]}`}>
                        <div className="flex items-center gap-3">
                          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-control bg-surface text-brand-press">
                            <Icon className="h-5 w-5" aria-hidden="true" />
                          </div>
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="text-label-m text-ink">{REQUEST_LABEL[req.requestType] || req.requestType}</span>
                              <span className={`rounded-pill px-2 py-0.5 text-label-s uppercase tracking-wide tabular-nums ${URGENCY_BADGE[tier]}`}>
                                {waitedLabel(req.createdAt, now)}
                              </span>
                            </div>
                            <div className="text-label-s text-muted flex items-center gap-2 mt-0.5">
                              <TableIcon className="w-3 h-3" aria-hidden="true" /> Table #{req.tableId}
                              <Clock className="w-3 h-3 ml-2" aria-hidden="true" /> {new Date(req.createdAt).toLocaleTimeString()}
                            </div>
                            {req.note && <p className="text-label-s text-muted mt-1 italic">&ldquo;{req.note}&rdquo;</p>}
                          </div>
                        </div>
                        <div className="flex gap-2">
                          <Button
                            variant="secondary"
                            size="lg"
                            disabled={resolveRequest.isPending}
                            onClick={() => resolveRequest.mutate({ requestId: req.id, status: 'ACKNOWLEDGED', merchantId })}
                          >
                            Acknowledge
                          </Button>
                          <Button
                            size="lg"
                            disabled={resolveRequest.isPending}
                            onClick={() => resolveRequest.mutate({ requestId: req.id, status: 'COMPLETED', merchantId })}
                          >
                            <CheckCircle2 className="w-3.5 h-3.5" aria-hidden="true" /> Complete
                          </Button>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              )}
            </Card>

            {/* Right column */}
            <div className="space-y-6">
              {/* Assigned tables */}
              <Card compact>
                <h3 className="flex items-center gap-2 text-label-m text-ink mb-4">
                  <TableIcon className="w-4 h-4 text-brand-press" aria-hidden="true" /> My Tables
                </h3>
                {assigned.length === 0 ? (
                  <p className="text-body-m text-muted">No active table assignments. A manager will assign tables to you.</p>
                ) : (
                  <div className="grid grid-cols-2 gap-2">
                    {assigned.map((a) => (
                      <div key={a.assignmentId ?? `${a.tableId}`} className="p-3 rounded-control border border-line bg-canvas text-center">
                        <div className="text-label-m text-ink tabular-nums">{a.tableNumber || `#${a.tableId}`}</div>
                        <div className="text-label-s text-muted">{a.shift || 'ACTIVE'}</div>
                      </div>
                    ))}
                  </div>
                )}
              </Card>

              {/* My table orders */}
              <Card compact>
                <h3 className="flex items-center gap-2 text-label-m text-ink mb-4">
                  <CookingPot className="w-4 h-4 text-warn" aria-hidden="true" /> Active Table Orders
                </h3>
                {myOrders.length === 0 ? (
                  <p className="text-body-m text-muted">No active orders on your assigned tables.</p>
                ) : (
                  <ul className="space-y-2">
                    {myOrders.map((o) => {
                      const chip = ORDER_STATUS_CHIP[o.status as OrderStatus] ?? { status: 'neutral' as const, label: o.status };
                      return (
                        <li key={o.id} className="rounded-control border border-line bg-canvas p-3">
                          <div className="flex items-center justify-between">
                            <span className="text-label-m text-ink tabular-nums">Table {o.tableNumber || o.tableId}</span>
                            <StatusChip status={chip.status}>{chip.label}</StatusChip>
                          </div>
                          <div className="text-label-s text-muted mt-1">#{o.orderNumber} - {o.items?.reduce((n, i) => n + i.quantity, 0)} items</div>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </Card>

              {/* Live alerts */}
              <Card compact>
                <h3 className="flex items-center gap-2 text-label-m text-ink mb-4">
                  <Radio className="h-4 w-4 text-success" aria-hidden="true" /> Live alerts
                </h3>
                {events.length === 0 ? (
                  <p className="text-body-m text-muted">Waiting for real-time alerts...</p>
                ) : (
                  <ul className="space-y-2 max-h-64 overflow-y-auto">
                    {events.map((e) => (
                      <li key={e.id} className="rounded-control border border-line bg-canvas p-2.5 text-body-m">
                        <span className="text-label-m text-ink">{e.eventType}</span>
                        <span className="text-muted ml-2 tabular-nums">{new Date(e.receivedAt).toLocaleTimeString()}</span>
                        {e.payload?.message && <p className="text-muted mt-0.5">{e.payload.message}</p>}
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
            </div>
          </div>
        )}
      </div>
    </DashboardLayout>
  );
};