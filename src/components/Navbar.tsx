import React from 'react';
import { 
  QrCode, 
  Store, 
  ChefHat, 
  User, 
  Sparkles, 
  LayoutDashboard, 
  CookingPot, 
  UtensilsCrossed, 
  ShieldCheck, 
  RotateCcw,
  Smartphone,
  ChevronDown,
  LogIn,
  LogOut
} from 'lucide-react';
import { UserRole, Merchant } from '../types';

interface NavbarProps {
  currentView: 'landing' | 'customer' | 'merchant' | 'kitchen' | 'superadmin';
  setCurrentView: (view: 'landing' | 'customer' | 'merchant' | 'kitchen' | 'superadmin') => void;
  activeRole: UserRole;
  setActiveRole: (role: UserRole) => void;
  selectedMerchant: Merchant;
  merchants: Merchant[];
  setSelectedMerchant: (merchant: Merchant) => void;
  activeTableNumber: string;
  setActiveTableNumber: (tableNum: string) => void;
  activeTab: string;
  setActiveTab: (tab: string) => void;
  onResetData: () => void;
  pendingOrdersCount: number;
  isAuthenticated: boolean;
  onLoginClick: () => void;
  onLogout: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  currentView,
  setCurrentView,
  activeRole,
  setActiveRole,
  selectedMerchant,
  merchants,
  setSelectedMerchant,
  activeTableNumber,
  setActiveTableNumber,
  activeTab,
  setActiveTab,
  onResetData,
  pendingOrdersCount,
  isAuthenticated,
  onLoginClick,
  onLogout
}) => {
  return (
    <header className="sticky top-0 z-40 bg-white border-b border-slate-200 shadow-xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16 gap-4">
          
          {/* Logo & Brand */}
          <div className="flex items-center gap-3">
            <button 
              onClick={() => setCurrentView('landing')}
              className="flex items-center gap-2.5 text-left group transition-all"
            >
              <div className="w-9 h-9 rounded-lg bg-[#E60028] flex items-center justify-center text-white shadow-sm group-hover:bg-[#CC0024] transition-colors">
                <QrCode className="w-5 h-5" />
              </div>
              <div>
                <span className="font-bold text-lg tracking-tight text-slate-900 flex items-center gap-1.5">
                  QRServe <span className="text-[#E60028] text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 bg-rose-50 rounded border border-rose-100">SaaS</span>
                </span>
                <span className="text-[11px] text-slate-500 block -mt-0.5">Smart QR Menu & Ordering</span>
              </div>
            </button>
          </div>

          {/* Primary View Selector Switcher */}
          <div className="hidden md:flex items-center gap-1 bg-slate-100 p-1 rounded-lg border border-slate-200/80">
            <button
              onClick={() => setCurrentView('landing')}
              className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-md transition-all ${
                currentView === 'landing' 
                  ? 'bg-white text-slate-900 shadow-xs border border-slate-200/60' 
                  : 'text-slate-600 hover:text-slate-900 hover:bg-white/50'
              }`}
            >
              <Sparkles className="w-3.5 h-3.5 text-amber-500" />
              Landing Page
            </button>

            <button
              onClick={() => {
                setCurrentView('customer');
              }}
              className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-md transition-all ${
                currentView === 'customer' 
                  ? 'bg-[#E60028] text-white shadow-sm' 
                  : 'text-slate-600 hover:text-slate-900 hover:bg-white/50'
              }`}
            >
              <Smartphone className="w-3.5 h-3.5" />
              Customer Menu (QR)
            </button>

            <button
              onClick={() => {
                setCurrentView('merchant');
                if (activeTab === 'landing') setActiveTab('dashboard');
              }}
              className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-md transition-all ${
                currentView === 'merchant' 
                  ? 'bg-[#1E1E1E] text-white shadow-sm' 
                  : 'text-slate-600 hover:text-slate-900 hover:bg-white/50'
              }`}
            >
              <LayoutDashboard className="w-3.5 h-3.5" />
              Merchant Portal
              {pendingOrdersCount > 0 && (
                <span className="w-4 h-4 bg-[#E60028] text-white text-[10px] font-bold rounded-full flex items-center justify-center animate-pulse">
                  {pendingOrdersCount}
                </span>
              )}
            </button>

            <button
              onClick={() => setCurrentView('kitchen')}
              className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-md transition-all ${
                currentView === 'kitchen' 
                  ? 'bg-emerald-600 text-white shadow-sm' 
                  : 'text-slate-600 hover:text-slate-900 hover:bg-white/50'
              }`}
            >
              <CookingPot className="w-3.5 h-3.5" />
              Kitchen KDS
            </button>

            <button
              onClick={() => setCurrentView('superadmin')}
              className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-md transition-all ${
                currentView === 'superadmin' 
                  ? 'bg-indigo-600 text-white shadow-sm' 
                  : 'text-slate-600 hover:text-slate-900 hover:bg-white/50'
              }`}
            >
              <ShieldCheck className="w-3.5 h-3.5" />
              Super Admin
            </button>
          </div>

          {/* Right Controls: Auth + Merchant Switcher + Role Selector */}
          <div className="flex items-center gap-2">
            
            {/* Merchant Switcher */}
            <div className="relative group">
              <div className="flex items-center gap-2 px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs font-medium text-slate-700 cursor-pointer hover:border-slate-300 transition-colors">
                <Store className="w-3.5 h-3.5 text-slate-500" />
                <span className="max-w-[120px] truncate">{selectedMerchant.name}</span>
                <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
              </div>
              <div className="absolute right-0 top-full mt-1 w-56 bg-white rounded-xl shadow-lg border border-slate-200 p-1.5 hidden group-hover:block z-50 animate-in fade-in slide-in-from-top-1 duration-150">
                <div className="px-3 py-1.5 border-b border-slate-100 text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                  Select Restaurant
                </div>
                {merchants.length === 0 ? (
                  <div className="px-3 py-3 text-xs text-slate-400 text-center">
                    No merchants available. Please sign in.
                  </div>
                ) : (
                  merchants.map((m) => (
                    <button
                      key={m.id}
                      onClick={() => setSelectedMerchant(m)}
                      className={`w-full text-left px-3 py-2 rounded-md text-xs flex items-center justify-between transition-colors ${
                        m.id === selectedMerchant.id ? 'bg-rose-50 text-[#E60028] font-bold' : 'hover:bg-slate-50 text-slate-700'
                      }`}
                    >
                      <span className="truncate">{m.name}</span>
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-500">{m.category}</span>
                    </button>
                  ))
                )}
              </div>
            </div>

            {/* Role Demo Switcher */}
            <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 bg-amber-50 border border-amber-200 rounded-lg">
              <User className="w-3.5 h-3.5 text-amber-600" />
              <select
                value={activeRole}
                onChange={(e) => setActiveRole(e.target.value as UserRole)}
                className="bg-transparent text-xs font-semibold text-amber-900 border-none focus:ring-0 cursor-pointer py-0.5"
              >
                <option value="merchant_owner">Merchant Owner</option>
                <option value="manager">Manager</option>
                <option value="kitchen">Kitchen Staff</option>
                <option value="waiter">Waiter / Cashier</option>
                <option value="customer">Customer</option>
                <option value="super_admin">Super Admin</option>
              </select>
            </div>

            {/* Auth / Login Button */}
            {isAuthenticated ? (
              <button
                onClick={onLogout}
                title="Sign Out"
                className="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors flex items-center gap-1.5 text-xs font-semibold"
              >
                <LogOut className="w-4 h-4" />
                <span className="hidden lg:inline">Sign Out</span>
              </button>
            ) : (
              <button
                onClick={onLoginClick}
                title="Sign In"
                className="px-3 py-2 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-bold rounded-lg transition-colors flex items-center gap-1.5 shadow-sm"
              >
                <LogIn className="w-4 h-4" />
                <span className="hidden lg:inline">Sign In</span>
              </button>
            )}

            {/* Reset Data Button */}
            <button
              onClick={() => {
                if (confirm('Refresh data from the backend server?')) {
                  onResetData();
                }
              }}
              title="Refresh Data"
              className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
            >
              <RotateCcw className="w-4 h-4" />
            </button>
          </div>

        </div>

        {/* Mobile Navigation Row */}
        <div className="flex md:hidden items-center justify-between py-2 border-t border-slate-100 overflow-x-auto gap-2 text-xs">
          <button
            onClick={() => setCurrentView('landing')}
            className={`px-3 py-1.5 rounded-md whitespace-nowrap font-medium ${currentView === 'landing' ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'}`}
          >
            Landing
          </button>
          <button
            onClick={() => setCurrentView('customer')}
            className={`px-3 py-1.5 rounded-md whitespace-nowrap font-medium ${currentView === 'customer' ? 'bg-[#E60028] text-white' : 'bg-slate-100 text-slate-700'}`}
          >
            Customer Menu
          </button>
          <button
            onClick={() => setCurrentView('merchant')}
            className={`px-3 py-1.5 rounded-md whitespace-nowrap font-medium ${currentView === 'merchant' ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'}`}
          >
            Merchant Portal
          </button>
          <button
            onClick={() => setCurrentView('kitchen')}
            className={`px-3 py-1.5 rounded-md whitespace-nowrap font-medium ${currentView === 'kitchen' ? 'bg-emerald-600 text-white' : 'bg-slate-100 text-slate-700'}`}
          >
            Kitchen KDS
          </button>
        </div>

      </div>
    </header>
  );
};