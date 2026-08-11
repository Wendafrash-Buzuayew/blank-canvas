import React from 'react';
import { Store, Building2, Table as TableIcon, Users, DollarSign, ShoppingBag } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState } from '../components/ui/States';
import { useAuth } from '../context/AuthContext';
import { useTodayAnalytics, useTables, useOrders } from '../hooks/useApiData';
import { getRoleLabel } from '../router/ProtectedRoute';

export const DashboardPage: React.FC = () => {
  const { user } = useAuth();
  const merchantId = user?.merchantId;

  const { data: todayMetrics, isLoading: metricsLoading, error: metricsError } = useTodayAnalytics(merchantId);
  const { data: tables, isLoading: tablesLoading } = useTables();
  const { data: orders, isLoading: ordersLoading } = useOrders();


  if (metricsLoading || tablesLoading || ordersLoading) {
    return (
      <DashboardLayout title="Dashboard">
        <Spinner label="Loading dashboard..." />
      </DashboardLayout>
    );
  }

  if (metricsError) {
    return (
      <DashboardLayout title="Dashboard">
        <ErrorState message={`Failed to load dashboard data: ${(metricsError as Error).message}`} />
      </DashboardLayout>
    );
  }

  const roleLabel = getRoleLabel(user?.role || '');
  const tableCount = tables?.length || 0;
  const orderCount = orders?.length || 0;
  const occupiedTables = tables?.filter(t => t.status === 'OCCUPIED').length || 0;

  return (
    <DashboardLayout title="Dashboard">
      <div className="space-y-6">
        <div className="bg-gradient-to-r from-slate-900 via-slate-800 to-indigo-900 text-white p-6 rounded-2xl shadow-lg">
          <span className="px-2.5 py-0.5 rounded-full text-[10px] font-extrabold bg-indigo-500 text-white uppercase tracking-wider">
            {roleLabel}
          </span>
          <h2 className="text-2xl font-black mt-2">Welcome back, {user?.name || user?.email}</h2>
          <p className="text-xs text-slate-300 mt-1">
            {user?.role === 'SUPER_ADMIN'
              ? 'Platform management dashboard - all data from live backend'
              : 'Your business at a glance - powered by real-time data'}
          </p>
        </div>

        {todayMetrics ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
              <div className="flex items-center gap-2 mb-2">
                <DollarSign className="w-4 h-4 text-emerald-600" />
                <span className="text-xs font-bold text-slate-400 uppercase">Today Revenue</span>
              </div>
              <div className="text-2xl font-black text-slate-900">
                {Number(todayMetrics.todayRevenue).toLocaleString()} ETB
              </div>
            </div>
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
              <div className="flex items-center gap-2 mb-2">
                <ShoppingBag className="w-4 h-4 text-blue-600" />
                <span className="text-xs font-bold text-slate-400 uppercase">Total Orders</span>
              </div>
              <div className="text-2xl font-black text-slate-900">{todayMetrics.totalOrders}</div>
              <div className="text-xs text-amber-600 font-bold mt-1">{todayMetrics.pendingOrders} pending</div>
            </div>
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
              <div className="flex items-center gap-2 mb-2">
                <TableIcon className="w-4 h-4 text-indigo-600" />
                <span className="text-xs font-bold text-slate-400 uppercase">Table Occupancy</span>
              </div>
              <div className="text-2xl font-black text-slate-900">
                {todayMetrics.occupiedTables}/{todayMetrics.totalTables}
              </div>
              <div className="text-xs text-slate-500 mt-1">{todayMetrics.occupancyRate}% occupied</div>
            </div>
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
              <div className="flex items-center gap-2 mb-2">
                <DollarSign className="w-4 h-4 text-amber-600" />
                <span className="text-xs font-bold text-slate-400 uppercase">Avg Order Value</span>
              </div>
              <div className="text-2xl font-black text-slate-900">
                {Number(todayMetrics.avgOrderValue).toLocaleString()} ETB
              </div>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
              <span className="text-xs font-bold text-slate-400 uppercase">Total Tables</span>
              <div className="text-2xl font-black text-slate-900 mt-1">{tableCount}</div>
              <div className="text-xs text-indigo-600 font-bold mt-1">{occupiedTables} occupied</div>
            </div>
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
              <span className="text-xs font-bold text-slate-400 uppercase">Total Orders</span>
              <div className="text-2xl font-black text-slate-900 mt-1">{orderCount}</div>
            </div>
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
              <span className="text-xs font-bold text-slate-400 uppercase">Available Tables</span>
              <div className="text-2xl font-black text-slate-900 mt-1">{tableCount - occupiedTables}</div>
            </div>
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
            <div className="flex items-center gap-3 mb-2">
              <Store className="w-5 h-5 text-[#E60028]" />
              <h3 className="font-bold text-sm text-slate-900">Merchants</h3>
            </div>
            <p className="text-xs text-slate-500">Manage merchant tenant accounts</p>
          </div>
          <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
            <div className="flex items-center gap-3 mb-2">
              <Building2 className="w-5 h-5 text-blue-600" />
              <h3 className="font-bold text-sm text-slate-900">Branches</h3>
            </div>
            <p className="text-xs text-slate-500">Manage restaurant branches</p>
          </div>
          <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
            <div className="flex items-center gap-3 mb-2">
              <Users className="w-5 h-5 text-indigo-600" />
              <h3 className="font-bold text-sm text-slate-900">Users</h3>
            </div>
            <p className="text-xs text-slate-500">Create and manage user accounts</p>
          </div>
        </div>

        {tables && tables.length > 0 && (
          <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm">
            <h3 className="text-sm font-bold text-slate-900 mb-4 flex items-center gap-2">
              <TableIcon className="w-4 h-4 text-slate-400" />
              Recent Tables
            </h3>
            <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-3">
              {tables.slice(0, 12).map((table) => (
                <div
                  key={table.id}
                  className={`p-3 rounded-xl border text-center ${
                    table.status === 'OCCUPIED'
                      ? 'bg-amber-50 border-amber-200'
                      : table.status === 'RESERVED'
                      ? 'bg-blue-50 border-blue-200'
                      : 'bg-emerald-50 border-emerald-200'
                  }`}
                >
                  <div className="text-sm font-bold text-slate-900">{table.tableNumber}</div>
                  <div className="text-[10px] text-slate-500">Cap: {table.capacity}</div>
                  <div className={`text-[10px] font-bold mt-1 ${
                    table.status === 'OCCUPIED' ? 'text-amber-700' :
                    table.status === 'RESERVED' ? 'text-blue-700' : 'text-emerald-700'
                  }`}>
                    {table.status}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </DashboardLayout>
  );
};