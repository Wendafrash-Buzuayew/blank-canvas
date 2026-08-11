import React, { useMemo, useState } from 'react';
import { Building2, Plus, Edit2, Trash2, X, Loader2 } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { EntitySelect } from '../components/ui/EntitySelect';
import { useCreateBranch, useUpdateBranch, useDeleteBranch } from '../hooks/useApiData';
import { useBranchesLookup, useMerchantsLookup, useTablesLookup } from '../hooks/useLookups';
import { friendlyError } from '../lib/errors';
import { BranchEntity } from '../lib/api';

export const BranchManagement: React.FC = () => {
  const merchantsQuery = useMerchantsLookup();
  const branchesQuery = useBranchesLookup();
  const tablesQuery = useTablesLookup();

  const createMutation = useCreateBranch();
  const updateMutation = useUpdateBranch();
  const deleteMutation = useDeleteBranch();

  const [showForm, setShowForm] = useState(false);
  const [editingBranch, setEditingBranch] = useState<BranchEntity | null>(null);
  const [formData, setFormData] = useState({ merchantId: '', name: '', phone: '', address: '' });
  const [formError, setFormError] = useState<string | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);

  const merchants = merchantsQuery.data ?? [];
  const branches = branchesQuery.data ?? [];
  const tables = tablesQuery.data ?? [];

  const merchantNameById = useMemo(
    () => new Map(merchants.map((m) => [m.id, m.name])),
    [merchants]
  );
  const tableCountByBranch = useMemo(() => {
    const counts = new Map<number, number>();
    tables.forEach((t) => counts.set(t.branchId, (counts.get(t.branchId) ?? 0) + 1));
    return counts;
  }, [tables]);

  const openCreate = () => {
    setEditingBranch(null);
    setFormData({ merchantId: merchants.length === 1 ? merchants[0].id : '', name: '', phone: '', address: '' });
    setFormError(null);
    setShowForm(true);
  };

  const openEdit = (branch: BranchEntity) => {
    setEditingBranch(branch);
    setFormData({
      merchantId: branch.merchantId,
      name: branch.name,
      phone: branch.phone,
      address: branch.address || '',
    });
    setFormError(null);
    setShowForm(true);
  };

  const handleDelete = async (branch: BranchEntity) => {
    if (!confirm(`Delete "${branch.name}"? This cannot be undone.`)) return;
    setPageError(null);
    try {
      await deleteMutation.mutateAsync({ id: branch.id, merchantId: branch.merchantId });
      branchesQuery.refetch();
    } catch (err) {
      setPageError(friendlyError(err, 'We could not delete this branch.'));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    if (!formData.merchantId) {
      setFormError('Please choose the merchant this branch belongs to.');
      return;
    }
    if (!formData.name.trim()) {
      setFormError('Please enter a branch name.');
      return;
    }

    try {
      if (editingBranch) {
        await updateMutation.mutateAsync({ id: editingBranch.id, data: formData });
      } else {
        await createMutation.mutateAsync(formData);
      }
      setShowForm(false);
      branchesQuery.refetch();
    } catch (err) {
      setFormError(friendlyError(err, 'We could not save this branch.'));
    }
  };

  const isSaving = createMutation.isPending || updateMutation.isPending;
  const isBusy = isSaving || deleteMutation.isPending;

  return (
    <DashboardLayout title="Branch Management">
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <h2 className="text-xl font-black text-slate-900 flex items-center gap-2">
              <Building2 className="w-5 h-5 text-[#E60028]" />
              Branches
            </h2>
            <p className="text-xs text-slate-500 mt-1">Create and manage restaurant branches</p>
          </div>
          <button
            onClick={openCreate}
            disabled={merchants.length === 0}
            className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-50 text-white text-xs font-bold rounded-xl flex items-center justify-center gap-2 shadow-sm"
          >
            <Plus className="w-4 h-4" />
            Add Branch
          </button>
        </div>

        {pageError && (
          <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700">
            {pageError}
          </div>
        )}

        {branchesQuery.isLoading && <Spinner label="Loading branches..." />}

        {!branchesQuery.isLoading && branchesQuery.error && (
          <ErrorState
            message={friendlyError(branchesQuery.error, 'We could not load branches right now.')}
            onRetry={() => branchesQuery.refetch()}
          />
        )}

        {!branchesQuery.isLoading && !branchesQuery.error && branches.length === 0 && (
          <EmptyState
            title="No branches found"
            description={
              merchants.length === 0
                ? 'Create a merchant first, then add its branches.'
                : 'Create your first branch to continue.'
            }
            action={
              merchants.length > 0 ? (
                <button
                  onClick={openCreate}
                  className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-bold rounded-xl"
                >
                  Add Branch
                </button>
              ) : undefined
            }
          />
        )}

        {branches.length > 0 && (
          <div className="bg-white rounded-2xl border border-slate-200 overflow-x-auto shadow-sm">
            <table className="w-full text-sm min-w-[640px]">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Branch</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Merchant</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Phone</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Address</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Tables</th>
                  <th className="text-right px-4 py-3 text-xs font-bold text-slate-600 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {branches.map((branch) => (
                  <tr key={branch.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-bold text-slate-900">{branch.name}</td>
                    <td className="px-4 py-3 text-slate-600">
                      {merchantNameById.get(branch.merchantId) ??
                        (merchantsQuery.isLoading ? 'Loading…' : '—')}
                    </td>
                    <td className="px-4 py-3 text-slate-600">{branch.phone || '—'}</td>
                    <td className="px-4 py-3 text-slate-600">{branch.address || '—'}</td>
                    <td className="px-4 py-3 text-slate-600">{tableCountByBranch.get(branch.id) ?? 0}</td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex justify-end gap-1">
                        <button
                          onClick={() => openEdit(branch)}
                          aria-label={`Edit ${branch.name}`}
                          className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => handleDelete(branch)}
                          disabled={isBusy}
                          aria-label={`Delete ${branch.name}`}
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
        <div className="fixed inset-0 bg-black/40 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4">
          <div className="bg-white rounded-t-2xl sm:rounded-2xl shadow-xl w-full sm:max-w-md max-h-[90vh] overflow-y-auto">
            <div className="flex items-start justify-between p-4 border-b border-slate-200">
              <div>
                <h3 className="font-bold text-sm">{editingBranch ? 'Edit Branch' : 'Create Branch'}</h3>
                <p className="text-[11px] text-slate-500 mt-0.5">
                  Branches belong to a merchant and hold their own tables.
                </p>
              </div>
              <button
                onClick={() => setShowForm(false)}
                aria-label="Close"
                className="p-1 text-slate-400 hover:text-slate-900 rounded-lg"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="p-4 space-y-3">
              <EntitySelect
                label="Merchant"
                required
                placeholder="Select merchant"
                value={formData.merchantId}
                onChange={(value) => setFormData({ ...formData, merchantId: value ?? '' })}
                options={merchants}
                descriptionKey="city"
                isLoading={merchantsQuery.isLoading}
                loadingMessage="Loading merchants..."
                emptyMessage="No merchants found. Create a merchant before adding branches."
                disabled={!!editingBranch}
                helperText={editingBranch ? 'A branch cannot be moved to another merchant.' : undefined}
              />

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Branch Name *</label>
                <input
                  type="text"
                  required
                  maxLength={100}
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="e.g. Bole Branch"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Phone *</label>
                <input
                  type="tel"
                  required
                  maxLength={30}
                  value={formData.phone}
                  onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Address</label>
                <input
                  type="text"
                  maxLength={200}
                  value={formData.address}
                  onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>

              {formError && (
                <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700">
                  {formError}
                </div>
              )}

              <div className="flex gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => setShowForm(false)}
                  className="flex-1 py-2.5 border border-slate-200 text-slate-700 text-sm font-bold rounded-xl hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSaving}
                  className="flex-1 py-2.5 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-60 text-white text-sm font-bold rounded-xl flex items-center justify-center gap-2"
                >
                  {isSaving ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" /> Saving...
                    </>
                  ) : editingBranch ? (
                    'Save Changes'
                  ) : (
                    'Create Branch'
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
};
