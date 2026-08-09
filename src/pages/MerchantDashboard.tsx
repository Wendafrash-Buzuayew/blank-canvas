import React, { useMemo } from 'react';
import { 
  LayoutDashboard, 
  ShoppingBag, 
  Utensils, 
  Table as TableIcon, 
  QrCode, 
  BarChart3, 
  Settings 
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import { getRoleLabel } from '../router/ProtectedRoute';
import { DashboardOverview } from '../components/merchant/DashboardOverview';
import { OrderManagement } from '../components/merchant/OrderManagement';
import { MenuManager } from '../components/merchant/MenuManager';
import { TableManager } from '../components/merchant/TableManager';
import { QRDesigner } from '../components/merchant/QRDesigner';
import { AnalyticsView } from '../components/merchant/AnalyticsView';
import { MerchantSettings } from '../components/merchant/MerchantSettings';
import { Spinner, ErrorState } from '../components/ui/States';
import { Navbar } from '../components/Navbar';
import {
  useMenu,
  useTables,
  useTodayAnalytics,
  usePopularItems,
  useRevenueAnalytics,
  useKitchenOrders,
  useUpdateOrderStatus,
  useMerchant,
  mapMenuToFrontend,
} from '../hooks/useApiData';
import { tableApi } from '../lib/api';
import type { TableStatus, OrderStatus, QRDesignConfig } from '../types';

export const MerchantDashboard: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [merchantTab, setMerchantTab] = React.useState('dashboard');
  const [activeTableNumber, setActiveTableNumber] = React.useState('1');

  const merchantId = user?.merchantId;

  // Backend queries
  const { data: merchantData, isLoading: merchantLoading, error: merchantError, refetch: refetchMerchant } = useMerchant(merchantId);
  const { data: tablesData, isLoading: tablesLoading } = useTables();
  const { data: menuData, isLoading: menuLoading } = useMenu(merchantId);
  const { data: todayAnalytics } = useTodayAnalytics(merchantId);
  const { data: popularItems } = usePopularItems(merchantId);
  const { data: revenueData } = useRevenueAnalytics(merchantId);
  const { data: ordersData, refetch: refetchOrders } = useKitchenOrders();
  const updateOrderStatusMutation = useUpdateOrderStatus();

  // QR Config persisted client-side
  const [qrConfig, setQrConfig] = React.useState<QRDesignConfig>(() => {
    try {
      const stored = localStorage.getItem('qrserve_qr_config');
      return stored ? JSON.parse(stored) : {
        primaryColor: '#E60028', accentColor: '#FFB000', backgroundColor: '#FFFFFF',
        patternColor: '#1E1E1E', frameStyle: 'card', showLogo: true,
        headerTitle: 'QRSERVE', subTitle: 'SCAN TO ORDER & PAY',
        callToAction: 'Point camera to view menu', template: 'Modern',
      };
    } catch {
      return {
        primaryColor: '#E60028', accentColor: '#FFB000', backgroundColor: '#FFFFFF',
        patternColor: '#1E1E1E', frameStyle: 'card', showLogo: true,
        headerTitle: 'QRSERVE', subTitle: 'SCAN TO ORDER & PAY',
        callToAction: 'Point camera to view menu', template: 'Modern',
      };
    }
  });

  // Convert backend merchant to frontend type
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

  const tables = useMemo(() => {
    if (!tablesData) return [];
    return tablesData.map(t => ({
      id: String(t.id),
      branchId: String(t.branchId),
      floorId: '',
      tableNumber: t.tableNumber,
      capacity: t.capacity,
      qrCodeUrl: '',
      status: (t.status === 'OCCUPIED' ? 'Occupied' : t.status === 'RESERVED' ? 'Reserved' : 'Available') as TableStatus,
    }));
  }, [tablesData]);

  const menuMapped = useMemo(() => {
    if (!menuData) return { categories: [], menuItems: [] };
    return mapMenuToFrontend(menuData);
  }, [menuData]);

  const orders = useMemo(() => {
    if (!ordersData) return [];
    return ordersData.map(o => ({
      id: o.id,
      orderNumber: o.orderNumber,
      merchantId: o.merchantId,
      branchId: String(o.branchId),
      tableId: String(o.tableId),
      tableNumber: o.tableNumber || String(o.tableId),
      customerName: o.customerName,
      items: o.items.map(item => ({
        id: `${o.id}-${item.productId}`,
        menuItemId: String(item.productId),
        name: item.productName,
        price: Number(item.unitPrice),
        quantity: item.quantity,
        notes: item.notes,
      })),
      totalPrice: Number(o.totalAmount),
      status: (o.status === 'DELIVERED' ? 'Served' : o.status.charAt(0) + o.status.slice(1).toLowerCase()) as OrderStatus,
      notes: o.note,
      createdAt: o.createdAt,
      updatedAt: o.createdAt,
      paymentMethod: undefined,
      paymentStatus: undefined,
      estimatedPrepMinutes: 15,
    }));
  }, [ordersData]);

  const dashboardMetrics = useMemo(() => {
    if (!todayAnalytics) return null;
    return {
      todayRevenue: Number(todayAnalytics.todayRevenue),
      totalOrders: todayAnalytics.totalOrders,
      pendingOrders: todayAnalytics.pendingOrders,
      avgOrderValue: Number(todayAnalytics.avgOrderValue),
      occupiedTables: todayAnalytics.occupiedTables,
      totalTables: todayAnalytics.totalTables,
      occupancyRate: todayAnalytics.occupancyRate,
    };
  }, [todayAnalytics]);

  const mappedPopularItems = useMemo(() => {
    if (!popularItems) return [];
    return popularItems.map(p => ({
      name: p.name,
      image: p.image || '',
      count: p.count,
      revenue: Number(p.revenue),
    }));
  }, [popularItems]);

  const mappedRevenueHistory = useMemo(() => {
    if (!revenueData) return [];
    return revenueData.salesHistory.map(p => ({
      time: p.date,
      orders: p.ordersCount,
      revenue: Number(p.revenue),
    }));
  }, [revenueData]);

  const handleUpdateOrderStatus = (orderId: string, newStatus: OrderStatus) => {
    updateOrderStatusMutation.mutate({ id: orderId, status: newStatus.toUpperCase() });
  };

  const pendingCount = orders.filter(o => o.status === 'Pending' || o.status === 'Accepted').length;

  if (merchantLoading || tablesLoading || menuLoading) {
    return (
      <div className="min-h-screen bg-slate-50">
        <Navbar
          currentView="merchant"
          setCurrentView={(v) => { if (v === 'landing') navigate('/'); }}
          activeRole={(user?.role.toLowerCase() as any) || 'merchant_owner'}
          setActiveRole={() => {}}
          selectedMerchant={merchant || { id: 'loading', name: 'Loading...', slug: '', category: 'Restaurant' as any, logo: '', coverImage: '', phone: '', address: '', city: '', description: '', currency: 'ETB', currencySymbol: 'Br', createdAt: '' }}
          merchants={merchant ? [merchant] : []}
          setSelectedMerchant={() => {}}
          activeTableNumber={activeTableNumber}
          setActiveTableNumber={setActiveTableNumber}
          activeTab={merchantTab}
          setActiveTab={setMerchantTab}
          onResetData={() => { refetchOrders(); refetchMerchant(); }}
          pendingOrdersCount={pendingCount}
          isAuthenticated={true}
          onLoginClick={() => {}}
          onLogout={() => { logout(); navigate('/login'); }}
        />
        <Spinner label="Loading merchant dashboard..." />
      </div>
    );
  }

  if (merchantError) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
        <ErrorState
          message={`Unable to load merchant data: ${(merchantError as Error).message}`}
          onRetry={() => refetchMerchant()}
        />
      </div>
    );
  }

  if (!merchant) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
        <ErrorState message="No merchant found for this account. Please contact your administrator." />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar
        currentView="merchant"
        setCurrentView={(v) => { if (v === 'landing') navigate('/'); }}
        activeRole={(user?.role.toLowerCase() as any) || 'merchant_owner'}
        setActiveRole={() => {}}
        selectedMerchant={merchant}
        merchants={[merchant]}
        setSelectedMerchant={() => {}}
        activeTableNumber={activeTableNumber}
        setActiveTableNumber={setActiveTableNumber}
        activeTab={merchantTab}
        setActiveTab={setMerchantTab}
        onResetData={() => { refetchOrders(); refetchMerchant(); }}
        pendingOrdersCount={pendingCount}
        isAuthenticated={true}
        onLoginClick={() => {}}
        onLogout={() => { logout(); navigate('/login'); }}
      />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Role banner */}
        <div className="mb-6 flex items-center justify-between bg-indigo-50 border border-indigo-200 rounded-xl px-4 py-3">
          <div className="flex items-center gap-2 text-xs font-bold text-indigo-800">
            <span className="px-2 py-0.5 bg-indigo-600 text-white rounded-md uppercase tracking-wider text-[10px]">
              {getRoleLabel(user?.role || '')}
            </span>
            <span>Access Level: Merchant Dashboard</span>
          </div>
          <span className="text-[11px] text-indigo-600 font-mono">{user?.email}</span>
        </div>

        {/* Merchant Sub-Navigation Tabs */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-3 mb-6 no-scrollbar border-b border-slate-200 text-xs font-medium">
          {[
            { id: 'dashboard', label: 'Dashboard Overview', icon: LayoutDashboard },
            { id: 'orders', label: 'Incoming Orders', icon: ShoppingBag, badge: pendingCount },
            { id: 'menu', label: 'Menu Management', icon: Utensils },
            { id: 'tables', label: 'Table Management', icon: TableIcon },
            { id: 'qr-designer', label: 'QR Designer', icon: QrCode },
            { id: 'analytics', label: 'Sales Analytics', icon: BarChart3 },
            { id: 'settings', label: 'Merchant Settings', icon: Settings },
          ].map(tab => {
            const Icon = tab.icon;
            const isActive = merchantTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setMerchantTab(tab.id)}
                className={`px-4 py-2.5 rounded-lg font-semibold flex items-center gap-2 whitespace-nowrap transition-all ${
                  isActive
                    ? 'bg-[#E60028] text-white shadow-xs'
                    : 'bg-white text-slate-600 hover:text-slate-900 hover:bg-slate-100 border border-slate-200'
                }`}
              >
                <Icon className={`w-4 h-4 ${isActive ? 'text-white' : 'text-slate-400'}`} />
                {tab.label}
                {tab.badge && tab.badge > 0 ? (
                  <span className="w-4 h-4 rounded-full bg-white text-[#E60028] text-[10px] font-bold flex items-center justify-center">
                    {tab.badge}
                  </span>
                ) : null}
              </button>
            );
          })}
        </div>

        {/* Content Views */}
        {merchantTab === 'dashboard' && (
          <DashboardOverview
            merchant={merchant}
            orders={orders}
            tables={tables}
            menuItems={menuMapped.menuItems}
            popularItems={mappedPopularItems}
            metrics={dashboardMetrics}
            onNavigateTab={setMerchantTab}
          />
        )}

        {merchantTab === 'orders' && (
          <OrderManagement
            merchant={merchant}
            orders={orders}
            onUpdateOrderStatus={handleUpdateOrderStatus}
          />
        )}

        {merchantTab === 'menu' && (
          <MenuManager
            merchant={merchant}
            categories={menuMapped.categories}
            menuItems={menuMapped.menuItems}
            onSaveCategory={(cat) => console.log('Create category via API', cat)}
            onDeleteCategory={(catId) => console.log('Delete category via API', catId)}
            onSaveMenuItem={(item) => console.log('Save menu item via API', item)}
            onDeleteMenuItem={(itemId) => console.log('Delete item via API', itemId)}
            onToggleAvailability={(itemId) => console.log('Toggle availability via API', itemId)}
          />
        )}

        {merchantTab === 'tables' && (
          <TableManager
            merchant={merchant}
            branches={[]}
            floors={[]}
            tables={tables}
            onSaveTable={(tbl) => console.log('Save table via API', tbl)}
            onDeleteTable={(tblId) => console.log('Delete table via API', tblId)}
            onUpdateTableStatus={(tblId, status) => {
              const table = tables.find(t => t.id === tblId);
              if (table) {
                tableApi.updateTableStatus(Number(table.id), status.toUpperCase()).catch(err => console.error('Failed to update table status', err));
              }
            }}
            onOpenQRDesigner={(tbl) => {
              setActiveTableNumber(tbl);
              setMerchantTab('qr-designer');
            }}
            onSimulateCustomerScan={(tbl) => {
              setActiveTableNumber(tbl);
              navigate(`/menu/${merchant.slug}/${tbl}`);
            }}
          />
        )}

        {merchantTab === 'qr-designer' && (
          <QRDesigner
            merchant={merchant}
            tables={tables}
            activeTableNumber={activeTableNumber}
            setActiveTableNumber={setActiveTableNumber}
            config={qrConfig}
            onSaveConfig={(cfg) => {
              setQrConfig(cfg);
              try { localStorage.setItem('qrserve_qr_config', JSON.stringify(cfg)); } catch {}
            }}
          />
        )}

        {merchantTab === 'analytics' && (
          <AnalyticsView
            merchant={merchant}
            revenueHistory={mappedRevenueHistory}
            popularItems={mappedPopularItems}
            metrics={dashboardMetrics}
          />
        )}

        {merchantTab === 'settings' && (
          <MerchantSettings
            merchant={merchant}
            onUpdateMerchant={(updated) => console.log('Update merchant via API', updated)}
          />
        )}
      </div>
    </div>
  );
};