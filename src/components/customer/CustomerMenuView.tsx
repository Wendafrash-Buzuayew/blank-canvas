import React, { useState, useMemo } from 'react';
import { 
  Search, 
  ShoppingBag, 
  Table as TableIcon, 
  Flame, 
  Sparkles, 
  Clock, 
  Plus, 
  Check, 
  ChevronRight, 
  Filter, 
  Info, 
  MapPin, 
  Phone, 
  Utensils 
} from 'lucide-react';
import { Merchant, Category, MenuItem, Table, CartItem, Order, ItemTag } from '../../types';
import { formatMoney } from '../../lib/utils';
import { ItemDetailModal } from './ItemDetailModal';
import { CartCheckoutDrawer } from './CartCheckoutDrawer';
import { OrderStatusModal } from './OrderStatusModal';

interface CustomerMenuViewProps {
  merchant: Merchant;
  categories: Category[];
  menuItems: MenuItem[];
  tables: Table[];
  selectedTableNumber: string;
  setSelectedTableNumber: (tableNum: string) => void;
  orders: Order[];
  onPlaceOrder: (orderData: {
    customerName: string;
    customerPhone: string;
    tableNumber: string;
    paymentMethod: 'Cash' | 'Card' | 'M-PESA' | 'Telebirr' | 'Pay at Counter';
    notes: string;
    cart: CartItem[];
  }) => void;
}

