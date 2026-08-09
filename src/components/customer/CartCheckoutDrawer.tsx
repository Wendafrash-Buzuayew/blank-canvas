import React, { useState } from 'react';
import { X, Trash2, ShoppingBag, CreditCard, Banknote, Smartphone, CheckCircle2, ArrowRight, Table as TableIcon } from 'lucide-react';
import { CartItem, Merchant, Table } from '../../types';
import { formatMoney } from '../../lib/utils';
import confetti from 'canvas-confetti';

interface CartCheckoutDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  cart: CartItem[];
  onUpdateQuantity: (index: number, delta: number) => void;
  onRemoveItem: (index: number) => void;
  onClearCart: () => void;
  merchant: Merchant;
  tables: Table[];
  selectedTableNumber: string;
  setSelectedTableNumber: (tableNum: string) => void;
  onSubmitOrder: (orderDetails: {
    customerName: string;
    customerPhone: string;
    tableNumber: string;
    paymentMethod: 'Cash' | 'Card' | 'M-PESA' | 'Telebirr' | 'Pay at Counter';
    notes: string;
  }) => void;
}

export const CartCheckoutDrawer: React.FC<CartCheckoutDrawerProps> = ({
  isOpen,
  onClose,
  cart,
  onUpdateQuantity,
  onRemoveItem,
  onClearCart,
  merchant,
  tables,
  selectedTableNumber,
  setSelectedTableNumber,
  onSubmitOrder,
}) => {
  if (!isOpen) return null;

  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<'Pay at Counter' | 'Cash' | 'Card' | 'M-PESA' | 'Telebirr'>('Pay at Counter');
  const [orderNotes, setOrderNotes] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const subtotal = cart.reduce((sum, item) => {
    const price = item.menuItem.discountPrice ?? item.menuItem.price;
    return sum + price * item.quantity;
  }, 0);

  const serviceFee = subtotal * 0.05; // 5% service fee demo
  const grandTotal = subtotal + serviceFee;

  const handleCheckout = (e: React.FormEvent) => {
    e.preventDefault();
    if (cart.length === 0) return;

    setIsSubmitting(true);

    // Fire confetti celebration
    try {
      confetti({
        particleCount: 80,
        spread: 70,
        origin: { y: 0.6 }
      });
    } catch {
      // ignore
    }

    setTimeout(() => {
      onSubmitOrder({
        customerName: customerName.trim() || 'Guest',
        customerPhone: customerPhone.trim(),
        tableNumber: selectedTableNumber,
        paymentMethod,
        notes: orderNotes.trim(),
      });
      setIsSubmitting(false);
    }, 600);
  };

  return (
    <div className="fixed inset-0 z-50 overflow-hidden bg-black/60 backdrop-blur-xs flex justify-end animate-in fade-in duration-200">
      <div 
        className="w-full max-w-md bg-white h-full shadow-2xl flex flex-col justify-between"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="p-4 sm:p-5 border-b border-gray-100 flex items-center justify-between bg-white sticky top-0 z-10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-red-50 text-[#E60028] flex items-center justify-center font-bold">
              <ShoppingBag className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-extrabold text-lg text-gray-900">Your Order Cart</h3>
              <p className="text-xs text-gray-500">
                {cart.length} {cart.length === 1 ? 'item' : 'items'} selected
              </p>
            </div>
          </div>
          <button 
            onClick={onClose}
            className="p-2 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scrollable Content */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-6">

          {/* Table Picker Banner */}
          <div className="bg-gradient-to-r from-red-50 to-amber-50 border border-red-100 p-3.5 rounded-2xl flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-white shadow-xs flex items-center justify-center text-[#E60028]">
                <TableIcon className="w-5 h-5" />
              </div>
              <div>
                <span className="text-[10px] font-extrabold uppercase text-red-600 tracking-wider">Identified Table</span>
                <div className="text-sm font-black text-gray-900">Table {selectedTableNumber}</div>
              </div>
            </div>
            <select
              value={selectedTableNumber}
              onChange={(e) => setSelectedTableNumber(e.target.value)}
              className="text-xs font-bold bg-white text-gray-800 border border-red-200 rounded-xl px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20"
            >
              {tables.map(t => (
                <option key={t.id} value={t.tableNumber}>
                  Table {t.tableNumber} ({t.capacity} seats)
                </option>
              ))}
            </select>
          </div>

          {/* Cart Items List */}
          {cart.length === 0 ? (
            <div className="py-12 text-center text-gray-400 space-y-3">
              <ShoppingBag className="w-12 h-12 mx-auto stroke-1 text-gray-300" />
              <p className="text-sm font-medium">Your order cart is currently empty.</p>
              <button
                onClick={onClose}
                className="text-xs font-bold text-[#E60028] underline hover:text-red-700"
              >
                Browse Menu & Add Items
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              <div className="flex items-center justify-between text-xs font-bold text-gray-400 uppercase tracking-wider">
                <span>Ordered Items</span>
                <button 
                  onClick={onClearCart}
                  className="text-red-500 hover:underline text-[11px]"
                >
                  Clear all
                </button>
              </div>

              {cart.map((item, idx) => {
                const itemPrice = item.menuItem.discountPrice ?? item.menuItem.price;
                return (
                  <div 
                    key={idx}
                    className="flex gap-3 p-3 bg-gray-50 rounded-2xl border border-gray-100 transition-all hover:bg-gray-100/60"
                  >
                    <img 
                      src={item.menuItem.image} 
                      alt={item.menuItem.name} 
                      className="w-16 h-16 rounded-xl object-cover shrink-0"
                    />
                    <div className="flex-1 flex flex-col justify-between">
                      <div>
                        <div className="flex items-start justify-between gap-1">
                          <h4 className="text-xs font-bold text-gray-900 leading-tight">
                            {item.menuItem.name}
                          </h4>
                          <button
                            onClick={() => onRemoveItem(idx)}
                            className="text-gray-400 hover:text-red-500 p-0.5"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                        {item.notes && (
                          <p className="text-[11px] text-amber-700 font-medium italic mt-0.5">
                            Note: "{item.notes}"
                          </p>
                        )}
                      </div>

                      <div className="flex items-center justify-between mt-2">
                        <span className="text-xs font-black text-gray-900">
                          {formatMoney(itemPrice * item.quantity, merchant.currencySymbol)}
                        </span>

                        <div className="flex items-center gap-2 bg-white px-2 py-0.5 rounded-lg border border-gray-200">
                          <button
                            onClick={() => onUpdateQuantity(idx, -1)}
                            className="text-xs font-extrabold text-gray-600 px-1 hover:text-red-600"
                          >
                            -
                          </button>
                          <span className="text-xs font-bold text-gray-900">{item.quantity}</span>
                          <button
                            onClick={() => onUpdateQuantity(idx, 1)}
                            className="text-xs font-extrabold text-gray-600 px-1 hover:text-red-600"
                          >
                            +
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {cart.length > 0 && (
            <form id="checkout-form" onSubmit={handleCheckout} className="space-y-4 pt-4 border-t border-gray-100">
              
              {/* Customer Info */}
              <div>
                <label className="block text-xs font-bold text-gray-900 uppercase tracking-wider mb-1">
                  Customer Name (Optional)
                </label>
                <input
                  type="text"
                  value={customerName}
                  onChange={(e) => setCustomerName(e.target.value)}
                  placeholder="e.g. Alex"
                  className="w-full text-xs p-2.5 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>

              {/* Payment Method Selector */}
              <div>
                <label className="block text-xs font-bold text-gray-900 uppercase tracking-wider mb-2">
                  Payment Preference
                </label>
                <div className="grid grid-cols-2 gap-2">
                  {[
                    { id: 'Pay at Counter', label: 'Pay at Counter', icon: Banknote },
                    { id: 'Cash', label: 'Cash on Delivery', icon: Banknote },
                    { id: 'Card', label: 'Credit/Debit Card', icon: CreditCard },
                    { id: 'M-PESA', label: 'M-PESA Mobile', icon: Smartphone },
                  ].map((mode) => {
                    const Icon = mode.icon;
                    const isSelected = paymentMethod === mode.id;
                    return (
                      <button
                        key={mode.id}
                        type="button"
                        onClick={() => setPaymentMethod(mode.id as any)}
                        className={`p-2.5 rounded-xl border text-left flex items-center gap-2 transition-all ${
                          isSelected
                            ? 'bg-red-50 border-[#E60028] text-[#E60028] font-bold shadow-xs'
                            : 'bg-white border-gray-200 text-gray-700 hover:border-gray-300'
                        }`}
                      >
                        <Icon className="w-4 h-4 shrink-0" />
                        <span className="text-xs truncate">{mode.label}</span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Special Order Instructions */}
              <div>
                <label className="block text-xs font-bold text-gray-900 uppercase tracking-wider mb-1">
                  Table Order Notes
                </label>
                <input
                  type="text"
                  value={orderNotes}
                  onChange={(e) => setOrderNotes(e.target.value)}
                  placeholder="e.g. Bring extra napkins, celebration cake coming..."
                  className="w-full text-xs p-2.5 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>

              {/* Bill Breakdown */}
              <div className="bg-gray-50 p-3.5 rounded-2xl border border-gray-100 space-y-1.5 text-xs">
                <div className="flex justify-between text-gray-600">
                  <span>Subtotal</span>
                  <span>{formatMoney(subtotal, merchant.currencySymbol)}</span>
                </div>
                <div className="flex justify-between text-gray-600">
                  <span>Service & Taxes (5%)</span>
                  <span>{formatMoney(serviceFee, merchant.currencySymbol)}</span>
                </div>
                <div className="flex justify-between text-sm font-extrabold text-gray-900 pt-2 border-t border-gray-200">
                  <span>Total Amount</span>
                  <span className="text-[#E60028]">
                    {formatMoney(grandTotal, merchant.currencySymbol)}
                  </span>
                </div>
              </div>

            </form>
          )}

        </div>

        {/* Footer Checkout CTA */}
        {cart.length > 0 && (
          <div className="p-4 bg-white border-t border-gray-100">
            <button
              form="checkout-form"
              type="submit"
              disabled={isSubmitting}
              className="w-full py-4 px-6 bg-[#E60028] hover:bg-[#CC0024] disabled:bg-gray-400 text-white font-extrabold rounded-2xl shadow-xl shadow-red-500/25 flex items-center justify-between transition-all"
            >
              <span className="flex items-center gap-2">
                {isSubmitting ? 'Sending Order to Kitchen...' : 'Send Order to Kitchen'}
                <ArrowRight className="w-5 h-5" />
              </span>
              <span className="text-lg font-black">
                {formatMoney(grandTotal, merchant.currencySymbol)}
              </span>
            </button>
          </div>
        )}

      </div>
    </div>
  );
};
