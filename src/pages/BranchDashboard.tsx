import React, { useMemo } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import { getRoleLabel } from '../router/ProtectedRoute';
import { Spinner, ErrorState } from '../components/ui/States';
import { Navbar } from '../components/Navbar';
import { useKitchenOrders, useTables, useMerchant, useTodayAnalytics } from '../hooks/useApiData';
import { LayoutDashboard, ShoppingBag, Table as TableIcon, Users, Bell, TrendingUp } from 'lucide-react';

export const BranchDashboard: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const merchantId = user?.merchantId;

  const { data: merchantData, isLoading: merchantLoading } = useMerchant(merchantId);
  const { data: ordersData, isLoading: ordersLoading } = useKitchenOrders();
  const { data: tablesData } = useTables();
  const { data: todayAnalytics } = useTodayAnalytics(merchantId);

  const merchant = useMemo(() => {
    if (!merchantData) return null;
    return {
      id: merchantData.id,
      name: merchantData.name,
      slug: merchantData.slug,
      category: merchantData.category as any,
      logo: merchantData.logoUrl || '',
      coverImage: '',
      phone: merchantData.phone,
      address: merchantData.address,
      city: merchantData.city,
      description: '',
      currency: 'ETB',
      currencySymbol: 'Br',
      createdAt: merchantData.createdAt,
    };
  }, [merchantData]);

  const fallbackMerchant = {
    id: 'demo',
    name: 'QRServe Branch',
    slug: 'qrserve',
    category: 'Restaurant' as const,
    logo: '',
    coverImage: '',
    phone: '',
    address: '',
    city: '',
    description: '',
    currency: 'ETB',
    currencySymbol: 'Br',
    createdAt: '',
  };

  const activeMerchant = merchant || fallbackMerchant;

  const orders = ordersData || [];
  const tables = tablesData || [];
  const pendingOrders = orders.filter(o => o.status === 'PENDING' || o.status === 'ACCEPTED');
  const occupiedTables = tables.filter(t => t.status === 'OCCUPIED').length;

  if (merchantLoading || ordersLoading) {
    return (
      <div className="min-h-screen bg-slate-50">
        <Navbar
          currentView="merchant"
          setCurrentView={(v) => { if (v === 'landing') navigate('/'); }}
          activeRole={(user?.role.toLowerCase() as any) || 'manager'}
          setActiveRole={() => {}}
          selectedMerchant={activeMerchant as any}
          merchants={merchant ? [merchant] as any : []}
          setSelectedMerchant={() => {}}
          activeTableNumber="1"
          setActiveTableNumber={() => {}}
          activeTab="branch"
          setActiveTab={() => {}}
          onResetData={() => {}}
          pendingOrdersCount={0}
          isAuthenticated={true}
          onLoginClick={() => {}}
          onLogout={() => { logout(); navigate('/login'); }}
        />
        <Spinner label="Loading branch dashboard..." />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar
        currentView="merchant"
        setCurrentView={(v) => { if (v === 'landing') navigate('/'); }}
        activeRole={(user?.role.toLowerCase() as any) || 'manager'}
        setActiveRole={() => {}}
        selectedMerchant={activeMerchant as any}
        merchants={merchant ? [merchant] as any : []}
        setSelectedMerchant={() => {}}
        activeTableNumber="1"
        setActiveTableNumber={() => {}}
        activeTab="branch"
        setActiveTab={() => {}}
        onResetData={() => {}}
        pendingOrdersCount={pendingOrders.length}
        isAuthenticated={true}
        onLoginClick={() => {}}
        onLogout={() => { logout(); navigate('/login'); }}
      />
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        <div className="mb-6 flex items-center justify-between bg-indigo-50 border border-indigo-200 rounded-xl px-4 py-3">
          <div className="flex items-center gap-2 text-xs font-bold text-indigo-800">
            <span className="px-2 py-0.5 bg-indigo-600 text-white rounded-md uppercase tracking-wider text-[10px]">
              {getRoleLabel(user?.role || 'BRANCH_MANAGER')}
            </span>
            <span>Branch Dashboard</span>
          </div>
          <span className="text-[11px] text-indigo-600 font-mono">{user?.email}</span>
        </div>

        {/* Branch KPIs */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="p-5 bg-white rounded-2xl border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold text-slate-500 uppercase">Today's Revenue</span>
              <TrendingUp className="w-4 h-4 text-emerald-500" />
            </div>
            <p className="text-2xl font-black text-slate-900">
              {todayAnalytics ? Number(todayAnalytics.todayRevenue).toFixed(2) : '0.00'} ETB
            </p>
          </div>

          <div className="p-5 bg-white rounded-2xl border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold text-slate-500 uppercase">Active Orders</span>
              <ShoppingBag className="w-4 h-4 text-[#E60028]" />
            </div>
            <p className="text-2xl font-black text-slate-900">{pendingOrders.length}</p>
            <span className="text-xs text-amber-600 font-semibold">{pendingOrders.length} need attention</span>
          </div>

          <div className="p-5 bg-white rounded-2xl border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold text-slate-500 uppercase">Table Occupancy</span>
              <TableIcon className="w-4 h-4 text-indigo-500" />
            </div>
            <p className="text-2xl font-black text-slate-900">{occupiedTables}/{tables.length}</p>
          </div>

          <div className="p-5 bg-white rounded-2xl border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold text-slate-500 uppercase">Waiters</span>
              <Users className="w-4 h-4 text-slate-400" />
            </div>
            <p className="text-2xl font-black text-slate-900">--</p>
            <span className="text-xs text-slate-400">Waiter assignment coming soon</span>
          </div>
        </div>

        {/* Branch Orders View */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-base font-black text-slate-900 mb-4 flex items-center gap-2">
            <Bell className="w-4 h-4 text-[#E60028]" />
            Branch Active Orders
          </h3>
          {pendingOrders.length === 0 ? (
            <p className="text-xs text-slate-400 py-6 text-center">No active orders for this branch.</p>
          ) : (
            <div className="divide-y divide-slate-100">
              {pendingOrders.map(order => (
                <div key={order.id} className="py-3 flex items-center justify-between">
                  <div>
                    <span className="font-black text-sm text-slate-900">#{order.orderNumber}</span>
                    <span className="text-xs text-slate-500 ml-2">Table {order.tableNumber || order.tableId}</span>
                  </div>
                  <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                    order.status === 'PENDING' ? 'bg-red-50 text-[#E60028]' : 'bg-blue-50 text-blue-700'
                  }`}>
                    {order.status}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};