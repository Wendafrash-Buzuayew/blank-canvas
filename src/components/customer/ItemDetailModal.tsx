import React, { useState } from 'react';
import { X, Plus, Minus, Clock, ShoppingBag, Flame, Sparkles } from 'lucide-react';
import { MenuItem, Merchant } from '../../types';
import { formatMoney } from '../../lib/utils';

interface ItemDetailModalProps {
  item: MenuItem | null;
  onClose: () => void;
  onAddToCart: (item: MenuItem, quantity: number, notes: string) => void;
  merchant: Merchant;
}

export const ItemDetailModal: React.FC<ItemDetailModalProps> = ({
  item,
  onClose,
  onAddToCart,
  merchant,
}) => {
  if (!item) return null;

  const [quantity, setQuantity] = useState(1);
  const [specialNotes, setSpecialNotes] = useState('');
  const [selectedQuickNotes, setSelectedQuickNotes] = useState<string[]>([]);

  const quickNoteOptions = [
    'No Sugar',
    'Extra Spicy',
    'No Onions',
    'Dressing on Side',
    'Extra Sauce',
    'Gluten Free'
  ];

  const toggleQuickNote = (note: string) => {
    if (selectedQuickNotes.includes(note)) {
      setSelectedQuickNotes(selectedQuickNotes.filter(n => n !== note));
    } else {
      setSelectedQuickNotes([...selectedQuickNotes, note]);
    }
  };

  const handleAdd = () => {
    const combinedNotes = [
      ...selectedQuickNotes,
      specialNotes.trim()
    ].filter(Boolean).join(', ');

    onAddToCart(item, quantity, combinedNotes);
    onClose();
  };

  const finalUnitPrice = item.discountPrice ?? item.price;
  const totalPrice = finalUnitPrice * quantity;

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/60 backdrop-blur-xs p-0 sm:p-4 animate-in fade-in duration-200">
      <div 
        className="w-full max-w-lg bg-white rounded-t-3xl sm:rounded-3xl overflow-hidden shadow-2xl flex flex-col max-h-[90vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header Image with Close Button */}
        <div className="relative h-64 sm:h-72 w-full bg-gray-100 shrink-0">
          <img 
            src={item.image} 
            alt={item.name} 
            className="w-full h-full object-cover"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-transparent to-black/30" />
          
          <button 
            onClick={onClose}
            className="absolute top-4 right-4 w-9 h-9 bg-black/50 hover:bg-black/80 text-white rounded-full flex items-center justify-center backdrop-blur-md transition-colors"
          >
            <X className="w-5 h-5" />
          </button>

          <div className="absolute bottom-4 left-4 right-4 text-white">
            <div className="flex items-center gap-2 mb-1 flex-wrap">
              {item.tags.map((tag) => (
                <span key={tag} className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-white/20 backdrop-blur-md text-white border border-white/30">
                  {tag}
                </span>
              ))}
            </div>
            <h3 className="text-2xl font-black tracking-tight">{item.name}</h3>
          </div>
        </div>

        {/* Modal Scrollable Content */}
        <div className="p-5 overflow-y-auto space-y-5">
          {/* Price & Prep time */}
          <div className="flex items-center justify-between">
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-black text-gray-900">
                {formatMoney(finalUnitPrice, merchant.currencySymbol)}
              </span>
              {item.discountPrice && (
                <span className="text-sm text-gray-400 line-through">
                  {formatMoney(item.price, merchant.currencySymbol)}
                </span>
              )}
            </div>
            <div className="flex items-center gap-1.5 text-xs font-semibold text-gray-500 bg-gray-100 px-3 py-1.5 rounded-full">
              <Clock className="w-3.5 h-3.5 text-amber-500" />
              <span>~{item.preparationTimeMinutes} mins prep</span>
            </div>
          </div>

          <p className="text-sm text-gray-600 leading-relaxed">
            {item.description}
          </p>

          {/* Quick Note Options */}
          <div>
            <label className="block text-xs font-bold text-gray-900 uppercase tracking-wider mb-2 flex items-center gap-1.5">
              <Sparkles className="w-3.5 h-3.5 text-[#E60028]" />
              Quick Customizations
            </label>
            <div className="flex flex-wrap gap-2">
              {quickNoteOptions.map((note) => {
                const isSelected = selectedQuickNotes.includes(note);
                return (
                  <button
                    key={note}
                    type="button"
                    onClick={() => toggleQuickNote(note)}
                    className={`px-3 py-1.5 rounded-xl text-xs font-medium border transition-all ${
                      isSelected
                        ? 'bg-red-50 border-[#E60028] text-[#E60028] font-bold shadow-xs'
                        : 'bg-gray-50 border-gray-200 text-gray-700 hover:border-gray-300'
                    }`}
                  >
                    {isSelected ? '✓ ' : '+ '}{note}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Special Instructions Input */}
          <div>
            <label className="block text-xs font-bold text-gray-900 uppercase tracking-wider mb-1.5">
              Additional Notes for Kitchen
            </label>
            <textarea
              value={specialNotes}
              onChange={(e) => setSpecialNotes(e.target.value)}
              placeholder="e.g. Extra hot, dressing on the side, no cutlery needed..."
              rows={2}
              className="w-full text-xs p-3 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028] transition-all"
            />
          </div>

          {/* Quantity Controls */}
          <div className="flex items-center justify-between pt-2">
            <span className="text-sm font-bold text-gray-900">Select Quantity</span>
            <div className="flex items-center gap-3 bg-gray-100 p-1.5 rounded-2xl border border-gray-200">
              <button
                type="button"
                onClick={() => setQuantity(Math.max(1, quantity - 1))}
                className="w-9 h-9 rounded-xl bg-white shadow-xs text-gray-700 flex items-center justify-center hover:bg-gray-50 disabled:opacity-40"
                disabled={quantity <= 1}
              >
                <Minus className="w-4 h-4" />
              </button>
              <span className="w-8 text-center text-base font-extrabold text-gray-900">{quantity}</span>
              <button
                type="button"
                onClick={() => setQuantity(quantity + 1)}
                className="w-9 h-9 rounded-xl bg-white shadow-xs text-gray-700 flex items-center justify-center hover:bg-gray-50"
              >
                <Plus className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>

        {/* Submit Footer */}
        <div className="p-4 bg-gray-50 border-t border-gray-100">
          <button
            onClick={handleAdd}
            className="w-full py-3.5 px-6 bg-[#E60028] hover:bg-[#CC0024] text-white font-bold rounded-2xl shadow-lg shadow-red-500/20 flex items-center justify-between transition-all"
          >
            <span className="flex items-center gap-2">
              <ShoppingBag className="w-5 h-5" />
              Add to Order
            </span>
            <span className="text-base font-black">
              {formatMoney(totalPrice, merchant.currencySymbol)}
            </span>
          </button>
        </div>

      </div>
    </div>
  );
};
