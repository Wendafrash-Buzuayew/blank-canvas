import React from 'react';
import { ShieldCheck, Store, Building2, ScrollText } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { Card } from '../components/ui/Card';
import { IdentityChip } from '../components/ui/Chip';
import { useBackOfficeSummary, useAuditLogs } from '../hooks/useApiData';

export const BackOfficePage: React.FC = () => {
  const { data: summary, isLoading: summaryLoading, error: summaryError, refetch: refetchSummary } = useBackOfficeSummary();
  const { data: logs, isLoading: logsLoading, error: logsError, refetch: refetchLogs } = useAuditLogs();

  return (
    <DashboardLayout title="Back Office">
      <div className="mx-auto max-w-[80rem] space-y-6">
        <div>
          <h2 className="flex items-center gap-2 text-title-m text-ink">
            <ShieldCheck className="h-5 w-5 text-brand-press" aria-hidden="true" />
            Back Office
          </h2>
          <p className="mt-1 text-body-m text-muted">Platform-wide reporting and the append-only audit trail.</p>
        </div>

        {summaryLoading && <Spinner label="Loading platform summary..." />}
        {!summaryLoading && summaryError && (
          <ErrorState message="Could not load the platform summary." onRetry={() => refetchSummary()} />
        )}
        {!summaryLoading && !summaryError && summary && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Card compact className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-control bg-brand-soft">
                <Store className="h-5 w-5 text-brand-press" aria-hidden="true" />
              </div>
              <div>
                <div className="text-title-l [font-variant-numeric:tabular-nums]">{summary.merchantCount}</div>
                <div className="text-label-s text-muted">Merchants</div>
              </div>
            </Card>
            <Card compact className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-control bg-info-soft">
                <Building2 className="h-5 w-5 text-info" aria-hidden="true" />
              </div>
              <div>
                <div className="text-title-l [font-variant-numeric:tabular-nums]">{summary.branchCount}</div>
                <div className="text-label-s text-muted">Branches</div>
              </div>
            </Card>
            <Card compact className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-control bg-surface-2">
                <ScrollText className="h-5 w-5 text-muted" aria-hidden="true" />
              </div>
              <div>
                <div className="text-title-l [font-variant-numeric:tabular-nums]">{summary.auditEventCount}</div>
                <div className="text-label-s text-muted">Audit events</div>
              </div>
            </Card>
          </div>
        )}

        <div>
          <h3 className="mb-3 text-label-m text-ink">Audit Log</h3>

          {logsLoading && <Spinner label="Loading audit log..." />}
          {!logsLoading && logsError && (
            <ErrorState message="Could not load the audit log." onRetry={() => refetchLogs()} />
          )}
          {!logsLoading && !logsError && (logs?.length ?? 0) === 0 && (
            <EmptyState
              title="No audit events yet"
              description="Significant platform actions - like creating a merchant - will show up here."
            />
          )}
          {!logsLoading && !logsError && (logs?.length ?? 0) > 0 && (
            <>
              {/* DESIGN.md 6.6: below md, a table becomes a card list, not a horizontal scroll. */}
              <div className="space-y-3 md:hidden">
                {logs!.map((log) => (
                  <Card key={log.id} compact>
                    <div className="flex items-center justify-between gap-2">
                      <IdentityChip>{log.action}</IdentityChip>
                      <span className="whitespace-nowrap text-label-s text-muted">{new Date(log.createdAt).toLocaleString()}</span>
                    </div>
                    <p className="mt-2 text-label-m text-ink">
                      {log.entityType}
                      {log.entityId && <span className="ml-1 font-mono text-label-s text-muted">({log.entityId})</span>}
                    </p>
                    {log.details && <p className="mt-1 text-body-m text-muted">{log.details}</p>}
                  </Card>
                ))}
              </div>

              <div className="hidden overflow-hidden rounded-card border border-line bg-surface shadow-[var(--shadow-card)] md:block">
                <table className="w-full text-body-m">
                  <thead>
                    <tr className="border-b border-line bg-surface-2 text-left text-label-s uppercase text-muted">
                      <th className="px-4 py-3">Action</th>
                      <th className="px-4 py-3">Entity</th>
                      <th className="px-4 py-3">Details</th>
                      <th className="px-4 py-3">When</th>
                    </tr>
                  </thead>
                  <tbody>
                    {logs!.map((log) => (
                      <tr key={log.id} className="min-h-12 border-b border-line last:border-0 hover:bg-surface-2">
                        <td className="px-4 py-3">
                          <IdentityChip>{log.action}</IdentityChip>
                        </td>
                        <td className="px-4 py-3 text-ink">
                          {log.entityType}
                          {log.entityId && <span className="ml-1 font-mono text-label-s text-muted">({log.entityId})</span>}
                        </td>
                        <td className="px-4 py-3 text-muted">{log.details || '-'}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-label-s text-muted">
                          {new Date(log.createdAt).toLocaleString()}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>
      </div>
    </DashboardLayout>
  );
};
