import React from 'react';
import { 
  ShieldCheck, 
  Store, 
  DollarSign, 
  Layers, 
  Building2, 
  CheckCircle2, 
  Award,
  Sparkles,
  TrendingUp,
  Zap
} from 'lucide-react';
import { Merchant } from '../../types';
import { formatMoney } from '../../lib/utils';

interface SuperAdminViewProps {
  merchants: Merchant[];
  onSelectMerchant: (merchant: Merchant) => void;
}

export const SuperAdminView: React.FC<SuperAdminViewProps> = ({
  merchants,
  onSelectMerchant,
}) => {
  const totalTenants = merchants.length;
  const platformGMV = 142500; // Simulated platform sales volume

  const subscriptionPlans = [
    { name: 'Free Tier', price: '$0', limit: 'Up to 5 Tables', activeCount: 120, badge: 'Popular Starter' },
    { name: 'Standard SaaS', price: '$29/mo', limit: 'Up to 50 Tables', activeCount: 450, badge: 'Most Selected' },
    { name: 'Premium Pro', price: '$79/mo', limit: 'Unlimited Tables & KDS', activeCount: 180, badge: 'High Volume' },
    { name: 'Enterprise Hotel Chain', price: 'Custom', limit: 'Multi-Branch & API', activeCount: 24, badge: 'White Label' },
  ];

  return (
    <div className="space-y-6">

      {/* Top Banner */}
      <div className="bg-gradient-to-r from-indigo-900 via-indigo-800 to-gray-900 text-white p-6 rounded-3xl shadow-xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="px-2.5 py-0.5 rounded-full text-[10px] font-extrabold bg-indigo-500 text-white uppercase tracking-wider">
              QRServe Super Admin Portal
            </span>
            <span className="text-xs text-indigo-200">Platform Multi-Tenant Overview</span>
          </div>
          <h2 className="text-2xl font-black">Platform SaaS Governance</h2>
          <p className="text-xs text-indigo-100 mt-1 max-w-lg">
            Monitor registered restaurant tenants, global platform GMV, subscription plans, and white-label deployments.
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <div className="px-4 py-2 bg-white/10 rounded-xl border border-white/20 text-xs font-bold text-white flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            Platform Status: Healthy
          </div>
        </div>
      </div>

      {/* Metrics Row */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="p-5 bg-white rounded-3xl border border-gray-100 shadow-xs">
          <span className="text-xs font-bold text-gray-400 uppercase">Active Merchant Tenants</span>
          <div className="text-2xl font-black text-gray-900 mt-1">{totalTenants} Restaurants & Cafes</div>
          <span className="text-xs text-emerald-600 font-bold mt-1 block">+14 new this week</span>
        </div>

        <div className="p-5 bg-white rounded-3xl border border-gray-100 shadow-xs">
          <span className="text-xs font-bold text-gray-400 uppercase">Total Platform GMV Processed</span>
          <div className="text-2xl font-black text-gray-900 mt-1">{formatMoney(platformGMV)}</div>
          <span className="text-xs text-indigo-600 font-semibold mt-1 block">Across 774 active tables</span>
        </div>

        <div className="p-5 bg-white rounded-3xl border border-gray-100 shadow-xs">
          <span className="text-xs font-bold text-gray-400 uppercase">MRR SaaS Revenue</span>
          <div className="text-2xl font-black text-[#E60028] mt-1">$27,270 / mo</div>
          <span className="text-xs text-gray-500 mt-1 block">Subscriptions & Commission</span>
        </div>
      </div>

      {/* Merchant Tenants List */}
      <div className="bg-white rounded-3xl p-6 border border-gray-100 shadow-xs space-y-4">
        <h3 className="text-lg font-black text-gray-900 flex items-center gap-2">
          <Store className="w-5 h-5 text-[#E60028]" />
          Registered Merchant Accounts
        </h3>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {merchants.map(m => (
            <div key={m.id} className="p-4 rounded-2xl border border-gray-100 bg-gray-50 space-y-3">
              <div className="flex items-center gap-3">
                <img src={m.logo} alt={m.name} className="w-12 h-12 rounded-xl object-cover border bg-white" />
                <div>
                  <h4 className="font-extrabold text-sm text-gray-900">{m.name}</h4>
                  <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-gray-200 text-gray-700">
                    {m.category}
                  </span>
                </div>
              </div>
              <p className="text-xs text-gray-500 line-clamp-2">{m.description}</p>
              
              <button
                onClick={() => onSelectMerchant(m)}
                className="w-full py-2 bg-gray-900 hover:bg-black text-white text-xs font-bold rounded-xl transition-colors"
              >
                Inspect Merchant Dashboard
              </button>
            </div>
          ))}
        </div>
      </div>

      {/* Subscription Plans Breakdown */}
      <div className="bg-white rounded-3xl p-6 border border-gray-100 shadow-xs space-y-4">
        <h3 className="text-lg font-black text-gray-900 flex items-center gap-2">
          <Zap className="w-5 h-5 text-amber-500" />
          SaaS Monetization Tiers
        </h3>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {subscriptionPlans.map((plan, idx) => (
            <div key={idx} className="p-4 rounded-2xl border border-gray-100 bg-gray-50/70 space-y-2">
              <span className="text-[10px] font-extrabold uppercase px-2 py-0.5 bg-red-100 text-[#E60028] rounded-md">
                {plan.badge}
              </span>
              <h4 className="font-black text-base text-gray-900">{plan.name}</h4>
              <div className="text-xl font-black text-[#E60028]">{plan.price}</div>
              <div className="text-xs text-gray-500 font-medium">{plan.limit}</div>
              <div className="pt-2 text-xs font-bold text-gray-700 border-t border-gray-200">
                {plan.activeCount} Active Tenants
              </div>
            </div>
          ))}
        </div>
      </div>

    </div>
  );
};
