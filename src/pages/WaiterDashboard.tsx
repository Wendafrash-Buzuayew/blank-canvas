import React, { useMemo } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import { getRoleLabel } from '../router/ProtectedRoute';
import { Spinner } from '../components/ui/States';
import { Navbar } from '../components/Navbar';
import { useKitchenOrders, useTables, useMerchant } from '../hooks/useApiData';
import { Table as TableIcon, Bell, CheckCircle2, ClipboardList, Users } from 'lucide-react';

export const WaiterDashboard: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const merchantId = user?.merchantId;

  const { data: merchantData, isLoading } = useMerchant(merchantId);
  const { data: ordersData } = useKitchenOrders();
  const { data: tablesData } = useTables();

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
    name: 'QRServe Waiter',
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

  const activeMerchant = (merchant || fallbackMerchant) as any;
  const orders = ordersData || [];
  const tables = tablesData || [];
  const readyOrders = orders.filter(o => o.status === 'READY');
  const preparingOrders = orders.filter(o => o.status === 'PREPARING');

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-50">
        <Navbar
          currentView="merchant"
          setCurrentView={(v) => { if (v === 'landing') navigate('/'); }}
          activeRole={(user?.role.toLowerCase() as any) || 'waiter'}
          setActiveRole={() => {}}
          selectedMerchant={activeMerchant}
          merchants={[activeMerchant]}
          setSelectedMerchant={() => {}}
          activeTableNumber="1"
          setActiveTableNumber={() => {}}
          activeTab="waiter"
          setActiveTab={() => {}}
          onResetData={() => {}}
          pendingOrdersCount={0}
          isAuthenticated={true}
          onLoginClick={() => {}}
          onLogout={() => { logout(); navigate('/login'); }}
        />
        <Spinner label="Loading waiter dashboard..." />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar
        currentView="merchant"
        setCurrentView={(v) => { if (v === 'landing') navigate('/'); }}
        activeRole={(user?.role.toLowerCase() as any) || 'waiter'}
        setActiveRole={() => {}}
        selectedMerchant={activeMerchant}
        merchants={[activeMerchant]}
        setSelectedMerchant={() => {}}
        activeTableNumber="1"
        setActiveTableNumber={() => {}}
        activeTab="waiter"
        setActiveTab={() => {}}
        onResetData={() => {}}
        pendingOrdersCount={readyOrders.length + preparingOrders.length}
        isAuthenticated={true}
        onLoginClick={() => {}}
        onLogout={() => { logout(); navigate('/login'); }}
      />
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        <div className="mb-6 flex items-center justify-between bg-indigo-50 border border-indigo-200 rounded-xl px-4 py-3">
          <div className="flex items-center gap-2 text-xs font-bold text-indigo-800">
            <span className="px-2 py-0.5 bg-indigo-600 text-white rounded-md uppercase tracking-wider text-[10px]">
              {getRoleLabel(user?.role || 'WAITER')}
            </span>
            <span>Waiter Dashboard</span>
          </div>
          <span className="text-[11px] text-indigo-600 font-mono">{user?.email}</span>
        </div>

        {/* Waiter KPIs */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="p-5 bg-white rounded-2xl border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold text-slate-500 uppercase">Assigned Tables</span>
              <TableIcon className="w-4 h-4 text-indigo-500" />
            </div>
            <p className="text-2xl font-black text-slate-900">{tables.filter(t => t.status === 'OCCUPIED').length}</p>
            <span className="text-xs text-slate-400">Occupied tables</span>
          </div>

          <div className="p-5 bg-white rounded-2xl border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold text-slate-500 uppercase">Orders Ready</span>
              <CheckCircle2 className="w-4 h-4 text-emerald-500" />
            </div>
            <p className="text-2xl font-black text-emerald-600">{readyOrders.length}</p>
            <span className="text-xs text-slate-400">Ready to serve</span>
          </div>

          <div className="p-5 bg-white rounded-2xl border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold text-slate-500 uppercase">In Preparation</span>
              <ClipboardList className="w-4 h-4 text-amber-500" />
            </div>
            <p className="text-2xl font-black text-amber-600">{preparingOrders.length}</p>
            <span className="text-xs text-slate-400">Being prepared</span>
          </div>

          <div className="p-5 bg-white rounded-2xl border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold text-slate-500 uppercase">Customer Requests</span>
              <Bell className="w-4 h-4 text-[#E60028]" />
            </div>
            <p className="text-2xl font-black text-slate-900">0</p>
            <span className="text-xs text-slate-400">Request feature coming soon</span>
          </div>
        </div>

        {/* Active Orders for Waiters */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-base font-black text-slate-900 mb-4">My Active Orders</h3>
          <div className="divide-y divide-slate-100">
            {orders.length === 0 ? (
              <p className="text-xs text-slate-400 py-6 text-center">No active orders.</p>
            ) : (
              orders.filter(o => !['PAID', 'CANCELLED'].includes(o.status)).map(order => (
                <div key={order.id} className="py-3 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <span className="w-8 h-8 rounded-lg bg-gray-900 text-white text-xs font-black flex items-center justify-center">
                      #{order.orderNumber}
                    </span>
                    <div>
                      <div className="text-sm font-black text-slate-900">Table {order.tableNumber || order.tableId}</div>
                      <div className="text-xs text-slate-500">{order.items.length} items • {order.customerName || 'Guest'}</div>
                    </div>
                  </div>
                  <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                    order.status === 'READY' ? 'bg-emerald-50 text-emerald-700' :
                    order.status === 'PREPARING' ? 'bg-amber-50 text-amber-700' :
                    'bg-red-50 text-[#E60028]'
                  }`}>
                    {order.status}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};