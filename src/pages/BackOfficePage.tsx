import React from 'react';
import { ShieldCheck, Store, Building2, ScrollText } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { useBackOfficeSummary, useAuditLogs } from '../hooks/useApiData';

export const BackOfficePage: React.FC = () => {
  const { data: summary, isLoading: summaryLoading, error: summaryError, refetch: refetchSummary } = useBackOfficeSummary();
  const { data: logs, isLoading: logsLoading, error: logsError, refetch: refetchLogs } = useAuditLogs();

  return (
    <DashboardLayout title="Back Office">
      <div className="space-y-6">
        <div>
          <h2 className="text-xl font-black text-slate-900 flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-[#E60028]" />
            Back Office
          </h2>
          <p className="text-xs text-slate-500 mt-1">Platform-wide reporting and the append-only audit trail.</p>
        </div>

        {summaryLoading && <Spinner label="Loading platform summary..." />}
        {!summaryLoading && summaryError && (
          <ErrorState message="Could not load the platform summary." onRetry={() => refetchSummary()} />
        )}
        {!summaryLoading && !summaryError && summary && (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-red-50 flex items-center justify-center">
                <Store className="w-5 h-5 text-[#E60028]" />
              </div>
              <div>
                <div className="text-2xl font-black text-slate-900">{summary.merchantCount}</div>
                <div className="text-xs text-slate-500">Merchants</div>
              </div>
            </div>
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-red-50 flex items-center justify-center">
                <Building2 className="w-5 h-5 text-[#E60028]" />
              </div>
              <div>
                <div className="text-2xl font-black text-slate-900">{summary.branchCount}</div>
                <div className="text-xs text-slate-500">Branches</div>
              </div>
            </div>
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-red-50 flex items-center justify-center">
                <ScrollText className="w-5 h-5 text-[#E60028]" />
              </div>
              <div>
                <div className="text-2xl font-black text-slate-900">{summary.auditEventCount}</div>
                <div className="text-xs text-slate-500">Audit events</div>
              </div>
            </div>
          </div>
        )}

        <div>
          <h3 className="text-sm font-bold text-slate-900 mb-3">Audit Log</h3>

          {logsLoading && <Spinner label="Loading audit log..." />}
          {!logsLoading && logsError && (
            <ErrorState message="Could not load the audit log." onRetry={() => refetchLogs()} />
          )}
          {!logsLoading && !logsError && (logs?.length ?? 0) === 0 && (
            <EmptyState
              title="No audit events yet"
              description="Significant platform actions — like creating a merchant — will show up here."
            />
          )}
          {!logsLoading && !logsError && (logs?.length ?? 0) > 0 && (
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-left text-[10px] font-bold text-slate-500 uppercase">
                      <th className="px-4 py-3">Action</th>
                      <th className="px-4 py-3">Entity</th>
                      <th className="px-4 py-3">Details</th>
                      <th className="px-4 py-3">When</th>
                    </tr>
                  </thead>
                  <tbody>
                    {logs!.map((log) => (
                      <tr key={log.id} className="border-b border-slate-100 last:border-0">
                        <td className="px-4 py-3">
                          <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-slate-100 text-slate-700">
                            {log.action}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-slate-700">
                          {log.entityType}
                          {log.entityId && <span className="text-slate-400 font-mono text-[10px] ml-1">({log.entityId})</span>}
                        </td>
                        <td className="px-4 py-3 text-slate-500 text-xs">{log.details || '—'}</td>
                        <td className="px-4 py-3 text-slate-400 text-[10px] whitespace-nowrap">
                          {new Date(log.createdAt).toLocaleString()}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      </div>
    </DashboardLayout>
  );
};
