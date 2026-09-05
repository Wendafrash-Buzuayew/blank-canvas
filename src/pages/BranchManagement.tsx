import React, { useMemo, useState } from 'react';
import { Building2, Plus, Edit2, Trash2, Star } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { EntitySelect } from '../components/ui/EntitySelect';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { FormField } from '../components/ui/FormField';
import { useCreateBranch, useUpdateBranch, useDeleteBranch, useSetPrimaryBranch } from '../hooks/useApiData';
import { useBranchesLookup, useMerchantsLookup, useTablesLookup } from '../hooks/useLookups';
import { friendlyError } from '../lib/errors';
import { isPhase2Enabled } from '../lib/phase';
import { BranchEntity } from '../lib/api';

/**
 * Client-side preview only - the backend (Slugs.toPathSlug) is the real
 * source of truth and re-normalises whatever is submitted, so this just
 * needs to look right to the merchant while they type.
 */
function slugify(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

export const BranchManagement: React.FC = () => {
  const phase2 = isPhase2Enabled();
  const merchantsQuery = useMerchantsLookup();
  const branchesQuery = useBranchesLookup();
  // Table counts are a Phase 2 (ordering) concept - skip the lookup entirely
  // in Phase 1 rather than show a column backed by a deprecated dependency.
  const tablesQuery = useTablesLookup(phase2);

  const createMutation = useCreateBranch();
  const updateMutation = useUpdateBranch();
  const deleteMutation = useDeleteBranch();
  const setPrimaryMutation = useSetPrimaryBranch();

  const [showForm, setShowForm] = useState(false);
  const [editingBranch, setEditingBranch] = useState<BranchEntity | null>(null);
  const [formData, setFormData] = useState({ merchantId: '', name: '', slug: '', phone: '', address: '' });
  const [slugTouched, setSlugTouched] = useState(false);
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
    setFormData({ merchantId: merchants.length === 1 ? merchants[0].id : '', name: '', slug: '', phone: '', address: '' });
    setSlugTouched(false);
    setFormError(null);
    setShowForm(true);
  };

  const openEdit = (branch: BranchEntity) => {
    setEditingBranch(branch);
    setFormData({
      merchantId: branch.merchantId,
      name: branch.name,
      slug: branch.slug,
      phone: branch.phone,
      address: branch.address || '',
    });
    setSlugTouched(true);
    setFormError(null);
    setShowForm(true);
  };

  const handleNameChange = (name: string) => {
    setFormData((prev) => ({ ...prev, name, slug: slugTouched ? prev.slug : slugify(name) }));
  };

  const handleSetPrimary = async (branch: BranchEntity) => {
    setPageError(null);
    try {
      await setPrimaryMutation.mutateAsync({ id: branch.id, merchantId: branch.merchantId });
      branchesQuery.refetch();
    } catch (err) {
      setPageError(friendlyError(err, 'We could not set this branch as primary.'));
    }
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
    if (!editingBranch && !formData.slug.trim()) {
      setFormError("Please enter a URL slug - it becomes part of this branch's public menu link.");
      return;
    }

    try {
      if (editingBranch) {
        await updateMutation.mutateAsync({
          id: editingBranch.id,
          merchantId: editingBranch.merchantId,
          data: { name: formData.name, phone: formData.phone, address: formData.address },
        });
      } else {
        await createMutation.mutateAsync({
          merchantId: formData.merchantId,
          name: formData.name,
          slug: formData.slug,
          phone: formData.phone,
          address: formData.address,
        });
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
      <div className="mx-auto max-w-[80rem] space-y-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="flex items-center gap-2 text-title-m text-ink">
              <Building2 className="h-5 w-5 text-brand-press" aria-hidden="true" />
              Branches
            </h2>
            <p className="mt-1 text-body-m text-muted">Create and manage restaurant branches</p>
          </div>
          <Button onClick={openCreate} disabled={merchants.length === 0}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Add Branch
          </Button>
        </div>

        {pageError && (
          <div role="alert" className="rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink">
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
            action={merchants.length > 0 ? <Button onClick={openCreate}>Add Branch</Button> : undefined}
          />
        )}

        {branches.length > 0 && (
          <>
            {/* DESIGN.md 6.6: below md, a table becomes a card list, not a horizontal scroll. */}
            <div className="space-y-3 md:hidden">
              {branches.map((branch) => (
                <Card key={branch.id} compact>
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="flex items-center gap-1.5">
                        <span className="text-label-m text-ink">{branch.name}</span>
                        {branch.isPrimary && <Star className="h-3.5 w-3.5 shrink-0 fill-warn text-warn" aria-label="Primary branch" />}
                      </div>
                      <div className="text-label-s text-muted">/{branch.slug}</div>
                      <div className="mt-1 text-body-m text-muted">
                        {merchantNameById.get(branch.merchantId) ?? '-'} - {branch.phone || '-'}
                      </div>
                    </div>
                    <div className="flex shrink-0 gap-1">
                      {!branch.isPrimary && (
                        <button
                          onClick={() => handleSetPrimary(branch)}
                          disabled={setPrimaryMutation.isPending}
                          aria-label={`Set ${branch.name} as primary`}
                          className="flex h-9 w-9 items-center justify-center rounded-control text-muted hover:bg-warn-soft hover:text-warn"
                        >
                          <Star className="h-4 w-4" aria-hidden="true" />
                        </button>
                      )}
                      <button
                        onClick={() => openEdit(branch)}
                        aria-label={`Edit ${branch.name}`}
                        className="flex h-9 w-9 items-center justify-center rounded-control text-muted hover:bg-info-soft hover:text-info"
                      >
                        <Edit2 className="h-4 w-4" aria-hidden="true" />
                      </button>
                      <button
                        onClick={() => handleDelete(branch)}
                        disabled={isBusy}
                        aria-label={`Delete ${branch.name}`}
                        className="flex h-9 w-9 items-center justify-center rounded-control text-muted hover:bg-danger-soft hover:text-danger"
                      >
                        <Trash2 className="h-4 w-4" aria-hidden="true" />
                      </button>
                    </div>
                  </div>
                </Card>
              ))}
            </div>

            <div className="hidden overflow-x-auto rounded-card border border-line bg-surface shadow-[var(--shadow-card)] md:block">
              <table className="w-full min-w-[40rem] text-body-m">
                <thead className="border-b border-line bg-surface-2">
                  <tr>
                    <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Branch</th>
                    <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Merchant</th>
                    <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Phone</th>
                    <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Address</th>
                    {phase2 && <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Tables</th>}
                    <th className="px-4 py-3 text-right text-label-s uppercase text-muted">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {branches.map((branch) => (
                    <tr key={branch.id} className="min-h-12 hover:bg-surface-2">
                      <td className="px-4 py-3 text-ink">
                        <div className="flex items-center gap-1.5">
                          {branch.name}
                          {branch.isPrimary && (
                            <span title="Primary branch - this is where /m/{merchant-slug} redirects to">
                              <Star className="h-3.5 w-3.5 fill-warn text-warn" aria-hidden="true" />
                            </span>
                          )}
                        </div>
                        <div className="text-label-s text-muted">/{branch.slug}</div>
                      </td>
                      <td className="px-4 py-3 text-muted">
                        {merchantNameById.get(branch.merchantId) ??
                          (merchantsQuery.isLoading ? 'Loading...' : '-')}
                      </td>
                      <td className="px-4 py-3 text-muted">{branch.phone || '-'}</td>
                      <td className="px-4 py-3 text-muted">{branch.address || '-'}</td>
                      {phase2 && <td className="px-4 py-3 text-muted">{tableCountByBranch.get(branch.id) ?? 0}</td>}
                      <td className="px-4 py-3 text-right">
                        <div className="flex justify-end gap-1">
                          {!branch.isPrimary && (
                            <button
                              onClick={() => handleSetPrimary(branch)}
                              disabled={setPrimaryMutation.isPending}
                              aria-label={`Set ${branch.name} as primary`}
                              title="Set as primary branch"
                              className="rounded-control p-1.5 text-muted hover:bg-warn-soft hover:text-warn"
                            >
                              <Star className="h-3.5 w-3.5" aria-hidden="true" />
                            </button>
                          )}
                          <button
                            onClick={() => openEdit(branch)}
                            aria-label={`Edit ${branch.name}`}
                            className="rounded-control p-1.5 text-muted hover:bg-info-soft hover:text-info"
                          >
                            <Edit2 className="h-3.5 w-3.5" aria-hidden="true" />
                          </button>
                          <button
                            onClick={() => handleDelete(branch)}
                            disabled={isBusy}
                            aria-label={`Delete ${branch.name}`}
                            className="rounded-control p-1.5 text-muted hover:bg-danger-soft hover:text-danger"
                          >
                            <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>

      <Modal
        open={showForm}
        onClose={() => setShowForm(false)}
        title={editingBranch ? 'Edit Branch' : 'Create Branch'}
      >
        <form onSubmit={handleSubmit} className="space-y-3">
          <p className="-mt-1 text-label-s text-muted">Each branch has its own menu and public menu link.</p>
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

          <FormField
            label="Branch Name"
            required
            maxLength={100}
            value={formData.name}
            onChange={(e) => handleNameChange(e.target.value)}
            placeholder="e.g. Bole Branch"
          />

          <FormField
            label="URL Slug"
            required
            maxLength={60}
            disabled={!!editingBranch}
            value={formData.slug}
            onChange={(e) => { setSlugTouched(true); setFormData({ ...formData, slug: slugify(e.target.value) }); }}
            placeholder="e.g. bole-branch"
            hint={
              editingBranch
                ? "Permanent - this is part of the branch's public menu link and cannot be changed."
                : "Becomes part of this branch's public menu link and cannot be changed later."
            }
          />

          <FormField
            label="Phone"
            type="tel"
            required
            maxLength={30}
            value={formData.phone}
            onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
          />

          <FormField
            label="Address"
            maxLength={200}
            value={formData.address}
            onChange={(e) => setFormData({ ...formData, address: e.target.value })}
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
            <Button type="submit" loading={isSaving} fullWidth>
              {isSaving ? 'Saving...' : editingBranch ? 'Save Changes' : 'Create Branch'}
            </Button>
          </div>
        </form>
      </Modal>
    </DashboardLayout>
  );
};
