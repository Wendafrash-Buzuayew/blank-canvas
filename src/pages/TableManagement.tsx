import React, { useMemo, useState } from 'react';
import { Table as TableIcon, Plus, Trash2, Loader2, Search, QrCode } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { EntitySelect } from '../components/ui/EntitySelect';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { FormField } from '../components/ui/FormField';
import { StatusChip } from '../components/ui/Chip';
import { useTables, useCreateTable, useDeleteTable, useUpdateTableStatus, useAssignWaiterV1, useTableQr } from '../hooks/useApiData';
import { useBranchesLookup, useMerchantsLookup, useUsersLookup, useWaitersLookup } from '../hooks/useLookups';
import { friendlyError } from '../lib/errors';
import { isAuthenticated, tableAssignmentApi, TableAssignmentEntity, CreateTableResponse } from '../lib/api';
import { canRenderQr, qrCaption, qrImageSrc } from '../lib/qrDisplay';

const selectClasses = 'h-9 rounded-control border border-line bg-surface px-2 text-label-s text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark';

export const TableManagement: React.FC = () => {
  const tablesQuery = useTables();
  const merchantsQuery = useMerchantsLookup();
  const allBranchesQuery = useBranchesLookup();
  const usersQuery = useUsersLookup();
  const waitersQuery = useWaitersLookup();

  const createMutation = useCreateTable();
  const deleteMutation = useDeleteTable();
  const updateStatusMutation = useUpdateTableStatus();
  const assignWaiterMutation = useAssignWaiterV1();

  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({ merchantId: '', branchId: 0, tableNumber: '', capacity: 4 });
  const [formError, setFormError] = useState<string | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [branchFilter, setBranchFilter] = useState<string>('');
  // The table just created, so we can show its QR immediately - the whole point of
  // creating a table is printing this sticker, so the merchant should see it now
  // rather than hunting for it in the list.
  const [createdTable, setCreatedTable] = useState<CreateTableResponse | null>(null);
  const createdTableQr = useTableQr(createdTable?.id);

  const tables = tablesQuery.data ?? [];
  const merchants = merchantsQuery.data ?? [];
  const allBranches = allBranchesQuery.data ?? [];
  const formBranchesQuery = useBranchesLookup(formData.merchantId || null);

  const merchantIds = useMemo(() => merchants.map((m) => m.id).sort(), [merchants]);

  // Active waiter assignments - fetched once per merchant, never per row.
  const assignmentsQuery = useQuery({
    queryKey: ['table-assignments', merchantIds],
    queryFn: async (): Promise<TableAssignmentEntity[]> => {
      const results = await Promise.all(
        merchantIds.map((id) => tableAssignmentApi.getByMerchant(id).catch(() => [] as TableAssignmentEntity[]))
      );
      return results.flat();
    },
    enabled: isAuthenticated() && merchantIds.length > 0,
    staleTime: 60_000,
  });

  const branchNameById = useMemo(() => new Map(allBranches.map((b) => [b.id, b.name])), [allBranches]);
  const userById = useMemo(() => new Map((usersQuery.data ?? []).map((u) => [u.id, u])), [usersQuery.data]);
  const waiterById = useMemo(
    () => new Map((waitersQuery.data ?? []).map((w) => [w.id, w])),
    [waitersQuery.data]
  );
  const waiterNameByTable = useMemo(() => {
    const map = new Map<number, string>();
    (assignmentsQuery.data ?? [])
      .filter((a) => a.status === 'ACTIVE' && !a.endedAt)
      .forEach((a) => {
        const waiter = waiterById.get(a.waiterId);
        const name = waiter ? userById.get(waiter.userId)?.name : undefined;
        map.set(a.tableId, name ?? 'Assigned');
      });
    return map;
  }, [assignmentsQuery.data, waiterById, userById]);

  const visibleTables = useMemo(() => {
    const q = search.trim().toLowerCase();
    return tables.filter((t) => {
      const matchesBranch = !branchFilter || String(t.branchId) === branchFilter;
      const matchesSearch =
        !q ||
        t.tableNumber.toLowerCase().includes(q) ||
        (branchNameById.get(t.branchId) ?? '').toLowerCase().includes(q);
      return matchesBranch && matchesSearch;
    });
  }, [tables, search, branchFilter, branchNameById]);

  const openCreate = () => {
    setFormData({
      merchantId: merchants.length === 1 ? merchants[0].id : '',
      branchId: 0,
      tableNumber: '',
      capacity: 4,
    });
    setFormError(null);
    setShowForm(true);
  };

  const handleDelete = async (id: number, label: string) => {
    if (!confirm(`Delete Table ${label}? Its QR code will stop working.`)) return;
    setPageError(null);
    try {
      await deleteMutation.mutateAsync(id);
    } catch (err) {
      setPageError(friendlyError(err, 'We could not delete this table.'));
    }
  };

  const handleStatusChange = async (id: number, status: string) => {
    setPageError(null);
    try {
      await updateStatusMutation.mutateAsync({ id, status });
    } catch (err) {
      setPageError(friendlyError(err, 'We could not update this table.'));
    }
  };

  const handleAssignWaiter = async (tableId: number, branchId: number, waiterId: number) => {
    if (!waiterId) return;
    setPageError(null);
    try {
      await assignWaiterMutation.mutateAsync({ tableId, branchId, waiterId });
      assignmentsQuery.refetch();
    } catch (err) {
      setPageError(friendlyError(err, 'We could not assign the waiter.'));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    if (!formData.merchantId) return setFormError('Please select a merchant.');
    if (!formData.branchId) return setFormError('Please select the branch this table belongs to.');
    if (!formData.tableNumber.trim()) return setFormError('Please enter a table number.');

    try {
      const created = await createMutation.mutateAsync({
        branchId: formData.branchId,
        tableNumber: formData.tableNumber.trim(),
        capacity: formData.capacity,
      });
      setShowForm(false);
      setCreatedTable(created);
    } catch (err) {
      setFormError(friendlyError(err, 'We could not create this table.'));
    }
  };

  const isBusy = deleteMutation.isPending || updateStatusMutation.isPending;

  return (
    <DashboardLayout title="Table Management">
      <div className="mx-auto max-w-[80rem] space-y-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="flex items-center gap-2 text-title-m text-ink">
              <TableIcon className="h-5 w-5 text-brand-press" aria-hidden="true" />
              Tables
            </h2>
            <p className="mt-1 text-body-m text-muted">Manage tables, seating and QR codes</p>
          </div>
          <Button onClick={openCreate} disabled={merchants.length === 0}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Add Table
          </Button>
        </div>

        {tables.length > 0 && (
          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden="true" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search tables or branches..."
                aria-label="Search tables"
                className="h-11 w-full rounded-control border border-line bg-surface pl-9 pr-3 text-body-m text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark"
              />
            </div>
            <div className="sm:w-56">
              <EntitySelect
                placeholder="All branches"
                clearable
                value={branchFilter}
                onChange={(value) => setBranchFilter(value ?? '')}
                options={allBranches}
                isLoading={allBranchesQuery.isLoading}
                loadingMessage="Loading branches..."
                emptyMessage="No branches found."
              />
            </div>
          </div>
        )}

        {pageError && (
          <div role="alert" className="rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink">
            {pageError}
          </div>
        )}

        {tablesQuery.isLoading && <Spinner label="Loading tables..." />}

        {!tablesQuery.isLoading && tablesQuery.error && (
          <ErrorState
            message={friendlyError(tablesQuery.error, 'We could not load tables right now.')}
            onRetry={() => tablesQuery.refetch()}
          />
        )}

        {!tablesQuery.isLoading && !tablesQuery.error && tables.length === 0 && (
          <EmptyState
            title="No tables yet"
            description="Create your first table to generate its QR code."
            action={merchants.length > 0 ? <Button onClick={openCreate}>Add Table</Button> : undefined}
          />
        )}

        {tables.length > 0 && visibleTables.length === 0 && (
          <EmptyState title="No matching tables" description="Try a different search term or branch filter." />
        )}

        {visibleTables.length > 0 && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {visibleTables.map((table) => (
              <Card key={table.id} compact className="space-y-3">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <h3 className="truncate text-label-m text-ink">Table {table.tableNumber}</h3>
                    <p className="truncate text-body-m text-muted">
                      {branchNameById.get(table.branchId) ?? (allBranchesQuery.isLoading ? 'Loading...' : '-')}
                    </p>
                  </div>
                  <button
                    onClick={() => handleDelete(table.id, table.tableNumber)}
                    disabled={isBusy}
                    aria-label={`Delete table ${table.tableNumber}`}
                    className="shrink-0 rounded-control p-1.5 text-muted hover:bg-danger-soft hover:text-danger"
                  >
                    <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                  </button>
                </div>

                <dl className="grid grid-cols-2 gap-2 text-body-m">
                  <div>
                    <dt className="text-label-s text-muted">Seats</dt>
                    <dd className="text-ink">{table.capacity}</dd>
                  </div>
                  <div className="min-w-0">
                    <dt className="text-label-s text-muted">Waiter</dt>
                    <dd className="truncate text-ink">
                      {waiterNameByTable.get(table.id) ?? 'Unassigned'}
                    </dd>
                  </div>
                </dl>

                <div className="flex items-center gap-2">
                  <select
                    value={table.status}
                    onChange={(e) => handleStatusChange(table.id, e.target.value)}
                    disabled={isBusy}
                    aria-label={`Status for table ${table.tableNumber}`}
                    className={`${selectClasses} flex-1`}
                  >
                    <option value="AVAILABLE">Available</option>
                    <option value="OCCUPIED">Occupied</option>
                    <option value="RESERVED">Reserved</option>
                  </select>
                  <StatusChip status={table.qrToken ? 'success' : 'neutral'}>
                    <QrCode className="h-3 w-3" aria-hidden="true" />
                    {table.qrToken ? 'QR Active' : 'No QR'}
                  </StatusChip>
                </div>

                <div>
                  <select
                    value={waiterNameByTable.get(table.id) ? 'assigned' : ''}
                    onChange={(e) => {
                      const waiterId = Number(e.target.value);
                      if (waiterId) handleAssignWaiter(table.id, table.branchId, waiterId);
                    }}
                    disabled={assignWaiterMutation.isPending}
                    aria-label={`Assign waiter to table ${table.tableNumber}`}
                    className={`${selectClasses} w-full`}
                  >
                    <option value="">
                      {waiterNameByTable.get(table.id) ? `Assigned: ${waiterNameByTable.get(table.id)}` : 'Assign waiter...'}
                    </option>
                    {(waitersQuery.data ?? [])
                      .filter((w) => w.branchId === table.branchId)
                      .map((w) => {
                        const person = userById.get(w.userId);
                        return (
                          <option key={w.id} value={w.id}>
                            {person?.name || `Waiter #${w.id}`}
                          </option>
                        );
                      })}
                  </select>
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>

      <Modal open={showForm} onClose={() => setShowForm(false)} title="Create Table">
        <form onSubmit={handleSubmit} className="space-y-3">
          <p className="-mt-1 text-label-s text-muted">A QR code is generated automatically.</p>
          <EntitySelect
            label="Merchant"
            required
            placeholder="Select merchant"
            value={formData.merchantId}
            onChange={(value) => setFormData({ ...formData, merchantId: value ?? '', branchId: 0 })}
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
            options={formBranchesQuery.data ?? []}
            descriptionKey="address"
            disabled={!formData.merchantId}
            isLoading={!!formData.merchantId && formBranchesQuery.isLoading}
            loadingMessage="Loading branches..."
            emptyMessage="No branches found. Create a branch before adding tables."
          />

          <FormField
            label="Table Number"
            required
            maxLength={20}
            value={formData.tableNumber}
            onChange={(e) => setFormData({ ...formData, tableNumber: e.target.value })}
            placeholder="e.g. 12"
          />

          <FormField
            label="Seats"
            type="number"
            required
            min={1}
            max={50}
            value={formData.capacity}
            onChange={(e) => setFormData({ ...formData, capacity: Number(e.target.value) })}
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
              {createMutation.isPending ? 'Saving...' : 'Create Table'}
            </Button>
          </div>
        </form>
      </Modal>

      <Modal
        open={!!createdTable}
        onClose={() => setCreatedTable(null)}
        title={createdTable ? `Table ${createdTable.tableNumber} created` : ''}
      >
        {createdTable && (
          <div className="flex flex-col items-center gap-3">
            <p className="-mt-2 self-start text-label-s text-muted">Print this code and place it on the table.</p>
            <div className="flex h-48 w-48 items-center justify-center rounded-card border border-line bg-surface shadow-inner">
              {createdTableQr.isLoading ? (
                <Loader2 className="h-6 w-6 animate-spin text-brand-dark" aria-hidden="true" />
              ) : canRenderQr(createdTableQr.data) ? (
                <img
                  src={qrImageSrc(createdTableQr.data)!}
                  alt={`QR code for Table ${createdTable.tableNumber}`}
                  className="h-full w-full rounded-[var(--radius-xl2)] object-contain"
                />
              ) : (
                <div className="flex flex-col items-center gap-1.5 px-3 text-center">
                  <QrCode className="h-6 w-6 text-line-strong" aria-hidden="true" />
                  <span className="text-label-s text-muted">{qrCaption(createdTableQr.data)}</span>
                </div>
              )}
            </div>
            <p className="text-label-m text-ink">{qrCaption(createdTableQr.data)}</p>
            <Button onClick={() => setCreatedTable(null)} fullWidth>Done</Button>
          </div>
        )}
      </Modal>
    </DashboardLayout>
  );
};
