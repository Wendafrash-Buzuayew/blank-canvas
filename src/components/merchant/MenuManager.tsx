import React, { useState } from 'react';
import { 
  Plus, 
  Trash2, 
  Edit3, 
  Check, 
  X, 
  Tag, 
  Utensils, 
  Sparkles, 
  Clock, 
  Image as ImageIcon,
  FolderPlus,
  Flame,
  Search
} from 'lucide-react';
import { Merchant, Category, MenuItem, ItemTag } from '../../types';
import { formatMoney } from '../../lib/utils';

interface MenuManagerProps {
  merchant: Merchant;
  categories: Category[];
  menuItems: MenuItem[];
  onSaveCategory: (category: Category) => void;
  onDeleteCategory: (categoryId: string) => void;
  onSaveMenuItem: (item: MenuItem) => void;
  onDeleteMenuItem: (itemId: string) => void;
  onToggleAvailability: (itemId: string) => void;
}

export const MenuManager: React.FC<MenuManagerProps> = ({
  merchant,
  categories,
  menuItems,
  onSaveCategory,
  onDeleteCategory,
  onSaveMenuItem,
  onDeleteMenuItem,
  onToggleAvailability,
}) => {
  const merchantCategories = categories.filter(c => c.merchantId === merchant.id);
  const merchantItems = menuItems.filter(i => i.merchantId === merchant.id);

  const [activeCategoryId, setActiveCategoryId] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState('');

  // Modals state
  const [isCategoryModalOpen, setIsCategoryModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<Partial<Category> | null>(null);

  const [isProductModalOpen, setIsProductModalOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<Partial<MenuItem> | null>(null);

  const availableTags: ItemTag[] = ['Bestseller', 'Vegan', 'Vegetarian', 'Gluten-Free', 'Hot', 'Spicy', 'Chef Special', 'Organic'];

  const foodImagePresets = [
    { label: 'Cappuccino / Latte', url: 'https://images.unsplash.com/photo-1534778101976-62847782c213?w=600&auto=format&fit=crop&q=80' },
    { label: 'Matcha Tea', url: 'https://images.unsplash.com/photo-1536256263959-770b48d82b0a?w=600&auto=format&fit=crop&q=80' },
    { label: 'Avocado Toast', url: 'https://images.unsplash.com/photo-1525351484163-7529414344d8?w=600&auto=format&fit=crop&q=80' },
    { label: 'Eggs Benedict', url: 'https://images.unsplash.com/photo-1608039829572-78524f79c4c7?w=600&auto=format&fit=crop&q=80' },
    { label: 'Gourmet Burger', url: 'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=600&auto=format&fit=crop&q=80' },
    { label: 'Woodfired Pizza', url: 'https://images.unsplash.com/photo-1604382354936-07c5d9983bd3?w=600&auto=format&fit=crop&q=80' },
    { label: 'Almond Croissant', url: 'https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=600&auto=format&fit=crop&q=80' },
    { label: 'Tiramisu Dessert', url: 'https://images.unsplash.com/photo-1571877227200-a0d98ea607e9?w=600&auto=format&fit=crop&q=80' },
  ];

  const filteredItems = merchantItems.filter(item => {
    const matchesCat = activeCategoryId === 'all' || item.categoryId === activeCategoryId;
    const matchesSearch = item.name.toLowerCase().includes(searchQuery.toLowerCase()) || 
                          item.description.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesCat && matchesSearch;
  });

  const handleOpenCategoryModal = (cat?: Category) => {
    setEditingCategory(cat || { name: '', sortOrder: merchantCategories.length + 1 });
    setIsCategoryModalOpen(true);
  };

  const handleSaveCategorySubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingCategory?.name) return;
    onSaveCategory({
      id: editingCategory.id || `c-${Date.now()}`,
      merchantId: merchant.id,
      name: editingCategory.name,
      sortOrder: editingCategory.sortOrder || 1,
    });
    setIsCategoryModalOpen(false);
  };

  const handleOpenProductModal = (prod?: MenuItem) => {
    setEditingProduct(prod || {
      name: '',
      description: '',
      price: 9.99,
      categoryId: merchantCategories[0]?.id || 'c1',
      available: true,
      preparationTimeMinutes: 10,
      image: foodImagePresets[0].url,
      tags: ['Bestseller'],
    });
    setIsProductModalOpen(true);
  };

  const handleSaveProductSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingProduct?.name || !editingProduct?.price) return;
    onSaveMenuItem({
      id: editingProduct.id || `item-${Date.now()}`,
      merchantId: merchant.id,
      categoryId: editingProduct.categoryId || merchantCategories[0]?.id || 'c1',
      name: editingProduct.name,
      description: editingProduct.description || '',
      price: Number(editingProduct.price),
      discountPrice: editingProduct.discountPrice ? Number(editingProduct.discountPrice) : undefined,
      image: editingProduct.image || foodImagePresets[0].url,
      available: editingProduct.available ?? true,
      preparationTimeMinutes: Number(editingProduct.preparationTimeMinutes || 10),
      tags: editingProduct.tags || [],
    });
    setIsProductModalOpen(false);
  };

  const toggleTagSelection = (tag: ItemTag) => {
    if (!editingProduct) return;
    const currentTags = editingProduct.tags || [];
    if (currentTags.includes(tag)) {
      setEditingProduct({ ...editingProduct, tags: currentTags.filter(t => t !== tag) });
    } else {
      setEditingProduct({ ...editingProduct, tags: [...currentTags, tag] });
    }
  };

  return (
    <div className="space-y-6">

      {/* Header Actions Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-3xl border border-gray-100 shadow-xs">
        <div>
          <h2 className="text-xl font-black text-gray-900 flex items-center gap-2">
            <Utensils className="w-6 h-6 text-[#E60028]" />
            Digital Menu Builder
          </h2>
          <p className="text-xs text-gray-500 mt-0.5">Manage categories, dishes, prices, and instant availability</p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => handleOpenCategoryModal()}
            className="px-3.5 py-2 bg-gray-100 hover:bg-gray-200 text-gray-800 text-xs font-bold rounded-xl border border-gray-200 flex items-center gap-1.5 transition-colors"
          >
            <FolderPlus className="w-4 h-4 text-gray-600" />
            + Add Category
          </button>
          <button
            onClick={() => handleOpenProductModal()}
            className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-bold rounded-xl shadow-md flex items-center gap-1.5 transition-colors"
          >
            <Plus className="w-4 h-4" />
            Add Menu Item
          </button>
        </div>
      </div>

      {/* Category Tabs & Search Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white p-4 rounded-2xl border border-gray-100 shadow-xs">
        <div className="flex items-center gap-2 overflow-x-auto pb-1 sm:pb-0 no-scrollbar text-xs">
          <button
            onClick={() => setActiveCategoryId('all')}
            className={`px-3.5 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
              activeCategoryId === 'all'
                ? 'bg-red-50 text-[#E60028] border border-red-200'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            All Items ({merchantItems.length})
          </button>
          {merchantCategories.map(cat => (
            <div key={cat.id} className="flex items-center gap-1">
              <button
                onClick={() => setActiveCategoryId(cat.id)}
                className={`px-3.5 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
                  activeCategoryId === cat.id
                    ? 'bg-red-50 text-[#E60028] border border-red-200'
                    : 'text-gray-600 hover:text-gray-900'
                }`}
              >
                {cat.name} ({merchantItems.filter(i => i.categoryId === cat.id).length})
              </button>
              <button
                onClick={() => handleOpenCategoryModal(cat)}
                className="text-gray-400 hover:text-gray-700 p-1"
                title="Edit Category"
              >
                <Edit3 className="w-3 h-3" />
              </button>
            </div>
          ))}
        </div>

        <div className="relative">
          <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-gray-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search products..."
            className="pl-8 pr-3 py-1.5 bg-gray-50 border border-gray-200 rounded-xl text-xs focus:outline-none focus:ring-2 focus:ring-[#E60028]/20"
          />
        </div>
      </div>

      {/* Products Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filteredItems.map(item => (
          <div 
            key={item.id}
            className={`bg-white rounded-3xl p-4 border shadow-xs flex flex-col justify-between space-y-3 transition-all hover:shadow-md ${
              !item.available ? 'opacity-60 bg-gray-50/80 border-gray-200' : 'border-gray-100'
            }`}
          >
            <div className="flex gap-3">
              <img 
                src={item.image} 
                alt={item.name} 
                className="w-20 h-20 rounded-2xl object-cover shrink-0"
              />
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between gap-1 mb-1">
                  <span className="text-[10px] font-bold text-gray-500 bg-gray-100 px-2 py-0.5 rounded-md truncate">
                    {merchantCategories.find(c => c.id === item.categoryId)?.name || 'Category'}
                  </span>
                  
                  {/* Availability Toggle */}
                  <button
                    onClick={() => onToggleAvailability(item.id)}
                    className={`px-2 py-0.5 rounded-md text-[10px] font-extrabold transition-colors ${
                      item.available ? 'bg-emerald-100 text-emerald-800' : 'bg-red-100 text-red-800'
                    }`}
                  >
                    {item.available ? 'In Stock' : 'Out of Stock'}
                  </button>
                </div>

                <h3 className="font-extrabold text-sm text-gray-900 truncate">{item.name}</h3>
                <p className="text-[11px] text-gray-500 line-clamp-2 mt-0.5">{item.description}</p>
              </div>
            </div>

            {/* Price & Actions Row */}
            <div className="flex items-center justify-between pt-2 border-t border-gray-100 text-xs">
              <div className="flex items-baseline gap-1.5">
                <span className="font-black text-sm text-gray-900">
                  {formatMoney(item.discountPrice ?? item.price, merchant.currencySymbol)}
                </span>
                {item.discountPrice && (
                  <span className="text-[10px] text-gray-400 line-through">
                    {formatMoney(item.price, merchant.currencySymbol)}
                  </span>
                )}
              </div>

              <div className="flex items-center gap-1">
                <button
                  onClick={() => handleOpenProductModal(item)}
                  className="p-1.5 text-gray-500 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors"
                >
                  <Edit3 className="w-3.5 h-3.5" />
                </button>
                <button
                  onClick={() => {
                    if (confirm(`Delete ${item.name}?`)) onDeleteMenuItem(item.id);
                  }}
                  className="p-1.5 text-red-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Category Create/Edit Modal */}
      {isCategoryModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form onSubmit={handleSaveCategorySubmit} className="bg-white max-w-sm w-full p-6 rounded-3xl shadow-2xl space-y-4">
            <h3 className="font-extrabold text-base text-gray-900">
              {editingCategory?.id ? 'Edit Category' : 'Create New Category'}
            </h3>

            <div>
              <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Category Name</label>
              <input
                type="text"
                required
                value={editingCategory?.name || ''}
                onChange={(e) => setEditingCategory({ ...editingCategory, name: e.target.value })}
                placeholder="e.g. Artisanal Pizza"
                className="w-full text-xs p-2.5 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20"
              />
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="submit"
                className="flex-1 py-2.5 bg-[#E60028] text-white font-bold text-xs rounded-xl shadow-xs"
              >
                Save Category
              </button>
              <button
                type="button"
                onClick={() => setIsCategoryModalOpen(false)}
                className="py-2.5 px-4 bg-gray-100 text-gray-700 font-bold text-xs rounded-xl"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Product Create/Edit Modal */}
      {isProductModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 overflow-y-auto">
          <form onSubmit={handleSaveProductSubmit} className="bg-white max-w-lg w-full p-6 rounded-3xl shadow-2xl space-y-4 my-8">
            <div className="flex justify-between items-center border-b border-gray-100 pb-3">
              <h3 className="font-extrabold text-lg text-gray-900">
                {editingProduct?.id ? 'Edit Menu Item' : 'Add New Menu Item'}
              </h3>
              <button type="button" onClick={() => setIsProductModalOpen(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="col-span-2">
                <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Product Name</label>
                <input
                  type="text"
                  required
                  value={editingProduct?.name || ''}
                  onChange={(e) => setEditingProduct({ ...editingProduct, name: e.target.value })}
                  placeholder="e.g. Caramel Macchiato Supreme"
                  className="w-full text-xs p-2.5 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Category</label>
                <select
                  value={editingProduct?.categoryId || ''}
                  onChange={(e) => setEditingProduct({ ...editingProduct, categoryId: e.target.value })}
                  className="w-full text-xs p-2.5 rounded-xl border border-gray-200 focus:outline-none"
                >
                  {merchantCategories.map(c => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Price ({merchant.currencySymbol})</label>
                <input
                  type="number"
                  step="0.01"
                  required
                  value={editingProduct?.price || ''}
                  onChange={(e) => setEditingProduct({ ...editingProduct, price: parseFloat(e.target.value) })}
                  className="w-full text-xs p-2.5 rounded-xl border border-gray-200 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Discount Price (Optional)</label>
                <input
                  type="number"
                  step="0.01"
                  value={editingProduct?.discountPrice || ''}
                  onChange={(e) => setEditingProduct({ ...editingProduct, discountPrice: e.target.value ? parseFloat(e.target.value) : undefined })}
                  placeholder="e.g. 4.80"
                  className="w-full text-xs p-2.5 rounded-xl border border-gray-200 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Prep Time (Minutes)</label>
                <input
                  type="number"
                  value={editingProduct?.preparationTimeMinutes || 10}
                  onChange={(e) => setEditingProduct({ ...editingProduct, preparationTimeMinutes: parseInt(e.target.value) })}
                  className="w-full text-xs p-2.5 rounded-xl border border-gray-200 focus:outline-none"
                />
              </div>

              <div className="col-span-2">
                <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Description</label>
                <textarea
                  rows={2}
                  value={editingProduct?.description || ''}
                  onChange={(e) => setEditingProduct({ ...editingProduct, description: e.target.value })}
                  placeholder="Ingredients and taste profile description..."
                  className="w-full text-xs p-2.5 rounded-xl border border-gray-200 focus:outline-none"
                />
              </div>

              {/* Image URL & Presets */}
              <div className="col-span-2 space-y-2">
                <label className="block text-xs font-bold text-gray-700 uppercase">Product Image URL</label>
                <input
                  type="text"
                  value={editingProduct?.image || ''}
                  onChange={(e) => setEditingProduct({ ...editingProduct, image: e.target.value })}
                  placeholder="https://..."
                  className="w-full text-xs p-2.5 rounded-xl border border-gray-200 focus:outline-none"
                />

                <div className="text-[11px] font-bold text-gray-400">Or Select Unsplash Food Preset:</div>
                <div className="grid grid-cols-4 gap-2">
                  {foodImagePresets.map((preset, idx) => (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => setEditingProduct({ ...editingProduct, image: preset.url })}
                      className={`relative h-14 rounded-xl overflow-hidden border-2 transition-all ${
                        editingProduct?.image === preset.url ? 'border-[#E60028] ring-2 ring-red-500/20' : 'border-transparent'
                      }`}
                    >
                      <img src={preset.url} alt={preset.label} className="w-full h-full object-cover" />
                    </button>
                  ))}
                </div>
              </div>

              {/* Tags Selector */}
              <div className="col-span-2">
                <label className="block text-xs font-bold text-gray-700 uppercase mb-1.5">Tags & Dietary Badges</label>
                <div className="flex flex-wrap gap-1.5">
                  {availableTags.map(tag => {
                    const isSelected = (editingProduct?.tags || []).includes(tag);
                    return (
                      <button
                        key={tag}
                        type="button"
                        onClick={() => toggleTagSelection(tag)}
                        className={`px-2.5 py-1 rounded-lg text-xs font-bold border transition-colors ${
                          isSelected ? 'bg-red-50 border-[#E60028] text-[#E60028]' : 'bg-gray-50 border-gray-200 text-gray-600'
                        }`}
                      >
                        {isSelected ? '✓ ' : '+ '}{tag}
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>

            <div className="flex gap-2 pt-3 border-t border-gray-100">
              <button
                type="submit"
                className="flex-1 py-3 bg-[#E60028] text-white font-bold text-xs rounded-xl shadow-md"
              >
                Save Product
              </button>
              <button
                type="button"
                onClick={() => setIsProductModalOpen(false)}
                className="py-3 px-5 bg-gray-100 text-gray-700 font-bold text-xs rounded-xl"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

    </div>
  );
};
