import React, { useMemo, useState } from 'react';
import { UserCog, Plus, Trash2, X, Loader2 } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { EntitySelect } from '../components/ui/EntitySelect';
import { useCreateWaiter, useDeleteWaiter, useUpdateWaiter, useWaiters } from '../hooks/useApiData';
import { useBranchesLookup, useMerchantsLookup, useUsersLookup } from '../hooks/useLookups';
import { friendlyError } from '../lib/errors';

const SHIFTS = [
  { id: 'MORNING', name: 'Morning' },
  { id: 'AFTERNOON', name: 'Afternoon' },
  { id: 'EVENING', name: 'Evening' },
  { id: 'NIGHT', name: 'Night' },
];

export const WaiterManagement: React.FC = () => {
  const waitersQuery = useWaiters();
  const merchantsQuery = useMerchantsLookup();
  const usersQuery = useUsersLookup();

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
  const [formError, setFormError] = useState<string | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);

  const merchants = merchantsQuery.data ?? [];
  const allUsers = usersQuery.data ?? [];
  const waiters = waitersQuery.data ?? [];

  // Cascading: branches are scoped to the selected merchant.
  const branchesQuery = useBranchesLookup(formData.merchantId || null);
  const allBranchesQuery = useBranchesLookup();

  const branchNameById = useMemo(
    () => new Map((allBranchesQuery.data ?? []).map((b) => [b.id, b.name])),
    [allBranchesQuery.data]
  );
  const merchantNameById = useMemo(() => new Map(merchants.map((m) => [m.id, m.name])), [merchants]);
  const userById = useMemo(() => new Map(allUsers.map((u) => [u.id, u])), [allUsers]);

  // Only users with a staff role that are not already waiters can be linked.
  const assignedUserIds = useMemo(() => new Set(waiters.map((w) => w.userId)), [waiters]);
  const selectableUsers = useMemo(
    () =>
      allUsers.filter(
        (u) =>
          (u.role === 'WAITER' || u.role === 'CASHIER') &&
          (!formData.merchantId || !u.merchantId || u.merchantId === formData.merchantId) &&
          !assignedUserIds.has(u.id)
      ),
    [allUsers, formData.merchantId, assignedUserIds]
  );

  const openCreate = () => {
    setFormData({
      merchantId: merchants.length === 1 ? merchants[0].id : '',
      branchId: 0,
      userId: '',
      status: 'ACTIVE',
      shift: 'MORNING',
    });
    setFormError(null);
    setShowForm(true);
  };

  const handleDelete = async (id: number, name: string) => {
    if (!confirm(`Remove ${name} from the waiter roster?`)) return;
    setPageError(null);
    try {
      await deleteMutation.mutateAsync({ id });
    } catch (err) {
      setPageError(friendlyError(err, 'We could not remove this waiter.'));
    }
  };

  const handleStatusChange = async (id: number, status: string) => {
    setPageError(null);
    try {
      await updateMutation.mutateAsync({ id, data: { status } });
    } catch (err) {
      setPageError(friendlyError(err, 'We could not update this waiter.'));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    if (!formData.merchantId) return setFormError('Please select a merchant.');
    if (!formData.branchId) return setFormError('Please select the branch this waiter works at.');
    if (!formData.userId) return setFormError('Please select the staff member to assign as waiter.');

    try {
      await createMutation.mutateAsync(formData);
      setShowForm(false);
    } catch (err) {
      setFormError(friendlyError(err, 'We could not create this waiter.'));
    }
  };

  const isBusy = createMutation.isPending || deleteMutation.isPending || updateMutation.isPending;

  return (
    <DashboardLayout title="Waiter Management">
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <h2 className="text-xl font-black text-slate-900 flex items-center gap-2">
              <UserCog className="w-5 h-5 text-[#E60028]" />
              Waiters
            </h2>
            <p className="text-xs text-slate-500 mt-1">Assign staff to branches and manage shifts</p>
          </div>
          <button
            onClick={openCreate}
            disabled={merchants.length === 0}
            className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-50 text-white text-xs font-bold rounded-xl flex items-center justify-center gap-2 shadow-sm"
          >
            <Plus className="w-4 h-4" />
            Add Waiter
          </button>
        </div>

        {pageError && (
          <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700">
            {pageError}
          </div>
        )}

        {waitersQuery.isLoading && <Spinner label="Loading waiters..." />}

        {!waitersQuery.isLoading && waitersQuery.error && (
          <ErrorState
            message={friendlyError(waitersQuery.error, 'We could not load the waiter roster.')}
            onRetry={() => waitersQuery.refetch()}
          />
        )}

        {!waitersQuery.isLoading && !waitersQuery.error && waiters.length === 0 && (
          <EmptyState
            title="No waiters yet"
            description="Create a user account first, then assign that person as a waiter for a branch."
          />
        )}

        {waiters.length > 0 && (
          <div className="bg-white rounded-2xl border border-slate-200 overflow-x-auto shadow-sm">
            <table className="w-full text-sm min-w-[720px]">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Waiter</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Merchant</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Branch</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Shift</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Status</th>
                  <th className="text-right px-4 py-3 text-xs font-bold text-slate-600 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {waiters.map((waiter) => {
                  const person = userById.get(waiter.userId);
                  const displayName = person?.name ?? (usersQuery.isLoading ? 'Loading…' : 'Unknown staff');
                  return (
                    <tr key={waiter.id} className="hover:bg-slate-50">
                      <td className="px-4 py-3">
                        <p className="font-bold text-slate-900">{displayName}</p>
                        {person?.email && <p className="text-[11px] text-slate-500">{person.email}</p>}
                      </td>
                      <td className="px-4 py-3 text-slate-600">
                        {merchantNameById.get(waiter.merchantId) ?? '—'}
                      </td>
                      <td className="px-4 py-3 text-slate-600">
                        {branchNameById.get(waiter.branchId) ??
                          (allBranchesQuery.isLoading ? 'Loading…' : '—')}
                      </td>
                      <td className="px-4 py-3 text-slate-600">
                        {SHIFTS.find((s) => s.id === waiter.shift)?.name ?? '—'}
                      </td>
                      <td className="px-4 py-3">
                        <select
                          value={waiter.status}
                          onChange={(e) => handleStatusChange(waiter.id, e.target.value)}
                          disabled={isBusy}
                          aria-label={`Status for ${displayName}`}
                          className="px-2 py-1.5 text-xs rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20"
                        >
                          <option value="ACTIVE">Active</option>
                          <option value="INACTIVE">Inactive</option>
                          <option value="ON_BREAK">On Break</option>
                        </select>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => handleDelete(waiter.id, displayName)}
                          disabled={isBusy}
                          aria-label={`Remove ${displayName}`}
                          className="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </td>
                    </tr>
                  );
                })}
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
                <h3 className="font-bold text-sm">Create Waiter</h3>
                <p className="text-[11px] text-slate-500 mt-0.5">Link an existing staff account to a branch.</p>
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
                onChange={(value) =>
                  setFormData({ ...formData, merchantId: value ?? '', branchId: 0, userId: '' })
                }
                options={merchants}
                descriptionKey="city"
                isLoading={merchantsQuery.isLoading}
                loadingMessage="Loading merchants..."
                emptyMessage="No merchants found."
              />

              <EntitySelect
                label="Branch"
                required
                placeholder={formData.merchantId ? 'Select branch' : 'Select a merchant first'}
                value={formData.branchId || ''}
                onChange={(value) => setFormData({ ...formData, branchId: value ? Number(value) : 0 })}
                options={branchesQuery.data ?? []}
                descriptionKey="address"
                disabled={!formData.merchantId}
                isLoading={!!formData.merchantId && branchesQuery.isLoading}
                loadingMessage="Loading branches..."
                emptyMessage="No branches found. Create a branch before assigning waiters."
              />

              <EntitySelect
                label="Staff Member"
                required
                placeholder={formData.merchantId ? 'Select staff member' : 'Select a merchant first'}
                value={formData.userId}
                onChange={(value) => setFormData({ ...formData, userId: value ?? '' })}
                options={selectableUsers}
                descriptionKey="email"
                disabled={!formData.merchantId}
                isLoading={usersQuery.isLoading}
                loadingMessage="Loading users..."
                emptyMessage="No available staff accounts. Create a user with the Waiter role first."
                helperText="Only unassigned Waiter/Cashier accounts are listed."
              />

              <EntitySelect
                label="Shift"
                value={formData.shift}
                onChange={(value) => setFormData({ ...formData, shift: value ?? 'MORNING' })}
                options={SHIFTS}
                searchThreshold={99}
              />

              <EntitySelect
                label="Status"
                value={formData.status}
                onChange={(value) => setFormData({ ...formData, status: value ?? 'ACTIVE' })}
                options={[
                  { id: 'ACTIVE', name: 'Active' },
                  { id: 'INACTIVE', name: 'Inactive' },
                ]}
                searchThreshold={99}
              />

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
                  disabled={createMutation.isPending}
                  className="flex-1 py-2.5 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-60 text-white text-sm font-bold rounded-xl flex items-center justify-center gap-2"
                >
                  {createMutation.isPending ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" /> Saving...
                    </>
                  ) : (
                    'Create Waiter'
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
