import React, { useState } from 'react';
import { Store, Plus, Edit2, Trash2, X, Loader2 } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { useCreateMerchant, useUpdateMerchant, useDeleteMerchant } from '../hooks/useApiData';
import { merchantApi, MerchantEntity, ApiError } from '../lib/api';
import { useQuery, useQueryClient } from '@tanstack/react-query';

export const MerchantManagement: React.FC = () => {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editingMerchant, setEditingMerchant] = useState<MerchantEntity | null>(null);
  const [formData, setFormData] = useState({
    name: '',
    phone: '',
    city: '',
    address: '',
    category: 'Restaurant',
  });
  const [error, setError] = useState<string | null>(null);

  // Fetch all tables to discover existing merchants
  const { data: tables, isLoading: tablesLoading } = useQuery({
    queryKey: ['tables'],
    queryFn: () => import('../lib/api').then(m => m.tableApi.getAllTables()),
  });

  // Extract unique merchant IDs from tables
  const merchantIds = React.useMemo(() => {
    if (!tables) return [];
    const ids = new Set<string>();
    tables.forEach(t => ids.add(t.merchantId));
    return Array.from(ids);
  }, [tables]);

  // Fetch each merchant by ID
  const { data: merchants = [], isLoading: merchantsLoading, error: merchantsError, refetch: refetchMerchants } = useQuery({
    queryKey: ['merchants', merchantIds],
    queryFn: async () => {
      const results = await Promise.all(
        merchantIds.map(id => merchantApi.getMerchant(id).catch(err => {
          console.error(`[MerchantManagement] Failed to fetch merchant ${id}:`, err);
          return null;
        }))
      );
      const valid = results.filter((m): m is MerchantEntity => m !== null);
      return valid;
    },
    enabled: merchantIds.length > 0,
  });

  const createMutation = useCreateMerchant();
  const updateMutation = useUpdateMerchant();
  const deleteMutation = useDeleteMerchant();


  const handleOpenCreate = () => {
    setEditingMerchant(null);
    setFormData({ name: '', phone: '', city: '', address: '', category: 'Restaurant' });
    setError(null);
    setShowForm(true);
  };

  const handleOpenEdit = (merchant: MerchantEntity) => {
    setEditingMerchant(merchant);
    setFormData({
      name: merchant.name,
      phone: merchant.phone,
      city: merchant.city,
      address: merchant.address,
      category: merchant.category,
    });
    setError(null);
    setShowForm(true);
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this merchant?')) return;
    try {
      await deleteMutation.mutateAsync(id);
      queryClient.invalidateQueries({ queryKey: ['tables'] });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to delete merchant');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      if (editingMerchant) {
        await updateMutation.mutateAsync({ id: editingMerchant.id, data: formData });
      } else {
        await createMutation.mutateAsync(formData);
      }
      setShowForm(false);
      queryClient.invalidateQueries({ queryKey: ['tables'] });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to save merchant');
    }
  };

  const isLoading = createMutation.isPending || updateMutation.isPending || deleteMutation.isPending;

  return (
    <DashboardLayout title="Merchant Management">
      <div className="space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-black text-slate-900 flex items-center gap-2">
              <Store className="w-5 h-5 text-[#E60028]" />
              Merchants
            </h2>
            <p className="text-xs text-slate-500 mt-1">Create and manage merchant tenant accounts</p>
          </div>
          <button
            onClick={handleOpenCreate}
            className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-bold rounded-xl flex items-center gap-2 shadow-sm"
          >
            <Plus className="w-4 h-4" />
            Add Merchant
          </button>
        </div>

        {error && (
          <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700">
            {error}
          </div>
        )}

        {/* Loading */}
        {tablesLoading && <Spinner label="Loading tables..." />}

        {/* Error */}
        {merchantsError && (
          <ErrorState
            message={`Failed to load merchants: ${(merchantsError as Error).message}`}
            onRetry={() => refetchMerchants()}
          />
        )}

        {/* Empty */}
        {!merchantsLoading && !merchantsError && merchants.length === 0 && (
          <EmptyState
            title="No merchants found"
            description="Create your first merchant tenant to get started."
            action={
              <button
                onClick={handleOpenCreate}
                className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-bold rounded-xl flex items-center gap-2"
              >
                <Plus className="w-4 h-4" />
                Add Merchant
              </button>
            }
          />
        )}

        {/* Merchant List */}
        {merchants.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {merchants.map((merchant) => (
              <div
                key={merchant.id}
                className="bg-white rounded-2xl border border-slate-200 p-5 space-y-3 shadow-sm"
              >
                <div className="flex items-start justify-between">
                  <div>
                    <h3 className="font-bold text-sm text-slate-900">{merchant.name}</h3>
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-slate-100 text-slate-600">
                      {merchant.category}
                    </span>
                  </div>
                  <div className="flex gap-1">
                    <button
                      onClick={() => handleOpenEdit(merchant)}
                      className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg"
                    >
                      <Edit2 className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => handleDelete(merchant.id)}
                      disabled={isLoading}
                      className="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
                <div className="space-y-1 text-xs text-slate-500">
                  <p><span className="font-bold">Phone:</span> {merchant.phone}</p>
                  <p><span className="font-bold">City:</span> {merchant.city}</p>
                  <p><span className="font-bold">Address:</span> {merchant.address}</p>
                  <p className="font-mono text-[10px] text-slate-400">ID: {merchant.id}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Create/Edit Modal */}
      {showForm && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md">
            <div className="flex items-center justify-between p-4 border-b border-slate-200">
              <h3 className="font-bold text-sm">
                {editingMerchant ? 'Edit Merchant' : 'Create Merchant'}
              </h3>
              <button
                onClick={() => setShowForm(false)}
                className="p-1 text-slate-400 hover:text-slate-900 rounded-lg"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="p-4 space-y-3">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Name</label>
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
                <label className="block text-xs font-bold text-slate-700 mb-1">City</label>
                <input
                  type="text"
                  required
                  value={formData.city}
                  onChange={(e) => setFormData({ ...formData, city: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Address</label>
                <input
                  type="text"
                  required
                  value={formData.address}
                  onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Category</label>
                <select
                  value={formData.category}
                  onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                >
                  <option value="Restaurant">Restaurant</option>
                  <option value="Coffee Shop">Coffee Shop</option>
                  <option value="Bar">Bar</option>
                  <option value="Hotel">Hotel</option>
                  <option value="Fast Food">Fast Food</option>
                  <option value="Lounge">Lounge</option>
                  <option value="Bakery">Bakery</option>
                </select>
              </div>
              <button
                type="submit"
                disabled={isLoading}
                className="w-full py-2.5 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-60 text-white text-sm font-bold rounded-xl flex items-center justify-center gap-2"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Saving...
                  </>
                ) : (
                  editingMerchant ? 'Update Merchant' : 'Create Merchant'
                )}
              </button>
            </form>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
};