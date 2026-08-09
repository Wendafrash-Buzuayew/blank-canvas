import React, { useState } from 'react';
import { UserCog, Plus, Trash2, X, Loader2 } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { useWaiters, useCreateWaiter, useDeleteWaiter, useUpdateWaiter } from '../hooks/useApiData';
import { useQuery } from '@tanstack/react-query';
import { tableApi, ApiError } from '../lib/api';

export const WaiterManagement: React.FC = () => {
  const { data: waitersData, isLoading, error, refetch } = useWaiters();
  const createMutation = useCreateWaiter();
  const deleteMutation = useDeleteWaiter();
  const updateMutation = useUpdateWaiter();
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    merchantId: '',
    branchId: 0,
    userId: '',
    status: 'ACTIVE',
    shift: 'MORNING',
  });
  const [error2, setError] = useState<string | null>(null);

  const { data: tables } = useQuery({
    queryKey: ['tables'],
    queryFn: () => tableApi.getAllTables(),
  });

  const merchantIds = React.useMemo(() => {
    if (!tables) return [];
    const ids = new Set<string>();
    tables.forEach(t => ids.add(t.merchantId));
    return Array.from(ids);
  }, [tables]);

  const branchIds = React.useMemo(() => {
    if (!tables) return [];
    const ids = new Set<number>();
    tables.forEach(t => ids.add(t.branchId));
    return Array.from(ids);
  }, [tables]);

  const waiters = waitersData || [];
  console.log('[WaiterManagement] waiters:', waiters);

  const handleOpenCreate = () => {
    setFormData({
      merchantId: merchantIds[0] || '',
      branchId: branchIds[0] || 0,
      userId: '',
      status: 'ACTIVE',
      shift: 'MORNING',
    });
    setError(null);
    setShowForm(true);
  };

  const handleDelete = async (id: number) => {
    if (!confirm('Delete this waiter?')) return;
    try {
      await deleteMutation.mutateAsync({ id });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to delete waiter');
    }
  };

  const handleStatusChange = async (id: number, status: string) => {
    try {
      await updateMutation.mutateAsync({ id, data: { status } });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to update status');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await createMutation.mutateAsync(formData);
      setShowForm(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to create waiter');
    }
  };

  const isLoading2 = createMutation.isPending || deleteMutation.isPending || updateMutation.isPending;

  return (
    <DashboardLayout title="Waiter Management">
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-black text-slate-900 flex items-center gap-2">
              <UserCog className="w-5 h-5 text-[#E60028]" />
              Waiters
            </h2>
            <p className="text-xs text-slate-500 mt-1">Manage waiter staff and shifts</p>
          </div>
          <button
            onClick={handleOpenCreate}
            disabled={merchantIds.length === 0}
            className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-50 text-white text-xs font-bold rounded-xl flex items-center gap-2 shadow-sm"
          >
            <Plus className="w-4 h-4" />
            Add Waiter
          </button>
        </div>

        {error2 && (
          <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700">
            {error2}
          </div>
        )}

        {isLoading && <Spinner label="Loading waiters..." />}

        {error && (
          <ErrorState message={`Failed to load waiters: ${(error as Error).message}`} onRetry={() => refetch()} />
        )}

        {!isLoading && !error && waiters.length === 0 && (
          <EmptyState title="No waiters found" description="Add waiter staff to assign tables." />
        )}

        {waiters.length > 0 && (
          <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">ID</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">User ID</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Branch</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Shift</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Status</th>
                  <th className="text-right px-4 py-3 text-xs font-bold text-slate-600 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {waiters.map((waiter) => (
                  <tr key={waiter.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-bold text-slate-900">{waiter.id}</td>
                    <td className="px-4 py-3 text-slate-600 font-mono text-[10px]">{waiter.userId}</td>
                    <td className="px-4 py-3 text-slate-600">{waiter.branchId}</td>
                    <td className="px-4 py-3 text-slate-600">{waiter.shift || '-'}</td>
                    <td className="px-4 py-3">
                      <select
                        value={waiter.status}
                        onChange={(e) => handleStatusChange(waiter.id, e.target.value)}
                        disabled={isLoading2}
                        className="px-2 py-1 text-xs rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20"
                      >
                        <option value="ACTIVE">Active</option>
                        <option value="INACTIVE">Inactive</option>
                        <option value="ON_BREAK">On Break</option>
                      </select>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={() => handleDelete(waiter.id)}
                        disabled={isLoading2}
                        className="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showForm && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md">
            <div className="flex items-center justify-between p-4 border-b border-slate-200">
              <h3 className="font-bold text-sm">Create Waiter</h3>
              <button onClick={() => setShowForm(false)} className="p-1 text-slate-400 hover:text-slate-900 rounded-lg">
                <X className="w-4 h-4" />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="p-4 space-y-3">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Merchant</label>
                <select
                  required
                  value={formData.merchantId}
                  onChange={(e) => setFormData({ ...formData, merchantId: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                >
                  <option value="">Select merchant...</option>
                  {merchantIds.map(id => <option key={id} value={id}>{id}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Branch</label>
                <select
                  required
                  value={formData.branchId}
                  onChange={(e) => setFormData({ ...formData, branchId: Number(e.target.value) })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                >
                  <option value={0}>Select branch...</option>
                  {branchIds.map(id => <option key={id} value={id}>Branch {id}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">User ID</label>
                <input
                  type="text"
                  required
                  value={formData.userId}
                  onChange={(e) => setFormData({ ...formData, userId: e.target.value })}
                  placeholder="UUID of the user account"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Shift</label>
                <select
                  value={formData.shift}
                  onChange={(e) => setFormData({ ...formData, shift: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                >
                  <option value="MORNING">Morning</option>
                  <option value="AFTERNOON">Afternoon</option>
                  <option value="EVENING">Evening</option>
                  <option value="NIGHT">Night</option>
                </select>
              </div>
              <button
                type="submit"
                disabled={isLoading2}
                className="w-full py-2.5 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-60 text-white text-sm font-bold rounded-xl flex items-center justify-center gap-2"
              >
                {isLoading2 ? <><Loader2 className="w-4 h-4 animate-spin" /> Saving...</> : 'Create Waiter'}
              </button>
            </form>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
};