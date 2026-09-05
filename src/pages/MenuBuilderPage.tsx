import React, { useEffect, useMemo, useState } from 'react';
import { Plus, Trash2, Edit3, Utensils, FolderPlus, Search, Loader2, Eye, Image as ImageIcon, Clock, AlertCircle, Store, Palette } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { FormField } from '../components/ui/FormField';
import { IdentityChip, StatusChip } from '../components/ui/Chip';
import { useAuth } from '../context/AuthContext';
import { useBranchMenu, useCreateCategory, useUpdateCategory, useDeleteCategory, useCreateProduct, useUpdateProduct, useDeleteProduct, useSetMenuTemplate, useUploadProductImage, useMenuTemplateDefinitions } from '../hooks/useApiData';
import { useBranchesLookup, useMerchantsLookup } from '../hooks/useLookups';
import { friendlyError } from '../lib/errors';
import { useNavigate } from 'react-router-dom';
import { resolveMediaUrl, type MenuResponse, type MenuTemplateStyle } from '../lib/api';
import { resolveTemplateClasses } from '../lib/menuTemplates';

const FOOD_IMAGE_PRESETS = [
  { label: 'Cappuccino', url: 'https://images.unsplash.com/photo-1534778101976-62847782c213?w=600&auto=format&fit=crop&q=80' },
  { label: 'Matcha Tea', url: 'https://images.unsplash.com/photo-1536256263959-770b48d82b0a?w=600&auto=format&fit=crop&q=80' },
  { label: 'Avocado Toast', url: 'https://images.unsplash.com/photo-1525351484163-7529414344d8?w=600&auto=format&fit=crop&q=80' },
  { label: 'Eggs Benedict', url: 'https://images.unsplash.com/photo-1608039829572-78524f79c4c7?w=600&auto=format&fit=crop&q=80' },
  { label: 'Gourmet Burger', url: 'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=600&auto=format&fit=crop&q=80' },
  { label: 'Woodfired Pizza', url: 'https://images.unsplash.com/photo-1604382354936-07c5d9983bd3?w=600&auto=format&fit=crop&q=80' },
  { label: 'Croissant', url: 'https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=600&auto=format&fit=crop&q=80' },
  { label: 'Tiramisu', url: 'https://images.unsplash.com/photo-1571877227200-a0d98ea607e9?w=600&auto=format&fit=crop&q=80' },
];

interface CategoryFormState { id?: number; name: string; displayOrder?: number; }
interface ProductFormState {
  id?: number; categoryId: number; name: string; description: string; price: number;
  discountPrice?: number; discountStartAt?: string; discountEndAt?: string;
  image?: string; available: boolean; preparationTime: number;
}

/** datetime-local inputs use "YYYY-MM-DDTHH:mm[:ss]" with no timezone - matches LocalDateTime's JSON shape exactly, so no conversion is needed either direction; this just trims to minute precision for the input's own display. */
function toDatetimeLocal(iso: string | null | undefined): string {
  return iso ? iso.slice(0, 16) : '';
}

const controlClasses = 'w-full rounded-control border border-line bg-surface p-2.5 text-body-m text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark';
const labelClasses = 'mb-1 block text-label-s uppercase text-muted';

