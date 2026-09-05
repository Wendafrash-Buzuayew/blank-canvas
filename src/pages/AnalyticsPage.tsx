import React from 'react';
import { BarChart3, TrendingUp, DollarSign, ShoppingBag, Table as TableIcon } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState } from '../components/ui/States';
import { Card } from '../components/ui/Card';
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
      <div className="mx-auto max-w-[80rem] space-y-6">
        <div>
          <h2 className="flex items-center gap-2 text-title-m text-ink">
            <BarChart3 className="h-5 w-5 text-brand-press" aria-hidden="true" />
            Analytics
          </h2>
          <p className="mt-1 text-body-m text-muted">Real-time business insights from the backend</p>
        </div>

        {todayMetrics && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Card compact>
              <div className="mb-2 flex items-center gap-2">
                <DollarSign className="h-4 w-4 text-success" aria-hidden="true" />
                <span className="text-label-s uppercase text-muted">Today Revenue</span>
              </div>
              <div className="text-title-l [font-variant-numeric:tabular-nums]">
                {Number(todayMetrics.todayRevenue).toLocaleString()} ETB
              </div>
            </Card>

            <Card compact>
              <div className="mb-2 flex items-center gap-2">
                <ShoppingBag className="h-4 w-4 text-info" aria-hidden="true" />
                <span className="text-label-s uppercase text-muted">Total Orders</span>
              </div>
              <div className="text-title-l [font-variant-numeric:tabular-nums]">{todayMetrics.totalOrders}</div>
            </Card>

            <Card compact>
              <div className="mb-2 flex items-center gap-2">
                <TrendingUp className="h-4 w-4 text-warn" aria-hidden="true" />
                <span className="text-label-s uppercase text-muted">Avg Order Value</span>
              </div>
              <div className="text-title-l [font-variant-numeric:tabular-nums]">
                {Number(todayMetrics.avgOrderValue).toLocaleString()} ETB
              </div>
            </Card>

            <Card compact>
              <div className="mb-2 flex items-center gap-2">
                <TableIcon className="h-4 w-4 text-info" aria-hidden="true" />
                <span className="text-label-s uppercase text-muted">Occupancy</span>
              </div>
              <div className="text-title-l [font-variant-numeric:tabular-nums]">
                {todayMetrics.occupiedTables}/{todayMetrics.totalTables}
              </div>
              <div className="mt-1 text-body-m text-muted">{todayMetrics.occupancyRate}% occupied</div>
            </Card>
          </div>
        )}

        {revenueData && revenueData.salesHistory.length > 0 && (
          <Card>
            <h3 className="mb-4 text-label-m text-ink">Revenue History</h3>
            <div className="space-y-2">
              {revenueData.salesHistory.map((entry, idx) => (
                <div key={idx} className="flex items-center justify-between border-b border-line py-2 last:border-0">
                  <span className="text-body-m text-muted">{entry.date}</span>
                  <div className="flex items-center gap-4">
                    <span className="text-label-s text-muted">{entry.ordersCount} orders</span>
                    <span className="text-label-m text-ink [font-variant-numeric:tabular-nums]">
                      {Number(entry.revenue).toLocaleString()} ETB
                    </span>
                  </div>
                </div>
              ))}
            </div>
            <div className="mt-4 border-t border-line pt-4">
              <div className="flex items-center justify-between">
                <span className="text-label-m text-ink">Total Revenue</span>
                <span className="text-title-s text-brand-press [font-variant-numeric:tabular-nums]">
                  {Number(revenueData.totalRevenue).toLocaleString()} ETB
                </span>
              </div>
            </div>
          </Card>
        )}

        {popularItems && popularItems.length > 0 && (
          <Card>
            <h3 className="mb-4 text-label-m text-ink">Popular Items</h3>
            <div className="space-y-3">
              {popularItems.map((item, idx) => (
                <div key={idx} className="flex items-center gap-3">
                  <span className="flex h-6 w-6 items-center justify-center rounded-pill bg-surface-2 text-label-s text-muted [font-variant-numeric:tabular-nums]">
                    {idx + 1}
                  </span>
                  <div className="flex-1">
                    <span className="text-label-m text-ink">{item.name}</span>
                    <span className="ml-2 text-label-s text-muted">{item.count} orders</span>
                  </div>
                  <span className="text-label-m text-success [font-variant-numeric:tabular-nums]">
                    {Number(item.revenue).toLocaleString()} ETB
                  </span>
                </div>
              ))}
            </div>
          </Card>
        )}

        {!todayMetrics && !revenueData && !popularItems && (
          <Card className="p-12 text-center">
            <BarChart3 className="mx-auto mb-3 h-12 w-12 text-line-strong" aria-hidden="true" />
            <p className="text-label-m text-ink">No analytics data available</p>
            <p className="mt-1 text-body-m text-muted">Analytics will appear once orders are placed.</p>
          </Card>
        )}
      </div>
    </DashboardLayout>
  );
};
