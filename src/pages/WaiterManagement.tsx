import React, { useMemo, useState } from 'react';
import { UserCog, Plus, Trash2 } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { EntitySelect } from '../components/ui/EntitySelect';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { useCreateWaiter, useDeleteWaiter, useUpdateWaiter, useWaiters } from '../hooks/useApiData';
import { useBranchesLookup, useMerchantsLookup, useUsersLookup } from '../hooks/useLookups';
import { friendlyError } from '../lib/errors';

const SHIFTS = [
  { id: 'MORNING', name: 'Morning' },
  { id: 'AFTERNOON', name: 'Afternoon' },
  { id: 'EVENING', name: 'Evening' },
  { id: 'NIGHT', name: 'Night' },
];

const selectClasses = 'h-9 rounded-control border border-line bg-surface px-2 text-label-s text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark';

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

  const statusOptions = (
    <>
      <option value="ACTIVE">Active</option>
      <option value="INACTIVE">Inactive</option>
      <option value="ON_BREAK">On Break</option>
    </>
  );

  return (
    <DashboardLayout title="Waiter Management">
      <div className="mx-auto max-w-[80rem] space-y-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="flex items-center gap-2 text-title-m text-ink">
              <UserCog className="h-5 w-5 text-brand-press" aria-hidden="true" />
              Waiters
            </h2>
            <p className="mt-1 text-body-m text-muted">Assign staff to branches and manage shifts</p>
          </div>
          <Button onClick={openCreate} disabled={merchants.length === 0}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Add Waiter
          </Button>
        </div>

        {pageError && (
          <div role="alert" className="rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink">
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
          <>
            {/* DESIGN.md 6.6: below md, a table becomes a card list, not a horizontal scroll. */}
            <div className="space-y-3 md:hidden">
              {waiters.map((waiter) => {
                const person = userById.get(waiter.userId);
                const displayName = person?.name ?? (usersQuery.isLoading ? 'Loading...' : 'Unknown staff');
                return (
                  <Card key={waiter.id} compact>
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="text-label-m text-ink">{displayName}</p>
                        {person?.email && <p className="truncate text-body-m text-muted">{person.email}</p>}
                        <p className="mt-1 text-label-s text-muted">
                          {merchantNameById.get(waiter.merchantId) ?? '-'} - {branchNameById.get(waiter.branchId) ?? '-'} - {SHIFTS.find((s) => s.id === waiter.shift)?.name ?? '-'}
                        </p>
                      </div>
                      <button
                        onClick={() => handleDelete(waiter.id, displayName)}
                        disabled={isBusy}
                        aria-label={`Remove ${displayName}`}
                        className="shrink-0 rounded-control p-1.5 text-muted hover:bg-danger-soft hover:text-danger"
                      >
                        <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                      </button>
                    </div>
                    <select
                      value={waiter.status}
                      onChange={(e) => handleStatusChange(waiter.id, e.target.value)}
                      disabled={isBusy}
                      aria-label={`Status for ${displayName}`}
                      className={`${selectClasses} mt-2 w-full`}
                    >
                      {statusOptions}
                    </select>
                  </Card>
                );
              })}
            </div>

            <div className="hidden overflow-x-auto rounded-card border border-line bg-surface shadow-[var(--shadow-card)] md:block">
              <table className="w-full min-w-[45rem] text-body-m">
                <thead className="border-b border-line bg-surface-2">
                  <tr>
                    <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Waiter</th>
                    <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Merchant</th>
                    <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Branch</th>
                    <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Shift</th>
                    <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Status</th>
                    <th className="px-4 py-3 text-right text-label-s uppercase text-muted">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {waiters.map((waiter) => {
                    const person = userById.get(waiter.userId);
                    const displayName = person?.name ?? (usersQuery.isLoading ? 'Loading...' : 'Unknown staff');
                    return (
                      <tr key={waiter.id} className="min-h-12 hover:bg-surface-2">
                        <td className="px-4 py-3">
                          <p className="text-ink">{displayName}</p>
                          {person?.email && <p className="text-label-s text-muted">{person.email}</p>}
                        </td>
                        <td className="px-4 py-3 text-muted">
                          {merchantNameById.get(waiter.merchantId) ?? '-'}
                        </td>
                        <td className="px-4 py-3 text-muted">
                          {branchNameById.get(waiter.branchId) ??
                            (allBranchesQuery.isLoading ? 'Loading...' : '-')}
                        </td>
                        <td className="px-4 py-3 text-muted">
                          {SHIFTS.find((s) => s.id === waiter.shift)?.name ?? '-'}
                        </td>
                        <td className="px-4 py-3">
                          <select
                            value={waiter.status}
                            onChange={(e) => handleStatusChange(waiter.id, e.target.value)}
                            disabled={isBusy}
                            aria-label={`Status for ${displayName}`}
                            className={selectClasses}
                          >
                            {statusOptions}
                          </select>
                        </td>
                        <td className="px-4 py-3 text-right">
                          <button
                            onClick={() => handleDelete(waiter.id, displayName)}
                            disabled={isBusy}
                            aria-label={`Remove ${displayName}`}
                            className="rounded-control p-1.5 text-muted hover:bg-danger-soft hover:text-danger"
                          >
                            <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>

      <Modal open={showForm} onClose={() => setShowForm(false)} title="Create Waiter">
        <form onSubmit={handleSubmit} className="space-y-3">
          <p className="-mt-1 text-label-s text-muted">Link an existing staff account to a branch.</p>
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
            <div role="alert" className="rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink">
              {formError}
            </div>
          )}

          <div className="flex gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={() => setShowForm(false)} fullWidth>
              Cancel
            </Button>
            <Button type="submit" loading={createMutation.isPending} fullWidth>
              {createMutation.isPending ? 'Saving...' : 'Create Waiter'}
            </Button>
          </div>
        </form>
      </Modal>
    </DashboardLayout>
  );
};
