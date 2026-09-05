import React, { useState } from 'react';
import { Store, Plus, Edit2, Trash2, Loader2 } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { FormField } from '../components/ui/FormField';
import { IdentityChip } from '../components/ui/Chip';
import { useCreateMerchant, useUpdateMerchant, useDeleteMerchant, useUploadMerchantLogo } from '../hooks/useApiData';
import { merchantApi, resolveMediaUrl, MerchantEntity, ApiError } from '../lib/api';
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
  const [logoUrl, setLogoUrl] = useState<string | undefined>(undefined);

  const { data: merchants = [], isLoading: merchantsLoading, error: merchantsError, refetch: refetchMerchants } = useQuery({
    queryKey: ['merchants', 'all'],
    queryFn: () => merchantApi.getAllMerchants(),
  });

  const createMutation = useCreateMerchant();
  const updateMutation = useUpdateMerchant();
  const deleteMutation = useDeleteMerchant();
  const uploadLogoMutation = useUploadMerchantLogo();


  const handleOpenCreate = () => {
    setEditingMerchant(null);
    setFormData({ name: '', phone: '', city: '', address: '', category: 'Restaurant' });
    setLogoUrl(undefined);
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
    setLogoUrl(merchant.logoUrl);
    setError(null);
    setShowForm(true);
  };

  const handleUploadLogo = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file || !editingMerchant) return;
    setError(null);
    try {
      const updated = await uploadLogoMutation.mutateAsync({ id: editingMerchant.id, file });
      setLogoUrl(updated.logoUrl);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to upload logo');
    }
  };

  const handleDelete = async (merchant: MerchantEntity) => {
    if (!confirm(`Delete "${merchant.name}"? This cannot be undone.`)) return;
    try {
      await deleteMutation.mutateAsync(merchant.id);
      queryClient.invalidateQueries({ queryKey: ['merchants', 'all'] });
      queryClient.invalidateQueries({ queryKey: ['lookup', 'merchants'] });
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
      queryClient.invalidateQueries({ queryKey: ['merchants', 'all'] });
      queryClient.invalidateQueries({ queryKey: ['lookup', 'merchants'] });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to save merchant');
    }
  };

  const isSaving = createMutation.isPending || updateMutation.isPending;
  const isBusy = isSaving || deleteMutation.isPending;

  return (
    <DashboardLayout title="Merchant Management">
      {/* DESIGN.md 5.4 frame C: 1280 max-width for tables/dashboards. */}
      <div className="mx-auto max-w-[80rem] space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="flex items-center gap-2 text-title-m text-ink">
              <Store className="h-5 w-5 text-brand-press" aria-hidden="true" />
              Merchants
            </h2>
            <p className="mt-1 text-body-m text-muted">Create and manage merchant tenant accounts</p>
          </div>
          <Button onClick={handleOpenCreate}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Add Merchant
          </Button>
        </div>

        {error && (
          <div role="alert" className="rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink">
            {error}
          </div>
        )}

        {merchantsLoading && <Spinner label="Loading merchants..." />}

        {merchantsError && (
          <ErrorState
            message={`Failed to load merchants: ${(merchantsError as Error).message}`}
            onRetry={() => refetchMerchants()}
          />
        )}

        {!merchantsLoading && !merchantsError && merchants.length === 0 && (
          <EmptyState
            title="No merchants found"
            description="Create your first merchant tenant to get started."
            action={
              <Button onClick={handleOpenCreate}>
                <Plus className="h-4 w-4" aria-hidden="true" />
                Add Merchant
              </Button>
            }
          />
        )}

        {merchants.length > 0 && (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            {merchants.map((merchant) => (
              <Card key={merchant.id} compact className="space-y-3">
                <div className="flex items-start justify-between">
                  <div>
                    <h3 className="text-label-m text-ink">{merchant.name}</h3>
                    <IdentityChip className="mt-1">{merchant.category}</IdentityChip>
                  </div>
                  <div className="flex gap-1">
                    <button
                      onClick={() => handleOpenEdit(merchant)}
                      aria-label={`Edit ${merchant.name}`}
                      className="rounded-control p-1.5 text-muted hover:bg-info-soft hover:text-info"
                    >
                      <Edit2 className="h-3.5 w-3.5" aria-hidden="true" />
                    </button>
                    <button
                      onClick={() => handleDelete(merchant)}
                      disabled={isBusy}
                      aria-label={`Delete ${merchant.name}`}
                      className="rounded-control p-1.5 text-muted hover:bg-danger-soft hover:text-danger"
                    >
                      <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                    </button>
                  </div>
                </div>
                <div className="space-y-1 text-body-m text-muted">
                  <p><span className="text-ink">Phone:</span> {merchant.phone}</p>
                  <p><span className="text-ink">City:</span> {merchant.city}</p>
                  <p><span className="text-ink">Address:</span> {merchant.address}</p>
                  <p className="font-mono text-label-s text-muted">ID: {merchant.id}</p>
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>

      <Modal
        open={showForm}
        onClose={() => setShowForm(false)}
        title={editingMerchant ? 'Edit Merchant' : 'Create Merchant'}
      >
        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <span className="mb-1 block text-label-m text-ink">Logo</span>
            {editingMerchant ? (
              <div className="flex items-center gap-3">
                {logoUrl ? (
                  <img
                    src={resolveMediaUrl(logoUrl)}
                    alt="Merchant logo"
                    className="h-12 w-12 rounded-control border border-line object-cover"
                  />
                ) : (
                  <div className="h-12 w-12 rounded-control border border-line bg-surface-2" />
                )}
                <label
                  className={`flex cursor-pointer items-center gap-1.5 rounded-control border border-line-strong bg-surface-2 px-3 py-2 text-label-s text-ink hover:bg-line ${uploadLogoMutation.isPending ? 'pointer-events-none opacity-50' : ''}`}
                >
                  {uploadLogoMutation.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />}
                  Upload Logo
                  <input type="file" accept="image/jpeg,image/png,image/webp" onChange={handleUploadLogo} className="hidden" />
                </label>
              </div>
            ) : (
              <p className="text-label-s text-muted">Create the merchant first, then edit it to upload a logo.</p>
            )}
          </div>
          <FormField
            label="Name"
            required
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          />
          <FormField
            label="Phone"
            type="tel"
            required
            value={formData.phone}
            onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
          />
          <FormField
            label="City"
            required
            value={formData.city}
            onChange={(e) => setFormData({ ...formData, city: e.target.value })}
          />
          <FormField
            label="Address"
            required
            value={formData.address}
            onChange={(e) => setFormData({ ...formData, address: e.target.value })}
          />
          <div>
            <label htmlFor="merchant-category" className="mb-1 block text-label-m text-ink">Category</label>
            <select
              id="merchant-category"
              value={formData.category}
              onChange={(e) => setFormData({ ...formData, category: e.target.value })}
              className="h-11 w-full rounded-control border border-line bg-surface px-3 text-body-m text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark"
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
          <Button type="submit" loading={isSaving} fullWidth>
            {isSaving ? 'Saving...' : editingMerchant ? 'Update Merchant' : 'Create Merchant'}
          </Button>
        </form>
      </Modal>
    </DashboardLayout>
  );
};
