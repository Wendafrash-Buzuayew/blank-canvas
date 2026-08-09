import React, { useState } from 'react';
import { Building2, Plus, Edit2, Trash2, X, Loader2 } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { useAuth } from '../context/AuthContext';
import { useBranches, useCreateBranch, useUpdateBranch, useDeleteBranch } from '../hooks/useApiData';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { tableApi, branchApi, ApiError, BranchEntity } from '../lib/api';

export const BranchManagement: React.FC = () => {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editingBranch, setEditingBranch] = useState<BranchEntity | null>(null);
  const [formData, setFormData] = useState({
    merchantId: '',
    name: '',
    phone: '',
    address: '',
  });
  const [error, setError] = useState<string | null>(null);

  // Get merchant IDs from tables
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

  // Fetch branches for all merchants
  const branchQueries = useQuery({
    queryKey: ['branches-all', merchantIds],
    queryFn: async () => {
      const results = await Promise.all(
        merchantIds.map(id => branchApi.getBranchesByMerchant(id).catch(() => []))
      );
      return results.flat();
    },
    enabled: merchantIds.length > 0,
  });

  const createMutation = useCreateBranch();
  const updateMutation = useUpdateBranch();
  const deleteMutation = useDeleteBranch();

  const branches = branchQueries.data || [];

  console.log('[BranchManagement] branches:', branches);

  const handleOpenCreate = () => {
    setEditingBranch(null);
    setFormData({
      merchantId: merchantIds[0] || '',
      name: '',
      phone: '',
      address: '',
    });
    setError(null);
    setShowForm(true);
  };

  const handleOpenEdit = (branch: BranchEntity) => {
    setEditingBranch(branch);
    setFormData({
      merchantId: branch.merchantId,
      name: branch.name,
      phone: branch.phone,
      address: branch.address || '',
    });
    setError(null);
    setShowForm(true);
  };

  const handleDelete = async (id: number) => {
    if (!confirm('Are you sure you want to delete this branch?')) return;
    try {
      await deleteMutation.mutateAsync({ id, merchantId: merchantIds[0] || '' });
      queryClient.invalidateQueries({ queryKey: ['tables'] });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to delete branch');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      if (editingBranch) {
        await updateMutation.mutateAsync({ id: editingBranch.id, data: formData });
      } else {
        await createMutation.mutateAsync(formData);
      }
      setShowForm(false);
      queryClient.invalidateQueries({ queryKey: ['tables'] });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to save branch');
    }
  };

  const isLoading = createMutation.isPending || updateMutation.isPending || deleteMutation.isPending;

  return (
    <DashboardLayout title="Branch Management">
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-black text-slate-900 flex items-center gap-2">
              <Building2 className="w-5 h-5 text-[#E60028]" />
              Branches
            </h2>
            <p className="text-xs text-slate-500 mt-1">Create and manage restaurant branches</p>
          </div>
          <button
            onClick={handleOpenCreate}
            disabled={merchantIds.length === 0}
            className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-50 text-white text-xs font-bold rounded-xl flex items-center gap-2 shadow-sm"
          >
            <Plus className="w-4 h-4" />
            Add Branch
          </button>
        </div>

        {error && (
          <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700">
            {error}
          </div>
        )}

        {branchQueries.isLoading && <Spinner label="Loading branches..." />}

        {branchQueries.error && (
          <ErrorState
            message={`Failed to load branches: ${(branchQueries.error as Error).message}`}
            onRetry={() => branchQueries.refetch()}
          />
        )}

        {!branchQueries.isLoading && !branchQueries.error && branches.length === 0 && (
          <EmptyState
            title="No branches found"
            description="Create a branch for a merchant to get started."
          />
        )}

        {branches.length > 0 && (
          <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Name</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Phone</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Address</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Merchant ID</th>
                  <th className="text-right px-4 py-3 text-xs font-bold text-slate-600 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {branches.map((branch) => (
                  <tr key={branch.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-bold text-slate-900">{branch.name}</td>
                    <td className="px-4 py-3 text-slate-600">{branch.phone}</td>
                    <td className="px-4 py-3 text-slate-600">{branch.address || '-'}</td>
                    <td className="px-4 py-3 text-slate-400 font-mono text-[10px]">{branch.merchantId}</td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex justify-end gap-1">
                        <button
                          onClick={() => handleOpenEdit(branch)}
                          className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => handleDelete(branch.id)}
                          disabled={isLoading}
                          className="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
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
              <h3 className="font-bold text-sm">
                {editingBranch ? 'Edit Branch' : 'Create Branch'}
              </h3>
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
                  {merchantIds.map(id => (
                    <option key={id} value={id}>{id}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Branch Name</label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Phone</label>
                <input
                  type="tel"
                  required
                  value={formData.phone}
                  onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Address</label>
                <input
                  type="text"
                  value={formData.address}
                  onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>
              <button
                type="submit"
                disabled={isLoading}
                className="w-full py-2.5 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-60 text-white text-sm font-bold rounded-xl flex items-center justify-center gap-2"
              >
                {isLoading ? (
                  <><Loader2 className="w-4 h-4 animate-spin" /> Saving...</>
                ) : (
                  editingBranch ? 'Update Branch' : 'Create Branch'
                )}
              </button>
            </form>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
};