export const MenuBuilderPage: React.FC = () => {
  const { user } = useAuth();
  const merchantId = user?.merchantId;
  const navigate = useNavigate();
  const merchantsQuery = useMerchantsLookup();
  const branchesQuery = useBranchesLookup();
  const createCategory = useCreateCategory();
  const updateCategory = useUpdateCategory();
  const deleteCategory = useDeleteCategory();
  const createProduct = useCreateProduct();
  const updateProduct = useUpdateProduct();
  const deleteProduct = useDeleteProduct();
  const setMenuTemplate = useSetMenuTemplate();
  const templatesQuery = useMenuTemplateDefinitions();
  const uploadProductImage = useUploadProductImage();

  const [activeCategoryId, setActiveCategoryId] = useState<number | 'all'>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [pageError, setPageError] = useState<string | null>(null);
  const [categoryModalOpen, setCategoryModalOpen] = useState(false);
  const [categoryForm, setCategoryForm] = useState<CategoryFormState>({ name: '' });
  const [productModalOpen, setProductModalOpen] = useState(false);
  const [productForm, setProductForm] = useState<ProductFormState>({ categoryId: 0, name: '', description: '', price: 9.99, image: FOOD_IMAGE_PRESETS[0].url, available: true, preparationTime: 10 });

  // Each branch has its own independent menu (categories/products may differ
  // per branch), so the builder edits one branch's catalog at a time.
  const [selectedBranchId, setSelectedBranchId] = useState<number | null>(null);
  useEffect(() => {
    if (selectedBranchId == null && branchesQuery.data && branchesQuery.data.length > 0) {
      setSelectedBranchId(branchesQuery.data[0].id);
    }
  }, [branchesQuery.data, selectedBranchId]);
  const selectedBranch = branchesQuery.data?.find((b) => b.id === selectedBranchId);

  const { data: menu, isLoading, error, refetch } = useBranchMenu(selectedBranchId ?? undefined);

  const merchant = merchantsQuery.data?.find((m) => m.id === merchantId);
  const merchantSlug = merchant?.slug || 'demo';
  const categories = menu?.categories || [];

  const allProducts = useMemo(() => {
    const items: (MenuResponse['categories'][number]['items'][number] & { categoryId: number; categoryName: string })[] = [];
    categories.forEach((cat) => cat.items.forEach((item) => items.push({ ...item, categoryId: cat.id, categoryName: cat.name })));
    return items;
  }, [categories]);

  const filteredProducts = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return allProducts.filter((p) => {
      const matchesCat = activeCategoryId === 'all' || p.categoryId === activeCategoryId;
      const matchesSearch = !q || p.name.toLowerCase().includes(q) || p.description.toLowerCase().includes(q);
      return matchesCat && matchesSearch;
    });
  }, [allProducts, activeCategoryId, searchQuery]);

  const openCategoryModal = (cat?: MenuResponse['categories'][number]) => {
    setCategoryForm(cat ? { id: cat.id, name: cat.name } : { name: '' });
    setCategoryModalOpen(true);
  };

  const handleSaveCategory = async (e: React.FormEvent) => {
    e.preventDefault();
    setPageError(null);
    if (!categoryForm.name.trim() || !merchantId) return;
    try {
      if (categoryForm.id) {
        await updateCategory.mutateAsync({ id: categoryForm.id, data: { name: categoryForm.name.trim(), displayOrder: categoryForm.displayOrder } });
      } else {
        // Categories are branch-scoped (backend requires branchId) - attach
        // to whichever branch is currently selected in the picker. A
        // merchant with zero branches has nothing to attach a category to;
        // the "Add Category" button is disabled in that case (see
        // !selectedBranch below).
        if (!selectedBranch) { setPageError('Create a branch first - categories belong to a branch.'); return; }
        await createCategory.mutateAsync({ merchantId, branchId: selectedBranch.id, name: categoryForm.name.trim(), displayOrder: categoryForm.displayOrder });
      }
      setCategoryModalOpen(false);
    } catch (err) { setPageError(friendlyError(err, 'Could not save category.')); }
  };

  const handleDeleteCategory = async (cat: MenuResponse['categories'][number]) => {
    if (!confirm(`Delete category "${cat.name}"? All products in it will be removed.`)) return;
    setPageError(null);
    try {
      await deleteCategory.mutateAsync(cat.id);
      if (activeCategoryId === cat.id) setActiveCategoryId('all');
    } catch (err) { setPageError(friendlyError(err, 'Could not delete category.')); }
  };

  const openProductModal = (product?: MenuResponse['categories'][number]['items'][number] & { categoryId: number }) => {
    setProductForm(product ? {
      id: product.id, categoryId: product.categoryId, name: product.name, description: product.description || '',
      price: Number(product.price), discountPrice: product.discountPrice ?? undefined,
      discountStartAt: toDatetimeLocal(product.discountStartAt), discountEndAt: toDatetimeLocal(product.discountEndAt),
      image: product.image || FOOD_IMAGE_PRESETS[0].url, available: product.available, preparationTime: product.preparationTime || 10,
    } : {
      categoryId: activeCategoryId !== 'all' ? activeCategoryId : categories[0]?.id || 0, name: '', description: '',
      price: 9.99, image: FOOD_IMAGE_PRESETS[0].url, available: true, preparationTime: 10,
    });
    setProductModalOpen(true);
  };

  const handleSaveProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    setPageError(null);
    if (!productForm.name.trim() || !productForm.categoryId) return;
    if (productForm.discountPrice != null && productForm.discountPrice >= productForm.price) {
      setPageError('Discount price must be less than the regular price.');
      return;
    }
    const discountFields = {
      discountPrice: productForm.discountPrice,
      discountStartAt: productForm.discountStartAt || undefined,
      discountEndAt: productForm.discountEndAt || undefined,
    };
    try {
      if (productForm.id) {
        const clearDiscount = productForm.discountPrice == null;
        await updateProduct.mutateAsync({
          id: productForm.id,
          data: {
            name: productForm.name.trim(), description: productForm.description, price: Number(productForm.price),
            image: productForm.image, available: productForm.available, preparationTime: Number(productForm.preparationTime),
            ...(clearDiscount ? { clearDiscount: true } : discountFields),
          },
        });
      } else {
        await createProduct.mutateAsync({ categoryId: productForm.categoryId, name: productForm.name.trim(), description: productForm.description, price: Number(productForm.price), image: productForm.image, preparationTime: Number(productForm.preparationTime), ...discountFields });
      }
      setProductModalOpen(false);
    } catch (err) { setPageError(friendlyError(err, 'Could not save product.')); }
  };

  const handleToggleAvailability = async (product: MenuResponse['categories'][number]['items'][number]) => {
    setPageError(null);
    try { await updateProduct.mutateAsync({ id: product.id, data: { available: !product.available } }); }
    catch (err) { setPageError(friendlyError(err, 'Could not update availability.')); }
  };

  const handleDeleteProduct = async (product: MenuResponse['categories'][number]['items'][number]) => {
    if (!confirm(`Delete "${product.name}"?`)) return;
    setPageError(null);
    try { await deleteProduct.mutateAsync(product.id); }
    catch (err) { setPageError(friendlyError(err, 'Could not delete product.')); }
  };

  const handlePreviewMenu = () => {
    if (!merchantSlug || !selectedBranch) return;
    navigate(`/m/${merchantSlug}/${selectedBranch.slug}`);
  };

  const handleBranchChange = (branchId: number) => {
    setSelectedBranchId(branchId);
    setActiveCategoryId('all');
    setSearchQuery('');
  };

  const handleUploadImage = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // allow re-selecting the same file later
    if (!file || !productForm.id) return;
    setPageError(null);
    try {
      const updated = await uploadProductImage.mutateAsync({ id: productForm.id, file });
      setProductForm({ ...productForm, image: updated.image });
    } catch (err) { setPageError(friendlyError(err, 'Could not upload the image.')); }
  };

  const handleSelectTemplate = async (templateStyle: MenuTemplateStyle) => {
    if (!selectedBranchId) return;
    setPageError(null);
    try {
      await setMenuTemplate.mutateAsync({ branchId: selectedBranchId, templateStyle });
    } catch (err) { setPageError(friendlyError(err, 'Could not update the menu template.')); }
  };

  return (
    <DashboardLayout title="Menu Builder">
      <div className="mx-auto max-w-[80rem] space-y-6">
        <Card compact className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <h2 className="flex items-center gap-2 text-title-m text-ink">
              <Utensils className="h-6 w-6 text-brand-press" aria-hidden="true" /> Digital Menu Builder
            </h2>
            <p className="mt-0.5 text-body-m text-muted">Build your menu by category and product - customers see it instantly after scanning the QR code.</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {branchesQuery.data && branchesQuery.data.length > 0 && (
              <div className="relative">
                <Store className="pointer-events-none absolute left-2.5 top-2.5 h-3.5 w-3.5 text-muted" aria-hidden="true" />
                <select
                  aria-label="Branch"
                  value={selectedBranchId ?? ''}
                  onChange={(e) => handleBranchChange(Number(e.target.value))}
                  className="rounded-control border border-line bg-surface-2 py-2 pl-8 pr-3 text-label-s text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark"
                >
                  {branchesQuery.data.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
                </select>
              </div>
            )}
            <Button variant="secondary" onClick={handlePreviewMenu} disabled={!merchantSlug || !selectedBranch} title={!selectedBranch ? 'Create a branch first' : undefined}>
              <Eye className="h-4 w-4" aria-hidden="true" /> Preview Customer Menu
            </Button>
            <Button variant="secondary" onClick={() => openCategoryModal()} disabled={!selectedBranch} title={!selectedBranch ? 'Create a branch first' : undefined}>
              <FolderPlus className="h-4 w-4" aria-hidden="true" /> Add Category
            </Button>
            <Button onClick={() => openProductModal()} disabled={categories.length === 0}>
              <Plus className="h-4 w-4" aria-hidden="true" /> Add Menu Item
            </Button>
          </div>
        </Card>

        {selectedBranch && (
          <Card compact className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <span className="flex shrink-0 items-center gap-1.5 text-label-s uppercase text-ink">
              <Palette className="h-4 w-4 text-brand-press" aria-hidden="true" /> Digital Menu Look
            </span>
            <div className="flex items-center gap-2">
              {(templatesQuery.data ?? []).map((def) => {
                const isActive = (menu?.templateStyle ?? 'CLASSIC') === def.key;
                const swatch = resolveTemplateClasses(def).swatch;
                return (
                  <button
                    key={def.key}
                    onClick={() => handleSelectTemplate(def.key as MenuTemplateStyle)}
                    disabled={setMenuTemplate.isPending}
                    title={def.displayName}
                    className={`flex items-center gap-1.5 rounded-control border-2 px-3 py-1.5 text-label-s transition-all disabled:opacity-50 ${isActive ? 'border-brand-dark bg-brand-soft text-brand-press' : 'border-transparent bg-surface-2 text-muted hover:bg-line'}`}
                  >
                    <span className={swatch} aria-hidden="true" />
                    {def.displayName}
                  </button>
                );
              })}
            </div>
          </Card>
        )}

        {!isLoading && !branchesQuery.isLoading && branchesQuery.data?.length === 0 && (
          <div role="alert" className="flex items-center gap-2 rounded-control bg-warn-soft px-3 py-3 text-label-s text-ink">
            <AlertCircle className="h-4 w-4 text-warn" aria-hidden="true" /> Create a branch before building a menu - each branch has its own independent catalog.
          </div>
        )}

        {pageError && (
          <div role="alert" className="flex items-center gap-2 rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink">
            <AlertCircle className="h-4 w-4 text-danger" aria-hidden="true" /> {pageError}
          </div>
        )}

        {isLoading && <Spinner label="Loading menu..." />}
        {!isLoading && error && <ErrorState message={friendlyError(error, 'Could not load the menu.')} onRetry={() => refetch()} />}

        {!isLoading && !error && (
          <>
            <Card compact className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
              <div className="no-scrollbar flex items-center gap-2 overflow-x-auto text-label-s">
                <button onClick={() => setActiveCategoryId('all')} className={`whitespace-nowrap rounded-control px-3.5 py-1.5 transition-all ${activeCategoryId === 'all' ? 'border border-brand-dark/30 bg-brand-soft text-brand-press' : 'text-muted hover:text-ink'}`}>
                  All Items ({allProducts.length})
                </button>
                {categories.map((cat) => (
                  <div key={cat.id} className="flex items-center gap-1">
                    <button onClick={() => setActiveCategoryId(cat.id)} className={`whitespace-nowrap rounded-control px-3.5 py-1.5 transition-all ${activeCategoryId === cat.id ? 'border border-brand-dark/30 bg-brand-soft text-brand-press' : 'text-muted hover:text-ink'}`}>
                      {cat.name} ({cat.items.length})
                    </button>
                    <button onClick={() => openCategoryModal(cat)} aria-label={`Edit category ${cat.name}`} className="p-1 text-muted hover:text-ink"><Edit3 className="h-3 w-3" aria-hidden="true" /></button>
                    <button onClick={() => handleDeleteCategory(cat)} aria-label={`Delete category ${cat.name}`} className="p-1 text-muted hover:text-danger"><Trash2 className="h-3 w-3" aria-hidden="true" /></button>
                  </div>
                ))}
              </div>
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-2.5 h-3.5 w-3.5 text-muted" aria-hidden="true" />
                <input
                  type="text"
                  aria-label="Search products"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search products..."
                  className="rounded-control border border-line bg-surface-2 py-1.5 pl-8 pr-3 text-label-s text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark"
                />
              </div>
            </Card>

            {categories.length === 0 && (
              <EmptyState title="No menu categories yet" description="Create your first category (e.g. Starters, Mains, Drinks) then add menu items to it."
                action={<Button onClick={() => openCategoryModal()}>Add Category</Button>} />
            )}

            {categories.length > 0 && (
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
                {filteredProducts.length === 0 && (
                  <Card className="col-span-full space-y-2 p-12 text-center">
                    <Utensils className="mx-auto h-12 w-12 stroke-1 text-line-strong" aria-hidden="true" />
                    <p className="text-label-m text-ink">No menu items found.</p>
                    <Button variant="link" onClick={() => openProductModal()}>+ Add your first menu item</Button>
                  </Card>
                )}
                {filteredProducts.map((product) => (
                  <Card key={product.id} compact className={`flex flex-col justify-between space-y-3 transition-shadow hover:shadow-[var(--shadow-lift)] ${!product.available ? 'bg-surface-2 opacity-70' : ''}`}>
                    <div className="flex gap-3">
                      {product.image ? (
                        <img src={resolveMediaUrl(product.image)} alt={product.name} className="h-20 w-20 shrink-0 rounded-[var(--radius-xl2)] object-cover" />
                      ) : (
                        <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-[var(--radius-xl2)] bg-surface-2 text-muted">
                          <ImageIcon className="h-6 w-6" aria-hidden="true" />
                        </div>
                      )}
                      <div className="min-w-0 flex-1">
                        <div className="mb-1 flex items-center justify-between gap-1">
                          <IdentityChip className="truncate">{product.categoryName}</IdentityChip>
                          <button
                            onClick={() => handleToggleAvailability(product)}
                            className="shrink-0"
                            aria-label={product.available ? `Mark ${product.name} out of stock` : `Mark ${product.name} in stock`}
                          >
                            <StatusChip status={product.available ? 'success' : 'danger'}>
                              {product.available ? 'In Stock' : 'Out of Stock'}
                            </StatusChip>
                          </button>
                        </div>
                        <h3 className="truncate text-label-m text-ink">{product.name}</h3>
                        <p className="mt-0.5 line-clamp-2 text-body-m text-muted">{product.description}</p>
                      </div>
                    </div>
                    <div className="flex items-center justify-between border-t border-line pt-2 text-body-m">
                      <div className="flex items-center gap-2">
                        {product.effectivePrice < product.price ? (
                          <span className="flex items-baseline gap-1.5">
                            <span className="text-label-m text-success [font-variant-numeric:tabular-nums]">{Number(product.effectivePrice).toLocaleString()} ETB</span>
                            <span className="text-label-s text-muted line-through [font-variant-numeric:tabular-nums]">{Number(product.price).toLocaleString()} ETB</span>
                          </span>
                        ) : (
                          <span className="text-label-m text-ink [font-variant-numeric:tabular-nums]">{Number(product.price).toLocaleString()} ETB</span>
                        )}
                        <span className="flex items-center gap-0.5 text-label-s text-muted"><Clock className="h-3 w-3" aria-hidden="true" />{product.preparationTime} min</span>
                      </div>
                      <div className="flex items-center gap-1">
                        <button onClick={() => openProductModal(product)} aria-label={`Edit ${product.name}`} className="rounded-control p-1.5 text-muted transition-colors hover:bg-info-soft hover:text-info"><Edit3 className="h-3.5 w-3.5" aria-hidden="true" /></button>
                        <button onClick={() => handleDeleteProduct(product)} aria-label={`Delete ${product.name}`} className="rounded-control p-1.5 text-danger/70 transition-colors hover:bg-danger-soft hover:text-danger"><Trash2 className="h-3.5 w-3.5" aria-hidden="true" /></button>
                      </div>
                    </div>
                  </Card>
                ))}
              </div>
            )}
          </>
        )}
      </div>

      <Modal
        open={categoryModalOpen}
        onClose={() => setCategoryModalOpen(false)}
        title={categoryForm.id ? 'Edit Category' : 'Create New Category'}
      >
        <form onSubmit={handleSaveCategory} className="space-y-4">
          <FormField
            label="Category Name"
            required
            value={categoryForm.name}
            onChange={(e) => setCategoryForm({ ...categoryForm, name: e.target.value })}
            placeholder="e.g. Artisanal Pizza"
          />
          <FormField
            label="Display Order"
            type="number"
            min={1}
            value={categoryForm.displayOrder || 1}
            onChange={(e) => setCategoryForm({ ...categoryForm, displayOrder: parseInt(e.target.value) || 1 })}
          />
          <div className="flex gap-2 pt-2">
            <Button type="submit" loading={createCategory.isPending || updateCategory.isPending} fullWidth>
              Save Category
            </Button>
            <Button type="button" variant="secondary" onClick={() => setCategoryModalOpen(false)}>Cancel</Button>
          </div>
        </form>
      </Modal>

      <Modal
        open={productModalOpen}
        onClose={() => setProductModalOpen(false)}
        title={productForm.id ? 'Edit Menu Item' : 'Add New Menu Item'}
      >
        <form onSubmit={handleSaveProduct} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div className="col-span-2">
              <FormField
                label="Product Name"
                required
                value={productForm.name}
                onChange={(e) => setProductForm({ ...productForm, name: e.target.value })}
                placeholder="e.g. Caramel Macchiato Supreme"
              />
            </div>
            <div>
              <label className={labelClasses}>Category</label>
              <select value={productForm.categoryId} onChange={(e) => setProductForm({ ...productForm, categoryId: Number(e.target.value) })} className={controlClasses}>
                {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
            <FormField
              label="Price (ETB)"
              type="number"
              step="0.01"
              required
              value={productForm.price}
              onChange={(e) => setProductForm({ ...productForm, price: parseFloat(e.target.value) })}
            />
            <FormField
              label="Prep Time (Minutes)"
              type="number"
              min={1}
              value={productForm.preparationTime}
              onChange={(e) => setProductForm({ ...productForm, preparationTime: parseInt(e.target.value) || 10 })}
            />
            <div>
              <label className={labelClasses}>Available</label>
              <select value={productForm.available ? 'true' : 'false'} onChange={(e) => setProductForm({ ...productForm, available: e.target.value === 'true' })} className={controlClasses}>
                <option value="true">In Stock</option><option value="false">Out of Stock</option>
              </select>
            </div>
            <div className="col-span-2 grid grid-cols-3 gap-3 rounded-control border border-line bg-surface-2 p-3">
              <div className="col-span-3 flex items-center justify-between">
                <span className="text-label-s uppercase text-muted">Promotional Pricing (Optional)</span>
                {productForm.discountPrice != null && (
                  <Button variant="link" type="button" onClick={() => setProductForm({ ...productForm, discountPrice: undefined, discountStartAt: undefined, discountEndAt: undefined })}>
                    Clear
                  </Button>
                )}
              </div>
              <FormField
                label="Discount Price"
                type="number"
                step="0.01"
                min={0}
                value={productForm.discountPrice ?? ''}
                onChange={(e) => setProductForm({ ...productForm, discountPrice: e.target.value ? parseFloat(e.target.value) : undefined })}
                placeholder="e.g. 7.99"
              />
              <FormField
                label="Starts"
                type="datetime-local"
                value={productForm.discountStartAt || ''}
                onChange={(e) => setProductForm({ ...productForm, discountStartAt: e.target.value || undefined })}
              />
              <FormField
                label="Ends"
                type="datetime-local"
                value={productForm.discountEndAt || ''}
                onChange={(e) => setProductForm({ ...productForm, discountEndAt: e.target.value || undefined })}
              />
              <p className="col-span-3 text-label-s text-muted">Leave Starts/Ends blank for an always-on discount while a discount price is set. Otherwise the discount is active only between them.</p>
            </div>
            <div className="col-span-2">
              <FormField
                as="textarea"
                rows={2}
                label="Description"
                value={productForm.description}
                onChange={(e) => setProductForm({ ...productForm, description: e.target.value })}
                placeholder="Ingredients and taste profile description..."
              />
            </div>
            <div className="col-span-2 space-y-2">
              <label className={labelClasses}>Product Photo</label>
              {productForm.id ? (
                <div className="flex items-center gap-2">
                  <label className={`flex cursor-pointer items-center gap-1.5 rounded-control border border-line-strong bg-surface-2 px-3 py-2 text-label-s text-ink hover:bg-line ${uploadProductImage.isPending ? 'pointer-events-none opacity-50' : ''}`}>
                    {uploadProductImage.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />}
                    Upload a Photo
                    <input type="file" accept="image/jpeg,image/png,image/webp" onChange={handleUploadImage} className="hidden" />
                  </label>
                  <span className="text-label-s text-muted">JPEG, PNG, or WebP, up to 5MB</span>
                </div>
              ) : (
                <p className="text-label-s text-muted">Save this item first, then come back to edit it to upload a real photo.</p>
              )}
              <FormField
                label="Or Paste an Image URL"
                value={productForm.image || ''}
                onChange={(e) => setProductForm({ ...productForm, image: e.target.value })}
                placeholder="https://..."
              />
              <div className="text-label-s text-muted">Or Select Unsplash Food Preset:</div>
              <div className="grid grid-cols-4 gap-2">
                {FOOD_IMAGE_PRESETS.map((preset, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => setProductForm({ ...productForm, image: preset.url })}
                    aria-label={`Use ${preset.label} preset image`}
                    className={`relative h-14 overflow-hidden rounded-control border-2 transition-all ${productForm.image === preset.url ? 'border-brand-dark ring-2 ring-brand-dark/20' : 'border-transparent'}`}
                  >
                    <img src={preset.url} alt={preset.label} className="h-full w-full object-cover" />
                  </button>
                ))}
              </div>
            </div>
          </div>
          <div className="flex gap-2 border-t border-line pt-3">
            <Button type="submit" loading={createProduct.isPending || updateProduct.isPending} fullWidth>
              Save Product
            </Button>
            <Button type="button" variant="secondary" onClick={() => setProductModalOpen(false)}>Cancel</Button>
          </div>
        </form>
      </Modal>
    </DashboardLayout>
  );
};
