import React from 'react';
import { 
  DollarSign, 
  ShoppingBag, 
  Table as TableIcon, 
  TrendingUp, 
  Flame, 
  ArrowUpRight, 
  Clock, 
  CheckCircle2, 
  ChevronRight,
  Plus,
  Sparkles,
  Zap
} from 'lucide-react';
import { Merchant, Order, Table, MenuItem } from '../../types';
import { formatMoney } from '../../lib/utils';

interface DashboardMetrics {
  todayRevenue: number;
  totalOrders: number;
  pendingOrders: number;
  avgOrderValue: number;
  occupiedTables: number;
  totalTables: number;
  occupancyRate: number;
}

interface PopularItem {
  name: string;
  image: string;
  count: number;
  revenue: number;
}

interface DashboardOverviewProps {
  merchant: Merchant;
  orders: Order[];
  tables: Table[];
  menuItems: MenuItem[];
  popularItems?: PopularItem[];
  metrics?: DashboardMetrics | null;
  onNavigateTab: (tab: string) => void;
}

export const DashboardOverview: React.FC<DashboardOverviewProps> = ({
  merchant,
  orders,
  tables,
  menuItems,
  popularItems,
  metrics,
  onNavigateTab,
}) => {
  const merchantOrders = orders.filter(o => o.merchantId === merchant.id);

  // Use backend analytics when available, otherwise compute from orders
  const todaySales = metrics?.todayRevenue ?? merchantOrders
    .filter(o => o.status !== 'Cancelled')
    .reduce((sum, o) => sum + o.totalPrice, 0);

  const ordersCount = metrics?.totalOrders ?? merchantOrders.length;
  const occupiedTables = metrics?.occupiedTables ?? tables.filter(t => t.status === 'Occupied').length;
  const avgOrderValue = metrics?.avgOrderValue ?? (ordersCount > 0 ? todaySales / ordersCount : 0);

  // Compute popular items locally as fallback, or use backend-provided data
  let displayPopularItems = popularItems;
  if (!displayPopularItems || displayPopularItems.length === 0) {
    // Local computation fallback
    const itemSalesMap: { [id: string]: { name: string; image: string; count: number; revenue: number } } = {};
    merchantOrders.forEach(o => {
      if (o.status === 'Cancelled') return;
      o.items.forEach(it => {
        if (!itemSalesMap[it.menuItemId]) {
          const itemObj = menuItems.find(m => m.id === it.menuItemId);
          itemSalesMap[it.menuItemId] = {
            name: it.name,
            image: itemObj?.image || '',
            count: 0,
            revenue: 0,
          };
        }
        itemSalesMap[it.menuItemId].count += it.quantity;
        itemSalesMap[it.menuItemId].revenue += it.price * it.quantity;
      });
    });

    displayPopularItems = Object.values(itemSalesMap)
      .sort((a, b) => b.count - a.count)
      .slice(0, 4);
  }

  const pendingCount = merchantOrders.filter(o => o.status === 'Pending' || o.status === 'Accepted').length;

  return (
    <div className="space-y-6">

      {/* Top Welcome Banner */}
      <div className="bg-[#1E1E1E] text-white p-6 rounded-xl shadow-sm border border-slate-800 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 mb-1.5">
            <span className="px-2.5 py-0.5 rounded text-[10px] font-bold bg-[#E60028] text-white uppercase tracking-wider">
              Merchant Dashboard
            </span>
            <span className="text-xs text-slate-400 font-medium">Branch: Main Dining</span>
          </div>
          <h2 className="text-2xl font-bold tracking-tight text-white">{merchant.name}</h2>
          <p className="text-xs text-slate-300 mt-1 max-w-lg">
            Manage live table orders, track sales performance, customize QR stand displays, and manage your digital menu.
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <button
            onClick={() => onNavigateTab('orders')}
            className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-semibold rounded-lg shadow-sm flex items-center gap-2 transition-colors"
          >
            <ShoppingBag className="w-4 h-4" />
            Kitchen Orders
          </button>
          <button
            onClick={() => onNavigateTab('qr-designer')}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold rounded-lg border border-slate-700 flex items-center gap-2 transition-colors"
          >
            <Sparkles className="w-4 h-4 text-amber-400" />
            QR Designer
          </button>
        </div>
      </div>

      {/* KPI Metrics Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        
        {/* Sales */}
        <div className="p-5 bg-white rounded-xl border border-slate-200 shadow-sm">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-1">Today's Revenue</p>
          <p className="text-2xl font-bold text-slate-900">
            {formatMoney(todaySales, merchant.currencySymbol)}
          </p>
          <p className="text-xs text-emerald-600 font-medium mt-2 flex items-center gap-1">
            <TrendingUp className="w-3.5 h-3.5" /> Live from analytics
          </p>
        </div>

        {/* Orders Today */}
        <div className="p-5 bg-white rounded-xl border border-slate-200 shadow-sm">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-1">Total Orders</p>
          <p className="text-2xl font-bold text-slate-900">{ordersCount}</p>
          <p className="text-xs text-emerald-600 font-medium mt-2">
            {pendingCount} pending currently
          </p>
        </div>

        {/* Active Tables */}
        <div className="p-5 bg-white rounded-xl border border-slate-200 shadow-sm">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-1">Active Tables</p>
          <p className="text-2xl font-bold text-slate-900">{occupiedTables}/{tables.length || metrics?.totalTables || 0}</p>
          <div className="w-full bg-slate-100 h-1.5 rounded-full mt-3 overflow-hidden">
            <div 
              className="bg-amber-400 h-full transition-all" 
              style={{ width: `${Math.round((occupiedTables / (tables.length || metrics?.totalTables || 1)) * 100)}%` }}
            />
          </div>
        </div>

        {/* Average Order Value */}
        <div className="p-5 bg-white rounded-xl border border-slate-200 shadow-sm">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-1">Avg Order Value</p>
          <p className="text-2xl font-bold text-slate-900">
            {formatMoney(avgOrderValue, merchant.currencySymbol)}
          </p>
          <p className="text-xs text-slate-400 mt-2">Across {ordersCount} scanned tables</p>
        </div>

      </div>

      {/* Main Grid: Live Table Map & Top Ordered Dishes */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

        {/* Left 2 Cols: Tables Status Floor Plan */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-sm p-6 space-y-4">
          <div className="flex items-center justify-between pb-3 border-b border-slate-100">
            <div>
              <h3 className="font-bold text-slate-800 text-sm">Floor Layout & Table Status</h3>
              <p className="text-xs text-slate-500">Live occupancy of scanned QR tables in Main Dining</p>
            </div>
            <button
              onClick={() => onNavigateTab('tables')}
              className="text-xs font-bold text-[#E60028] hover:underline flex items-center gap-1"
            >
              Manage Tables
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>

          {tables.length === 0 ? (
            <p className="text-xs text-slate-400 py-6 text-center">No tables configured yet.</p>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {tables.map(t => {
                const tableOrder = merchantOrders.find(
                  o => o.tableNumber === t.tableNumber && o.status !== 'Paid' && o.status !== 'Cancelled'
                );

                return (
                  <div
                    key={t.id}
                    onClick={() => onNavigateTab('tables')}
                    className={`p-4 rounded-xl border transition-all cursor-pointer ${
                      t.status === 'Occupied'
                        ? 'bg-amber-50/70 border-amber-200'
                        : t.status === 'Reserved'
                        ? 'bg-indigo-50/70 border-indigo-200'
                        : 'bg-emerald-50/40 border-emerald-200 hover:border-emerald-300'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-2">
                      <span className="font-bold text-sm text-slate-900">Table {t.tableNumber}</span>
                      <span className={`w-2.5 h-2.5 rounded-full ${
                        t.status === 'Occupied' ? 'bg-amber-500 animate-pulse' : t.status === 'Reserved' ? 'bg-indigo-500' : 'bg-emerald-500'
                      }`} />
                    </div>
                    <div className="text-xs text-slate-500">{t.capacity} Seats</div>
                    {tableOrder ? (
                      <div className="mt-2 text-[11px] font-bold text-amber-800 bg-amber-100 px-2 py-0.5 rounded text-center truncate">
                        #{tableOrder.orderNumber} ({tableOrder.status})
                      </div>
                    ) : (
                      <div className="mt-2 text-[11px] text-emerald-700 font-semibold">
                        Ready for Scan
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Right Col: Popular Items Ranking */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 space-y-4">
          <div className="flex items-center justify-between pb-3 border-b border-slate-100">
            <h3 className="font-bold text-slate-800 text-sm flex items-center gap-2">
              <Flame className="w-4 h-4 text-amber-500" />
              Popular Menu Items
            </h3>
            <button
              onClick={() => onNavigateTab('menu')}
              className="text-xs font-bold text-[#E60028] hover:underline"
            >
              View Menu
            </button>
          </div>

          <div className="space-y-3">
            {!displayPopularItems || displayPopularItems.length === 0 ? (
              <p className="text-xs text-slate-400 py-6 text-center">No orders recorded yet.</p>
            ) : (
              displayPopularItems.map((item, idx) => (
                <div key={idx} className="flex items-center gap-3 p-3 bg-slate-50 rounded-lg border border-slate-100">
                  {item.image ? (
                    <img 
                      src={item.image} 
                      alt={item.name} 
                      className="w-10 h-10 rounded-md object-cover shrink-0 border border-slate-200"
                    />
                  ) : (
                    <div className="w-10 h-10 rounded-md bg-slate-200 flex items-center justify-center text-slate-400 shrink-0">
                      <Zap className="w-4 h-4" />
                    </div>
                  )}
                  <div className="flex-1 min-w-0">
                    <h4 className="text-xs font-bold text-slate-900 truncate">{item.name}</h4>
                    <span className="text-[11px] text-slate-500">{item.count} ordered today</span>
                  </div>
                  <div className="text-right">
                    <span className="text-xs font-bold text-slate-900 block">
                      {formatMoney(item.revenue, merchant.currencySymbol)}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

      </div>

    </div>
  );
};