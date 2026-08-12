import React, { useMemo, useState } from 'react';
import { Table as TableIcon, Plus, Trash2, X, Loader2, Search, QrCode } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { EntitySelect } from '../components/ui/EntitySelect';
import { useTables, useCreateTable, useDeleteTable, useUpdateTableStatus, useAssignWaiterV1 } from '../hooks/useApiData';
import { useBranchesLookup, useMerchantsLookup, useUsersLookup, useWaitersLookup } from '../hooks/useLookups';
import { friendlyError } from '../lib/errors';
import { isAuthenticated, tableAssignmentApi, TableAssignmentEntity } from '../lib/api';

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

  const tables = tablesQuery.data ?? [];
  const merchants = merchantsQuery.data ?? [];
  const allBranches = allBranchesQuery.data ?? [];
  const formBranchesQuery = useBranchesLookup(formData.merchantId || null);

  const merchantIds = useMemo(() => merchants.map((m) => m.id).sort(), [merchants]);

  // Active waiter assignments — fetched once per merchant, never per row.
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
      await createMutation.mutateAsync({
        branchId: formData.branchId,
        tableNumber: formData.tableNumber.trim(),
        capacity: formData.capacity,
      });
      setShowForm(false);
    } catch (err) {
      setFormError(friendlyError(err, 'We could not create this table.'));
    }
  };

  const isBusy = deleteMutation.isPending || updateStatusMutation.isPending;

  return (
    <DashboardLayout title="Table Management">
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <h2 className="text-xl font-black text-slate-900 flex items-center gap-2">
              <TableIcon className="w-5 h-5 text-[#E60028]" />
              Tables
            </h2>
            <p className="text-xs text-slate-500 mt-1">Manage tables, seating and QR codes</p>
          </div>
          <button
            onClick={openCreate}
            disabled={merchants.length === 0}
            className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-50 text-white text-xs font-bold rounded-xl flex items-center justify-center gap-2 shadow-sm"
          >
            <Plus className="w-4 h-4" />
            Add Table
          </button>
        </div>

        {tables.length > 0 && (
          <div className="flex flex-col sm:flex-row gap-3">
            <div className="relative flex-1">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search tables or branches..."
                aria-label="Search tables"
                className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-slate-300 bg-white focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
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
          <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700">
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
            action={
              merchants.length > 0 ? (
                <button
                  onClick={openCreate}
                  className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-bold rounded-xl"
                >
                  Add Table
                </button>
              ) : undefined
            }
          />
        )}

        {tables.length > 0 && visibleTables.length === 0 && (
          <EmptyState title="No matching tables" description="Try a different search term or branch filter." />
        )}

        {visibleTables.length > 0 && (
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
            {visibleTables.map((table) => (
              <div key={table.id} className="bg-white rounded-2xl border border-slate-200 p-4 space-y-3 shadow-sm">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <h3 className="font-bold text-sm text-slate-900 truncate">Table {table.tableNumber}</h3>
                    <p className="text-xs text-slate-500 truncate">
                      {branchNameById.get(table.branchId) ?? (allBranchesQuery.isLoading ? 'Loading…' : '—')}
                    </p>
                  </div>
                  <button
                    onClick={() => handleDelete(table.id, table.tableNumber)}
                    disabled={isBusy}
                    aria-label={`Delete table ${table.tableNumber}`}
                    className="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg shrink-0"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>

                <dl className="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <dt className="text-slate-400">Seats</dt>
                    <dd className="font-bold text-slate-700">{table.capacity}</dd>
                  </div>
                  <div className="min-w-0">
                    <dt className="text-slate-400">Waiter</dt>
                    <dd className="font-bold text-slate-700 truncate">
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
                    className="flex-1 px-2 py-1.5 text-xs rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20"
                  >
                    <option value="AVAILABLE">Available</option>
                    <option value="OCCUPIED">Occupied</option>
                    <option value="RESERVED">Reserved</option>
                  </select>
                  <span
                    className={`inline-flex items-center gap-1 text-[10px] font-bold px-2 py-1.5 rounded-lg ${
                      table.qrToken ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'
                    }`}
                  >
                    <QrCode className="w-3 h-3" />
                    {table.qrToken ? 'QR Active' : 'No QR'}
                  </span>
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
                    className="w-full px-2 py-1.5 text-xs rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20"
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
              </div>
            ))}
          </div>
        )}
      </div>

      {showForm && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4">
          <div className="bg-white rounded-t-2xl sm:rounded-2xl shadow-xl w-full sm:max-w-md max-h-[90vh] overflow-y-auto">
            <div className="flex items-start justify-between p-4 border-b border-slate-200">
              <div>
                <h3 className="font-bold text-sm">Create Table</h3>
                <p className="text-[11px] text-slate-500 mt-0.5">A QR code is generated automatically.</p>
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

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Table Number *</label>
                <input
                  type="text"
                  required
                  maxLength={20}
                  value={formData.tableNumber}
                  onChange={(e) => setFormData({ ...formData, tableNumber: e.target.value })}
                  placeholder="e.g. 12"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Seats *</label>
                <input
                  type="number"
                  required
                  min={1}
                  max={50}
                  value={formData.capacity}
                  onChange={(e) => setFormData({ ...formData, capacity: Number(e.target.value) })}
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
                  disabled={createMutation.isPending}
                  className="flex-1 py-2.5 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-60 text-white text-sm font-bold rounded-xl flex items-center justify-center gap-2"
                >
                  {createMutation.isPending ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" /> Saving...
                    </>
                  ) : (
                    'Create Table'
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
