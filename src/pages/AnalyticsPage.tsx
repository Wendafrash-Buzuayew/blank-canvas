import React from 'react';
import { BarChart3, TrendingUp, DollarSign, ShoppingBag, Table as TableIcon } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState } from '../components/ui/States';
import { useAuth } from '../context/AuthContext';
import { useTodayAnalytics, useRevenueAnalytics, usePopularItems } from '../hooks/useApiData';

export const AnalyticsPage: React.FC = () => {
  const { user } = useAuth();
  const merchantId = user?.merchantId;

  const { data: todayMetrics, isLoading: todayLoading, error: todayError } = useTodayAnalytics(merchantId);
  const { data: revenueData, isLoading: revenueLoading } = useRevenueAnalytics(merchantId);
  const { data: popularItems, isLoading: popularLoading } = usePopularItems(merchantId);


  if (todayLoading || revenueLoading || popularLoading) {
    return (
      <DashboardLayout title="Analytics">
        <Spinner label="Loading analytics..." />
      </DashboardLayout>
    );
  }

  if (todayError) {
    return (
      <DashboardLayout title="Analytics">
        <ErrorState message={`Failed to load analytics: ${(todayError as Error).message}`} />
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout title="Analytics">
      <div className="space-y-6">
        <div>
          <h2 className="text-xl font-black text-slate-900 flex items-center gap-2">
            <BarChart3 className="w-5 h-5 text-[#E60028]" />
            Analytics
          </h2>
          <p className="text-xs text-slate-500 mt-1">Real-time business insights from the backend</p>
        </div>

        {/* Today's Metrics */}
        {todayMetrics && (
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
            </div>

            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
              <div className="flex items-center gap-2 mb-2">
                <TrendingUp className="w-4 h-4 text-amber-600" />
                <span className="text-xs font-bold text-slate-400 uppercase">Avg Order Value</span>
              </div>
              <div className="text-2xl font-black text-slate-900">
                {Number(todayMetrics.avgOrderValue).toLocaleString()} ETB
              </div>
            </div>

            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
              <div className="flex items-center gap-2 mb-2">
                <TableIcon className="w-4 h-4 text-indigo-600" />
                <span className="text-xs font-bold text-slate-400 uppercase">Occupancy</span>
              </div>
              <div className="text-2xl font-black text-slate-900">
                {todayMetrics.occupiedTables}/{todayMetrics.totalTables}
              </div>
              <div className="text-xs text-slate-500 mt-1">{todayMetrics.occupancyRate}% occupied</div>
            </div>
          </div>
        )}

        {/* Revenue History */}
        {revenueData && revenueData.salesHistory.length > 0 && (
          <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm">
            <h3 className="text-sm font-bold text-slate-900 mb-4">Revenue History</h3>
            <div className="space-y-2">
              {revenueData.salesHistory.map((entry, idx) => (
                <div key={idx} className="flex items-center justify-between py-2 border-b border-slate-100 last:border-0">
                  <span className="text-xs text-slate-600">{entry.date}</span>
                  <div className="flex items-center gap-4">
                    <span className="text-xs text-slate-500">{entry.ordersCount} orders</span>
                    <span className="text-sm font-bold text-slate-900">
                      {Number(entry.revenue).toLocaleString()} ETB
                    </span>
                  </div>
                </div>
              ))}
            </div>
            <div className="mt-4 pt-4 border-t border-slate-200">
              <div className="flex items-center justify-between">
                <span className="text-sm font-bold text-slate-700">Total Revenue</span>
                <span className="text-lg font-black text-[#E60028]">
                  {Number(revenueData.totalRevenue).toLocaleString()} ETB
                </span>
              </div>
            </div>
          </div>
        )}

        {/* Popular Items */}
        {popularItems && popularItems.length > 0 && (
          <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm">
            <h3 className="text-sm font-bold text-slate-900 mb-4">Popular Items</h3>
            <div className="space-y-3">
              {popularItems.map((item, idx) => (
                <div key={idx} className="flex items-center gap-3">
                  <span className="w-6 h-6 rounded-full bg-slate-100 flex items-center justify-center text-xs font-bold text-slate-600">
                    {idx + 1}
                  </span>
                  <div className="flex-1">
                    <span className="text-sm font-bold text-slate-900">{item.name}</span>
                    <span className="text-xs text-slate-500 ml-2">{item.count} orders</span>
                  </div>
                  <span className="text-sm font-bold text-emerald-600">
                    {Number(item.revenue).toLocaleString()} ETB
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {!todayMetrics && !revenueData && !popularItems && (
          <div className="bg-white rounded-2xl border border-slate-200 p-12 text-center shadow-sm">
            <BarChart3 className="w-12 h-12 text-slate-300 mx-auto mb-3" />
            <p className="text-sm font-bold text-slate-700">No analytics data available</p>
            <p className="text-xs text-slate-500 mt-1">Analytics will appear once orders are placed.</p>
          </div>
        )}
      </div>
    </DashboardLayout>
  );
};