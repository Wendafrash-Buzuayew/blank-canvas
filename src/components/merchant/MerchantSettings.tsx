import React, { useState } from 'react';
import { Store, Save, Building, Phone, MapPin, Globe, CheckCircle2 } from 'lucide-react';
import { Merchant, BusinessCategory } from '../../types';

interface MerchantSettingsProps {
  merchant: Merchant;
  onUpdateMerchant: (updated: Merchant) => void;
}

export const MerchantSettings: React.FC<MerchantSettingsProps> = ({
  merchant,
  onUpdateMerchant,
}) => {
  const [formData, setFormData] = useState<Merchant>(merchant);
  const [savedSuccess, setSavedSuccess] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onUpdateMerchant(formData);
    setSavedSuccess(true);
    setTimeout(() => setSavedSuccess(false), 3000);
  };

  const categories: BusinessCategory[] = [
    'Restaurant', 'Coffee Shop', 'Bar', 'Hotel', 'Fast Food', 'Lounge', 'Bakery'
  ];

  return (
    <div className="space-y-6 max-w-3xl">

      <div className="bg-white p-5 rounded-3xl border border-gray-100 shadow-xs flex items-center justify-between">
        <div>
          <h2 className="text-xl font-black text-gray-900 flex items-center gap-2">
            <Store className="w-6 h-6 text-[#E60028]" />
            Restaurant & Branch Profile Settings
          </h2>
          <p className="text-xs text-gray-500 mt-0.5">Configure business info, logo, cover photo, currency symbol, and contact details</p>
        </div>

        {savedSuccess && (
          <span className="px-3 py-1.5 rounded-xl bg-emerald-100 text-emerald-800 text-xs font-bold flex items-center gap-1.5 animate-in fade-in">
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
            Changes Saved!
          </span>
        )}
      </div>

      <form onSubmit={handleSubmit} className="bg-white p-6 rounded-3xl border border-gray-100 shadow-xs space-y-5">
        
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          
          <div>
            <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Business Name</label>
            <input
              type="text"
              required
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              className="w-full text-xs p-3 rounded-xl border border-gray-200 focus:ring-2 focus:ring-[#E60028]/20"
            />
          </div>

          <div>
            <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Business Category</label>
            <select
              value={formData.category}
              onChange={(e) => setFormData({ ...formData, category: e.target.value as BusinessCategory })}
              className="w-full text-xs p-3 rounded-xl border border-gray-200"
            >
              {categories.map(c => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Currency Code & Symbol</label>
            <div className="flex gap-2">
              <input
                type="text"
                value={formData.currency}
                onChange={(e) => setFormData({ ...formData, currency: e.target.value })}
                placeholder="USD"
                className="w-1/2 text-xs p-3 rounded-xl border border-gray-200"
              />
              <input
                type="text"
                value={formData.currencySymbol}
                onChange={(e) => setFormData({ ...formData, currencySymbol: e.target.value })}
                placeholder="$"
                className="w-1/2 text-xs p-3 rounded-xl border border-gray-200"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Phone Number</label>
            <input
              type="text"
              value={formData.phone}
              onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
              className="w-full text-xs p-3 rounded-xl border border-gray-200"
            />
          </div>

          <div className="sm:col-span-2">
            <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Physical Address & City</label>
            <input
              type="text"
              value={formData.address}
              onChange={(e) => setFormData({ ...formData, address: e.target.value })}
              placeholder="Address"
              className="w-full text-xs p-3 rounded-xl border border-gray-200"
            />
          </div>

          <div className="sm:col-span-2">
            <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Logo Image URL</label>
            <input
              type="text"
              value={formData.logo}
              onChange={(e) => setFormData({ ...formData, logo: e.target.value })}
              className="w-full text-xs p-3 rounded-xl border border-gray-200"
            />
          </div>

          <div className="sm:col-span-2">
            <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Cover Image URL</label>
            <input
              type="text"
              value={formData.coverImage}
              onChange={(e) => setFormData({ ...formData, coverImage: e.target.value })}
              className="w-full text-xs p-3 rounded-xl border border-gray-200"
            />
          </div>

          <div className="sm:col-span-2">
            <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Business Description</label>
            <textarea
              rows={3}
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              className="w-full text-xs p-3 rounded-xl border border-gray-200"
            />
          </div>

        </div>

        <button
          type="submit"
          className="py-3 px-6 bg-[#E60028] hover:bg-[#CC0024] text-white font-extrabold text-xs rounded-xl shadow-md flex items-center gap-2 transition-all"
        >
          <Save className="w-4 h-4" />
          Save Business Profile Changes
        </button>
      </form>

    </div>
  );
};
