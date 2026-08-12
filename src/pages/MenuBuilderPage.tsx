import React, { useMemo, useState } from 'react';
import { Plus, Trash2, Edit3, X, Utensils, FolderPlus, Search, Loader2, Eye, Smartphone, Image as ImageIcon, Clock, AlertCircle } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { useAuth } from '../context/AuthContext';
import { useMenu, useCreateCategory, useUpdateCategory, useDeleteCategory, useCreateProduct, useUpdateProduct, useDeleteProduct } from '../hooks/useApiData';
import { useBranchesLookup, useMerchantsLookup, useTablesLookup } from '../hooks/useLookups';
import { friendlyError } from '../lib/errors';
import { useNavigate } from 'react-router-dom';
import type { MenuResponse } from '../lib/api';

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
interface ProductFormState { id?: number; categoryId: number; name: string; description: string; price: number; image?: string; available: boolean; preparationTime: number; }

export const MenuBuilderPage: React.FC = () => {
  const { user } = useAuth();
  const merchantId = user?.merchantId;
  const navigate = useNavigate();
  const { data: menu, isLoading, error, refetch } = useMenu(merchantId);
  const merchantsQuery = useMerchantsLookup();
  const branchesQuery = useBranchesLookup();
  const tablesQuery = useTablesLookup();
  const createCategory = useCreateCategory();
  const updateCategory = useUpdateCategory();
  const deleteCategory = useDeleteCategory();
  const createProduct = useCreateProduct();
  const updateProduct = useUpdateProduct();
  const deleteProduct = useDeleteProduct();

  const [activeCategoryId, setActiveCategoryId] = useState<number | 'all'>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [pageError, setPageError] = useState<string | null>(null);
  const [categoryModalOpen, setCategoryModalOpen] = useState(false);
  const [categoryForm, setCategoryForm] = useState<CategoryFormState>({ name: '' });
  const [productModalOpen, setProductModalOpen] = useState(false);
  const [productForm, setProductForm] = useState<ProductFormState>({ categoryId: 0, name: '', description: '', price: 9.99, image: FOOD_IMAGE_PRESETS[0].url, available: true, preparationTime: 10 });

  const merchant = merchantsQuery.data?.find((m) => m.id === merchantId);
  const merchantSlug = merchant?.slug || 'demo';
  const firstBranch = branchesQuery.data?.[0];
  const firstTable = tablesQuery.data?.[0];
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
        await createCategory.mutateAsync({ merchantId, name: categoryForm.name.trim(), displayOrder: categoryForm.displayOrder });
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
      price: Number(product.price), image: product.image || FOOD_IMAGE_PRESETS[0].url, available: product.available, preparationTime: product.preparationTime || 10,
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
    try {
      if (productForm.id) {
        await updateProduct.mutateAsync({ id: productForm.id, data: { name: productForm.name.trim(), description: productForm.description, price: Number(productForm.price), image: productForm.image, available: productForm.available, preparationTime: Number(productForm.preparationTime) } });
      } else {
        await createProduct.mutateAsync({ categoryId: productForm.categoryId, name: productForm.name.trim(), description: productForm.description, price: Number(productForm.price), image: productForm.image, preparationTime: Number(productForm.preparationTime) });
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
    if (!merchantSlug || !firstTable) { alert('You need at least one table to preview the customer menu. Create a table first.'); return; }
    const branchSlug = firstBranch?.name?.toLowerCase().replace(/\s+/g, '-') || 'main';
    navigate(`/menu/${merchantSlug}/${branchSlug}/${firstTable.tableNumber}`);
  };

  const handleQRDemo = () => {
    if (!merchantSlug || !firstTable) { alert('You need at least one table to demo the QR scan. Create a table first.'); return; }
    const branchSlug = firstBranch?.name?.toLowerCase().replace(/\s+/g, '-') || 'main';
    navigate(`/menu/${merchantSlug}/${branchSlug}/${firstTable.tableNumber}?demo=qr`);
  };

  return (
    <DashboardLayout title="Menu Builder">
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div>
            <h2 className="text-xl font-black text-slate-900 flex items-center gap-2">
              <Utensils className="w-6 h-6 text-[#E60028]" /> Digital Menu Builder
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">Build your menu by category and product — customers see it instantly after scanning the QR code.</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <button onClick={handlePreviewMenu} disabled={!merchantSlug || !firstTable} className="px-4 py-2 bg-slate-900 hover:bg-black text-white text-xs font-bold rounded-xl flex items-center gap-1.5 transition-colors disabled:opacity-50">
              <Eye className="w-4 h-4" /> Preview Customer Menu
            </button>
            <button onClick={handleQRDemo} disabled={!merchantSlug || !firstTable} className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-bold rounded-xl flex items-center gap-1.5 transition-colors disabled:opacity-50">
              <Smartphone className="w-4 h-4" /> QR Scan Demo
            </button>
            <button onClick={() => openCategoryModal()} className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-800 text-xs font-bold rounded-xl flex items-center gap-1.5 transition-colors">
              <FolderPlus className="w-4 h-4" /> Add Category
            </button>
            <button onClick={() => openProductModal()} disabled={categories.length === 0} className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-bold rounded-xl flex items-center gap-1.5 transition-colors disabled:opacity-50">
              <Plus className="w-4 h-4" /> Add Menu Item
            </button>
          </div>
        </div>

        {pageError && (
          <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700 flex items-center gap-2">
            <AlertCircle className="w-4 h-4" /> {pageError}
          </div>
        )}

        {isLoading && <Spinner label="Loading menu..." />}
        {!isLoading && error && <ErrorState message={friendlyError(error, 'Could not load the menu.')} onRetry={() => refetch()} />}

        {!isLoading && !error && (
          <>
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
              <div className="flex items-center gap-2 overflow-x-auto pb-1 sm:pb-0 no-scrollbar text-xs">
                <button onClick={() => setActiveCategoryId('all')} className={`px-3.5 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${activeCategoryId === 'all' ? 'bg-red-50 text-[#E60028] border border-red-200' : 'text-slate-600 hover:text-slate-900'}`}>
                  All Items ({allProducts.length})
                </button>
                {categories.map((cat) => (
                  <div key={cat.id} className="flex items-center gap-1">
                    <button onClick={() => setActiveCategoryId(cat.id)} className={`px-3.5 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${activeCategoryId === cat.id ? 'bg-red-50 text-[#E60028] border border-red-200' : 'text-slate-600 hover:text-slate-900'}`}>
                      {cat.name} ({cat.items.length})
                    </button>
                    <button onClick={() => openCategoryModal(cat)} className="text-slate-400 hover:text-slate-700 p-1" title="Edit Category"><Edit3 className="w-3 h-3" /></button>
                    <button onClick={() => handleDeleteCategory(cat)} className="text-slate-400 hover:text-red-600 p-1" title="Delete Category"><Trash2 className="w-3 h-3" /></button>
                  </div>
                ))}
              </div>
              <div className="relative">
                <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
                <input type="text" value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} placeholder="Search products..." className="pl-8 pr-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs focus:outline-none focus:ring-2 focus:ring-[#E60028]/20" />
              </div>
            </div>

            {categories.length === 0 && (
              <EmptyState title="No menu categories yet" description="Create your first category (e.g. Starters, Mains, Drinks) then add menu items to it."
                action={<button onClick={() => openCategoryModal()} className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-bold rounded-xl">Add Category</button>} />
            )}

            {categories.length > 0 && (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {filteredProducts.length === 0 && (
                  <div className="col-span-full bg-white p-12 rounded-2xl border border-slate-200 text-center text-slate-400 space-y-2">
                    <Utensils className="w-12 h-12 mx-auto stroke-1 text-slate-300" />
                    <p className="text-sm font-semibold">No menu items found.</p>
                    <button onClick={() => openProductModal()} className="text-xs font-bold text-[#E60028] hover:underline">+ Add your first menu item</button>
                  </div>
                )}
                {filteredProducts.map((product) => (
                  <div key={product.id} className={`bg-white rounded-2xl p-4 border shadow-sm flex flex-col justify-between space-y-3 transition-all hover:shadow-md ${!product.available ? 'opacity-60 bg-slate-50/80 border-slate-200' : 'border-slate-200'}`}>
                    <div className="flex gap-3">
                      {product.image ? <img src={product.image} alt={product.name} className="w-20 h-20 rounded-xl object-cover shrink-0" />
                        : <div className="w-20 h-20 rounded-xl bg-slate-100 flex items-center justify-center text-slate-400 shrink-0"><ImageIcon className="w-6 h-6" /></div>}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between gap-1 mb-1">
                          <span className="text-[10px] font-bold text-slate-500 bg-slate-100 px-2 py-0.5 rounded-md truncate">{product.categoryName}</span>
                          <button onClick={() => handleToggleAvailability(product)} className={`px-2 py-0.5 rounded-md text-[10px] font-extrabold transition-colors ${product.available ? 'bg-emerald-100 text-emerald-800' : 'bg-red-100 text-red-800'}`}>
                            {product.available ? 'In Stock' : 'Out of Stock'}
                          </button>
                        </div>
                        <h3 className="font-extrabold text-sm text-slate-900 truncate">{product.name}</h3>
                        <p className="text-[11px] text-slate-500 line-clamp-2 mt-0.5">{product.description}</p>
                      </div>
                    </div>
                    <div className="flex items-center justify-between pt-2 border-t border-slate-100 text-xs">
                      <div className="flex items-center gap-2">
                        <span className="font-black text-sm text-slate-900">{Number(product.price).toLocaleString()} ETB</span>
                        <span className="text-[10px] text-slate-400 flex items-center gap-0.5"><Clock className="w-3 h-3" />{product.preparationTime} min</span>
                      </div>
                      <div className="flex items-center gap-1">
                        <button onClick={() => openProductModal(product)} className="p-1.5 text-slate-500 hover:text-slate-900 hover:bg-slate-100 rounded-lg transition-colors"><Edit3 className="w-3.5 h-3.5" /></button>
                        <button onClick={() => handleDeleteProduct(product)} className="p-1.5 text-red-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"><Trash2 className="w-3.5 h-3.5" /></button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </div>

      {categoryModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form onSubmit={handleSaveCategory} className="bg-white max-w-sm w-full p-6 rounded-2xl shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-base text-slate-900">{categoryForm.id ? 'Edit Category' : 'Create New Category'}</h3>
              <button type="button" onClick={() => setCategoryModalOpen(false)} className="text-slate-400 hover:text-slate-600"><X className="w-5 h-5" /></button>
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase mb-1">Category Name</label>
              <input type="text" required value={categoryForm.name} onChange={(e) => setCategoryForm({ ...categoryForm, name: e.target.value })} placeholder="e.g. Artisanal Pizza" className="w-full text-xs p-2.5 rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20" />
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase mb-1">Display Order</label>
              <input type="number" min={1} value={categoryForm.displayOrder || 1} onChange={(e) => setCategoryForm({ ...categoryForm, displayOrder: parseInt(e.target.value) || 1 })} className="w-full text-xs p-2.5 rounded-xl border border-slate-200 focus:outline-none" />
            </div>
            <div className="flex gap-2 pt-2">
              <button type="submit" disabled={createCategory.isPending || updateCategory.isPending} className="flex-1 py-2.5 bg-[#E60028] text-white font-bold text-xs rounded-xl shadow-sm disabled:opacity-50 flex items-center justify-center gap-2">
                {(createCategory.isPending || updateCategory.isPending) && <Loader2 className="w-3.5 h-3.5 animate-spin" />} Save Category
              </button>
              <button type="button" onClick={() => setCategoryModalOpen(false)} className="py-2.5 px-4 bg-slate-100 text-slate-700 font-bold text-xs rounded-xl">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {productModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
          <form onSubmit={handleSaveProduct} className="bg-white max-w-lg w-full p-6 rounded-2xl shadow-2xl space-y-4 my-8">
            <div className="flex justify-between items-center border-b border-slate-100 pb-3">
              <h3 className="font-extrabold text-lg text-slate-900">{productForm.id ? 'Edit Menu Item' : 'Add New Menu Item'}</h3>
              <button type="button" onClick={() => setProductModalOpen(false)} className="text-slate-400 hover:text-slate-600"><X className="w-5 h-5" /></button>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="col-span-2">
                <label className="block text-xs font-bold text-slate-700 uppercase mb-1">Product Name</label>
                <input type="text" required value={productForm.name} onChange={(e) => setProductForm({ ...productForm, name: e.target.value })} placeholder="e.g. Caramel Macchiato Supreme" className="w-full text-xs p-2.5 rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20" />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase mb-1">Category</label>
                <select value={productForm.categoryId} onChange={(e) => setProductForm({ ...productForm, categoryId: Number(e.target.value) })} className="w-full text-xs p-2.5 rounded-xl border border-slate-200 focus:outline-none">
                  {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase mb-1">Price (ETB)</label>
                <input type="number" step="0.01" required value={productForm.price} onChange={(e) => setProductForm({ ...productForm, price: parseFloat(e.target.value) })} className="w-full text-xs p-2.5 rounded-xl border border-slate-200 focus:outline-none" />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase mb-1">Prep Time (Minutes)</label>
                <input type="number" min={1} value={productForm.preparationTime} onChange={(e) => setProductForm({ ...productForm, preparationTime: parseInt(e.target.value) || 10 })} className="w-full text-xs p-2.5 rounded-xl border border-slate-200 focus:outline-none" />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase mb-1">Available</label>
                <select value={productForm.available ? 'true' : 'false'} onChange={(e) => setProductForm({ ...productForm, available: e.target.value === 'true' })} className="w-full text-xs p-2.5 rounded-xl border border-slate-200 focus:outline-none">
                  <option value="true">In Stock</option><option value="false">Out of Stock</option>
                </select>
              </div>
              <div className="col-span-2">
                <label className="block text-xs font-bold text-slate-700 uppercase mb-1">Description</label>
                <textarea rows={2} value={productForm.description} onChange={(e) => setProductForm({ ...productForm, description: e.target.value })} placeholder="Ingredients and taste profile description..." className="w-full text-xs p-2.5 rounded-xl border border-slate-200 focus:outline-none" />
              </div>
              <div className="col-span-2 space-y-2">
                <label className="block text-xs font-bold text-slate-700 uppercase">Product Image URL</label>
                <input type="text" value={productForm.image || ''} onChange={(e) => setProductForm({ ...productForm, image: e.target.value })} placeholder="https://..." className="w-full text-xs p-2.5 rounded-xl border border-slate-200 focus:outline-none" />
                <div className="text-[11px] font-bold text-slate-400">Or Select Unsplash Food Preset:</div>
                <div className="grid grid-cols-4 gap-2">
                  {FOOD_IMAGE_PRESETS.map((preset, idx) => (
                    <button key={idx} type="button" onClick={() => setProductForm({ ...productForm, image: preset.url })} className={`relative h-14 rounded-xl overflow-hidden border-2 transition-all ${productForm.image === preset.url ? 'border-[#E60028] ring-2 ring-red-500/20' : 'border-transparent'}`}>
                      <img src={preset.url} alt={preset.label} className="w-full h-full object-cover" />
                    </button>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex gap-2 pt-3 border-t border-slate-100">
              <button type="submit" disabled={createProduct.isPending || updateProduct.isPending} className="flex-1 py-3 bg-[#E60028] text-white font-bold text-xs rounded-xl shadow-md disabled:opacity-50 flex items-center justify-center gap-2">
                {(createProduct.isPending || updateProduct.isPending) && <Loader2 className="w-3.5 h-3.5 animate-spin" />} Save Product
              </button>
              <button type="button" onClick={() => setProductModalOpen(false)} className="py-3 px-5 bg-slate-100 text-slate-700 font-bold text-xs rounded-xl">Cancel</button>
            </div>
          </form>
        </div>
      )}
    </DashboardLayout>
  );
};