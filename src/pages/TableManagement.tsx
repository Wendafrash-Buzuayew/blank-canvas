import React, { useState } from 'react';
import { Table as TableIcon, Plus, Trash2, X, Loader2 } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { useTables, useCreateTable, useDeleteTable, useUpdateTableStatus } from '../hooks/useApiData';
import { useQuery } from '@tanstack/react-query';
import { tableApi, ApiError } from '../lib/api';

export const TableManagement: React.FC = () => {
  const { data: tablesData, isLoading, error, refetch } = useTables();
  const createMutation = useCreateTable();
  const deleteMutation = useDeleteTable();
  const updateStatusMutation = useUpdateTableStatus();
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({ branchId: 0, tableNumber: '', capacity: 4 });
  const [error2, setError] = useState<string | null>(null);

  const { data: tables } = useQuery({
    queryKey: ['tables'],
    queryFn: () => tableApi.getAllTables(),
  });

  const branchIds = React.useMemo(() => {
    if (!tables) return [];
    const ids = new Set<number>();
    tables.forEach(t => ids.add(t.branchId));
    return Array.from(ids);
  }, [tables]);

  const tables_ = tablesData || [];
  console.log('[TableManagement] tables:', tables_);

  const handleOpenCreate = () => {
    setFormData({ branchId: branchIds[0] || 0, tableNumber: '', capacity: 4 });
    setError(null);
    setShowForm(true);
  };

  const handleDelete = async (id: number) => {
    if (!confirm('Delete this table?')) return;
    try {
      await deleteMutation.mutateAsync(id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to delete table');
    }
  };

  const handleStatusChange = async (id: number, status: string) => {
    try {
      await updateStatusMutation.mutateAsync({ id, status });
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
      setError(err instanceof ApiError ? err.message : 'Failed to create table');
    }
  };

  const isLoading2 = createMutation.isPending || deleteMutation.isPending || updateStatusMutation.isPending;

  return (
    <DashboardLayout title="Table Management">
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-black text-slate-900 flex items-center gap-2">
              <TableIcon className="w-5 h-5 text-[#E60028]" />
              Tables
            </h2>
            <p className="text-xs text-slate-500 mt-1">Manage restaurant tables and QR codes</p>
          </div>
          <button
            onClick={handleOpenCreate}
            disabled={branchIds.length === 0}
            className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-50 text-white text-xs font-bold rounded-xl flex items-center gap-2 shadow-sm"
          >
            <Plus className="w-4 h-4" />
            Add Table
          </button>
        </div>

        {error2 && (
          <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700">
            {error2}
          </div>
        )}

        {isLoading && <Spinner label="Loading tables..." />}

        {error && (
          <ErrorState message={`Failed to load tables: ${(error as Error).message}`} onRetry={() => refetch()} />
        )}

        {!isLoading && !error && tables_.length === 0 && (
          <EmptyState title="No tables found" description="Create a table to generate QR codes." />
        )}

        {tables_.length > 0 && (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {tables_.map((table) => (
              <div key={table.id} className="bg-white rounded-2xl border border-slate-200 p-4 space-y-3 shadow-sm">
                <div className="flex items-start justify-between">
                  <div>
                    <h3 className="font-bold text-sm text-slate-900">{table.tableNumber}</h3>
                    <p className="text-xs text-slate-500">Capacity: {table.capacity}</p>
                  </div>
                  <button
                    onClick={() => handleDelete(table.id)}
                    disabled={isLoading2}
                    className="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
                <div className="flex items-center gap-2">
                  <select
                    value={table.status}
                    onChange={(e) => handleStatusChange(table.id, e.target.value)}
                    disabled={isLoading2}
                    className="flex-1 px-2 py-1.5 text-xs rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20"
                  >
                    <option value="AVAILABLE">Available</option>
                    <option value="OCCUPIED">Occupied</option>
                    <option value="RESERVED">Reserved</option>
                  </select>
                </div>
                <p className="text-[10px] text-slate-400 font-mono">ID: {table.id} | Branch: {table.branchId}</p>
              </div>
            ))}
          </div>
        )}
      </div>

      {showForm && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md">
            <div className="flex items-center justify-between p-4 border-b border-slate-200">
              <h3 className="font-bold text-sm">Create Table</h3>
              <button onClick={() => setShowForm(false)} className="p-1 text-slate-400 hover:text-slate-900 rounded-lg">
                <X className="w-4 h-4" />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="p-4 space-y-3">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Branch</label>
                <select
                  required
                  value={formData.branchId}
                  onChange={(e) => setFormData({ ...formData, branchId: Number(e.target.value) })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                >
                  <option value={0}>Select branch...</option>
                  {branchIds.map(id => (
                    <option key={id} value={id}>Branch {id}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Table Number</label>
                <input
                  type="text"
                  required
                  value={formData.tableNumber}
                  onChange={(e) => setFormData({ ...formData, tableNumber: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Capacity</label>
                <input
                  type="number"
                  required
                  min={1}
                  value={formData.capacity}
                  onChange={(e) => setFormData({ ...formData, capacity: Number(e.target.value) })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>
              <button
                type="submit"
                disabled={isLoading2}
                className="w-full py-2.5 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-60 text-white text-sm font-bold rounded-xl flex items-center justify-center gap-2"
              >
                {isLoading2 ? <><Loader2 className="w-4 h-4 animate-spin" /> Saving...</> : 'Create Table'}
              </button>
            </form>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
};