export const CustomerMenuView: React.FC<CustomerMenuViewProps> = ({
  merchant,
  categories,
  menuItems,
  tables,
  selectedTableNumber,
  setSelectedTableNumber,
  orders,
  onPlaceOrder,
}) => {
  const [activeCategoryId, setActiveCategoryId] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedTag, setSelectedTag] = useState<ItemTag | 'All'>('All');
  
  // Cart State
  const [cart, setCart] = useState<CartItem[]>([]);
  const [isCartOpen, setIsCartOpen] = useState(false);

  // Modals
  const [detailItem, setDetailItem] = useState<MenuItem | null>(null);
  const [viewingOrder, setViewingOrder] = useState<Order | null>(null);

  // Find customer's recent active order for this table
  const activeTableOrder = orders.find(
    o => o.tableNumber === selectedTableNumber && (o.status !== 'Paid' && o.status !== 'Cancelled')
  );

  // Filter Categories & Items for selected Merchant
  const merchantCategories = categories.filter(c => c.merchantId === merchant.id);
  const merchantItems = menuItems.filter(i => i.merchantId === merchant.id && i.available);

  const availableTags: ItemTag[] = ['Bestseller', 'Vegan', 'Vegetarian', 'Gluten-Free', 'Hot', 'Spicy', 'Chef Special'];

  const filteredItems = useMemo(() => {
    return merchantItems.filter(item => {
      const matchesCategory = activeCategoryId === 'all' || item.categoryId === activeCategoryId;
      const matchesSearch = item.name.toLowerCase().includes(searchQuery.toLowerCase()) || 
                            item.description.toLowerCase().includes(searchQuery.toLowerCase());
      const matchesTag = selectedTag === 'All' || item.tags.includes(selectedTag);
      return matchesCategory && matchesSearch && matchesTag;
    });
  }, [merchantItems, activeCategoryId, searchQuery, selectedTag]);

  // Cart operations
  const handleAddToCart = (item: MenuItem, quantity: number, notes: string) => {
    setCart(prev => {
      const existingIdx = prev.findIndex(ci => ci.menuItem.id === item.id && ci.notes === notes);
      if (existingIdx > -1) {
        const updated = [...prev];
        updated[existingIdx].quantity += quantity;
        return updated;
      }
      return [...prev, { menuItem: item, quantity, notes }];
    });
  };

  const handleUpdateQuantity = (index: number, delta: number) => {
    setCart(prev => {
      const updated = [...prev];
      const newQty = updated[index].quantity + delta;
      if (newQty <= 0) {
        return updated.filter((_, i) => i !== index);
      }
      updated[index].quantity = newQty;
      return updated;
    });
  };

  const handleRemoveItem = (index: number) => {
    setCart(prev => prev.filter((_, i) => i !== index));
  };

  const handleClearCart = () => setCart([]);

  const cartItemCount = cart.reduce((sum, item) => sum + item.quantity, 0);
  const cartSubtotal = cart.reduce((sum, item) => {
    const price = item.menuItem.discountPrice ?? item.menuItem.price;
    return sum + price * item.quantity;
  }, 0);

  return (
    <div className="min-h-screen bg-gray-50 pb-28">
      
      {/* Simulation Header Notice */}
      <div className="bg-gradient-to-r from-red-600 to-amber-600 text-white text-xs py-2 px-4 shadow-xs">
        <div className="max-w-md mx-auto flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
            <span className="font-extrabold">QR Customer Simulator</span>
            <span className="opacity-80 hidden sm:inline">• Scanning QR Code</span>
          </div>
          <div className="flex items-center gap-1 bg-white/20 px-2 py-0.5 rounded-lg font-bold">
            <TableIcon className="w-3.5 h-3.5" />
            <span>Table {selectedTableNumber}</span>
          </div>
        </div>
      </div>

      {/* Main Container - Mobile Centered Frame */}
      <div className="max-w-md mx-auto bg-white shadow-xl min-h-screen">

        {/* Restaurant Cover & Profile Header */}
        <div className="relative h-48 w-full bg-gray-900 overflow-hidden">
          <img 
            src={merchant.coverImage} 
            alt={merchant.name} 
            className="w-full h-full object-cover opacity-80"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/30 to-transparent" />

          {/* Restaurant Details Badge */}
          <div className="absolute bottom-4 left-4 right-4 flex items-end gap-3 text-white">
            <img 
              src={merchant.logo} 
              alt={merchant.name} 
              className="w-16 h-16 rounded-2xl object-cover border-2 border-white shadow-md bg-white shrink-0"
            />
            <div className="flex-1 min-w-0">
              <span className="px-2 py-0.5 rounded-md bg-[#E60028] text-[10px] font-bold uppercase tracking-wider text-white">
                {merchant.category}
              </span>
              <h1 className="text-xl font-black text-white truncate mt-0.5">{merchant.name}</h1>
              <div className="flex items-center gap-2 text-xs text-gray-200 mt-0.5">
                <span className="flex items-center gap-1">
                  <MapPin className="w-3 h-3 text-red-400" />
                  <span className="truncate">{merchant.city}</span>
                </span>
                <span>•</span>
                <span className="text-emerald-400 font-semibold">Open Now</span>
              </div>
            </div>
          </div>
        </div>

        {/* Table Selector Bar & Active Order Alert */}
        <div className="p-3 bg-gray-50 border-b border-gray-100 space-y-2">
          <div className="flex items-center justify-between text-xs">
            <span className="text-gray-500 font-semibold flex items-center gap-1">
              <TableIcon className="w-3.5 h-3.5 text-[#E60028]" />
              Simulate Table Scan:
            </span>
            <select
              value={selectedTableNumber}
              onChange={(e) => setSelectedTableNumber(e.target.value)}
              className="bg-white border border-gray-200 text-gray-900 text-xs font-bold rounded-lg px-2 py-1 shadow-2xs focus:outline-none"
            >
              {tables.map(t => (
                <option key={t.id} value={t.tableNumber}>
                  Table {t.tableNumber} ({t.capacity} Seats)
                </option>
              ))}
            </select>
          </div>

          {/* Ongoing Order Status Strip */}
          {activeTableOrder && (
            <button
              onClick={() => setViewingOrder(activeTableOrder)}
              className="w-full p-2.5 bg-gradient-to-r from-gray-900 to-gray-800 text-white rounded-xl shadow-md flex items-center justify-between transition-all hover:scale-[1.01]"
            >
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-[#E60028] text-white flex items-center justify-center font-bold text-xs animate-pulse">
                  <Clock className="w-4 h-4" />
                </div>
                <div className="text-left">
                  <div className="text-xs font-bold flex items-center gap-1.5">
                    Order #{activeTableOrder.orderNumber}
                    <span className="px-1.5 py-0.2 rounded bg-amber-500 text-black text-[10px] uppercase font-black">
                      {activeTableOrder.status}
                    </span>
                  </div>
                  <div className="text-[11px] text-gray-300">Tap to view live kitchen status</div>
                </div>
              </div>
              <ChevronRight className="w-4 h-4 text-gray-400" />
            </button>
          )}
        </div>

        {/* Search Bar & Tag Chips */}
        <div className="p-4 space-y-3 sticky top-16 bg-white z-20 border-b border-gray-100 shadow-2xs">
          <div className="relative">
            <Search className="absolute left-3.5 top-3 w-4 h-4 text-gray-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search dishes, drinks, desserts..."
              className="w-full pl-10 pr-4 py-2.5 bg-gray-100 border border-gray-200 rounded-2xl text-xs font-medium focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
            />
          </div>

          {/* Tags Slider */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 no-scrollbar text-xs">
            <button
              onClick={() => setSelectedTag('All')}
              className={`px-3 py-1 rounded-full font-bold whitespace-nowrap transition-colors ${
                selectedTag === 'All' 
                  ? 'bg-gray-900 text-white' 
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              All Items
            </button>
            {availableTags.map(tag => (
              <button
                key={tag}
                onClick={() => setSelectedTag(selectedTag === tag ? 'All' : tag)}
                className={`px-3 py-1 rounded-full font-bold whitespace-nowrap transition-colors ${
                  selectedTag === tag 
                    ? 'bg-[#E60028] text-white shadow-xs' 
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                {tag}
              </button>
            ))}
          </div>

          {/* Category Tabs Bar */}
          <div className="flex items-center gap-2 overflow-x-auto pt-1 no-scrollbar border-t border-gray-100">
            <button
              onClick={() => setActiveCategoryId('all')}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-bold whitespace-nowrap transition-all ${
                activeCategoryId === 'all'
                  ? 'bg-red-50 text-[#E60028] border border-red-200'
                  : 'text-gray-500 hover:text-gray-900'
              }`}
            >
              All Menu
            </button>
            {merchantCategories.map(cat => (
              <button
                key={cat.id}
                onClick={() => setActiveCategoryId(cat.id)}
                className={`px-3.5 py-1.5 rounded-xl text-xs font-bold whitespace-nowrap transition-all ${
                  activeCategoryId === cat.id
                    ? 'bg-red-50 text-[#E60028] border border-red-200'
                    : 'text-gray-500 hover:text-gray-900'
                }`}
              >
                {cat.name}
              </button>
            ))}
          </div>
        </div>

        {/* Product Items List */}
        <div className="p-4 space-y-4">
          {filteredItems.length === 0 ? (
            <div className="py-12 text-center text-gray-400 space-y-2">
              <Utensils className="w-10 h-10 mx-auto stroke-1 text-gray-300" />
              <p className="text-xs font-semibold">No menu items found matching search.</p>
            </div>
          ) : (
            filteredItems.map(item => {
              const finalPrice = item.discountPrice ?? item.price;
              return (
                <div 
                  key={item.id}
                  onClick={() => setDetailItem(item)}
                  className="flex gap-3.5 p-3 rounded-2xl bg-white border border-gray-100 hover:border-red-100 shadow-xs hover:shadow-md transition-all cursor-pointer group"
                >
                  <div className="relative w-24 h-24 rounded-2xl overflow-hidden bg-gray-100 shrink-0">
                    <img 
                      src={item.image} 
                      alt={item.name} 
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                    />
                    {item.discountPrice && (
                      <span className="absolute top-1 left-1 px-1.5 py-0.5 rounded-md bg-[#E60028] text-white text-[9px] font-black">
                        SALE
                      </span>
                    )}
                  </div>

                  <div className="flex-1 flex flex-col justify-between py-0.5">
                    <div>
                      <div className="flex items-center gap-1.5 mb-1 flex-wrap">
                        {item.tags.slice(0, 2).map(tag => (
                          <span key={tag} className="px-1.5 py-0.2 rounded text-[9px] font-bold bg-gray-100 text-gray-600">
                            {tag}
                          </span>
                        ))}
                      </div>
                      <h3 className="font-extrabold text-sm text-gray-900 group-hover:text-[#E60028] transition-colors leading-snug">
                        {item.name}
                      </h3>
                      <p className="text-[11px] text-gray-500 line-clamp-2 mt-0.5">
                        {item.description}
                      </p>
                    </div>

                    <div className="flex items-center justify-between mt-2">
                      <div className="flex items-baseline gap-1.5">
                        <span className="text-sm font-black text-gray-900">
                          {formatMoney(finalPrice, merchant.currencySymbol)}
                        </span>
                        {item.discountPrice && (
                          <span className="text-[10px] text-gray-400 line-through">
                            {formatMoney(item.price, merchant.currencySymbol)}
                          </span>
                        )}
                      </div>

                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleAddToCart(item, 1, '');
                        }}
                        className="w-8 h-8 rounded-xl bg-gray-900 hover:bg-[#E60028] text-white flex items-center justify-center transition-colors shadow-xs"
                      >
                        <Plus className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Floating Cart CTA Footer Bar */}
        {cartItemCount > 0 && (
          <div className="fixed bottom-4 left-0 right-0 z-30 px-4 max-w-md mx-auto">
            <button
              onClick={() => setIsCartOpen(true)}
              className="w-full py-3.5 px-5 bg-[#E60028] hover:bg-[#CC0024] text-white rounded-2xl shadow-2xl shadow-red-500/30 flex items-center justify-between transition-all transform active:scale-95"
            >
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-xl bg-white/20 text-white flex items-center justify-center font-extrabold text-xs backdrop-blur-md">
                  {cartItemCount}
                </div>
                <div className="text-left">
                  <div className="text-xs font-bold uppercase tracking-wider text-red-100">View Cart</div>
                  <div className="text-xs text-white/90 font-medium">Table {selectedTableNumber}</div>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <span className="text-base font-black">
                  {formatMoney(cartSubtotal, merchant.currencySymbol)}
                </span>
                <ChevronRight className="w-5 h-5" />
              </div>
            </button>
          </div>
        )}

      </div>

      {/* Item Detail Customizer Modal */}
      <ItemDetailModal
        item={detailItem}
        onClose={() => setDetailItem(null)}
        onAddToCart={handleAddToCart}
        merchant={merchant}
      />

      {/* Cart & Checkout Drawer */}
      <CartCheckoutDrawer
        isOpen={isCartOpen}
        onClose={() => setIsCartOpen(false)}
        cart={cart}
        onUpdateQuantity={handleUpdateQuantity}
        onRemoveItem={handleRemoveItem}
        onClearCart={handleClearCart}
        merchant={merchant}
        tables={tables}
        selectedTableNumber={selectedTableNumber}
        setSelectedTableNumber={setSelectedTableNumber}
        onSubmitOrder={(details) => {
          onPlaceOrder({
            ...details,
            cart
          });
          setIsCartOpen(false);
          setCart([]);
        }}
      />

      {/* Order Tracker Modal */}
      <OrderStatusModal
        order={viewingOrder}
        onClose={() => setViewingOrder(null)}
        merchant={merchant}
      />

    </div>
  );
};
