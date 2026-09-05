import React from 'react';
import { Store, Building2, Table as TableIcon, Users, DollarSign, ShoppingBag, Utensils, Star, ExternalLink } from 'lucide-react';
import { Link } from 'react-router-dom';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState } from '../components/ui/States';
import { Card } from '../components/ui/Card';
import { IdentityChip, ChipStatus } from '../components/ui/Chip';
import { useAuth } from '../context/AuthContext';
import { useTodayAnalytics, useTables, useOrders, useMerchant } from '../hooks/useApiData';
import { useBranchesLookup } from '../hooks/useLookups';
import { getRoleLabel } from '../router/ProtectedRoute';
import { isPhase2Enabled } from '../lib/phase';
import type { TableStatus } from '../lib/orderStatus';

export const DashboardPage: React.FC = () => {
  return isPhase2Enabled() ? <Phase2Dashboard /> : <Phase1Dashboard />;
};

/** DESIGN.md section 3.7 - the sanctioned TableStatus -> ground mapping. */
const TABLE_STATUS_GROUND: Record<TableStatus, ChipStatus> = {
  AVAILABLE: 'success',
  OCCUPIED: 'warn',
  RESERVED: 'info',
};

/**
 * Phase 1 has no ordering, tables, or analytics services live - this shows
 * only what the mini-app actually has: the merchant's own profile and
 * branches, with a link to each branch's live public menu.
 */
const Phase1Dashboard: React.FC = () => {
  const { user } = useAuth();
  const { data: merchant, isLoading: merchantLoading, error: merchantError } = useMerchant(user?.merchantId);
  const branchesQuery = useBranchesLookup();
  const branches = branchesQuery.data ?? [];
  const primaryBranch = branches.find((b) => b.isPrimary);

  if (merchantLoading) {
    return (
      <DashboardLayout title="Dashboard">
        <Spinner label="Loading dashboard..." />
      </DashboardLayout>
    );
  }

  if (merchantError) {
    return (
      <DashboardLayout title="Dashboard">
        <ErrorState message={`Failed to load dashboard data: ${(merchantError as Error).message}`} />
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout title="Dashboard">
      <div className="space-y-6">
        <div className="bg-brand-gradient rounded-card p-6 text-brand-fg shadow-[var(--shadow-lift-brand)]">
          <IdentityChip className="!bg-white/15 !border-white/30 !text-white">{getRoleLabel(user?.role || '')}</IdentityChip>
          <h2 className="mt-2 text-title-l">Welcome back, {user?.name || user?.email}</h2>
          <p className="mt-1 text-body-m text-white/80">Your business at a glance</p>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Card compact>
            <div className="mb-2 flex items-center gap-2">
              <Store className="h-4 w-4 text-brand-press" aria-hidden="true" />
              <span className="text-label-s uppercase text-muted">Merchant</span>
            </div>
            <div className="text-title-s text-ink">{merchant?.name ?? '-'}</div>
            <div className="mt-1 text-body-m text-muted">{merchant?.category ?? '-'} - {merchant?.city ?? '-'}</div>
          </Card>
          <Card compact>
            <div className="mb-2 flex items-center gap-2">
              <Building2 className="h-4 w-4 text-info" aria-hidden="true" />
              <span className="text-label-s uppercase text-muted">Branches</span>
            </div>
            <div className="text-title-l [font-variant-numeric:tabular-nums]">{branches.length}</div>
            {branches.length === 0 && (
              <Link to="/merchant/branches" className="mt-1 inline-block text-label-m text-brand-press hover:underline">
                Create your first branch &rarr;
              </Link>
            )}
          </Card>
          {primaryBranch && merchant && (
            <Card
              as="a"
              interactive
              href={`/m/${merchant.slug}/${primaryBranch.slug}`}
              target="_blank"
              rel="noreferrer"
              compact
            >
              <div className="mb-2 flex items-center gap-2">
                <ExternalLink className="h-4 w-4 text-success" aria-hidden="true" />
                <span className="text-label-s uppercase text-muted">Public Menu</span>
              </div>
              <div className="truncate text-label-m text-ink">/m/{merchant.slug}/{primaryBranch.slug}</div>
              <div className="mt-1 text-body-m text-muted">View your live customer menu</div>
            </Card>
          )}
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Link
            to="/merchant/menu"
            className="card-surface flex items-center gap-3 p-5 transition-shadow hover:shadow-[var(--shadow-lift)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark"
          >
            <div className="flex h-10 w-10 items-center justify-center rounded-control bg-brand-soft">
              <Utensils className="h-5 w-5 text-brand-press" aria-hidden="true" />
            </div>
            <div>
              <h3 className="text-label-m text-ink">Build your menu</h3>
              <p className="text-body-m text-muted">Add categories and products</p>
            </div>
          </Link>
          <Link
            to="/merchant/reviews"
            className="card-surface flex items-center gap-3 p-5 transition-shadow hover:shadow-[var(--shadow-lift)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark"
          >
            <div className="flex h-10 w-10 items-center justify-center rounded-control bg-brand-soft">
              <Star className="h-5 w-5 text-brand-press" aria-hidden="true" />
            </div>
            <div>
              <h3 className="text-label-m text-ink">Customer reviews</h3>
              <p className="text-body-m text-muted">See what customers are saying</p>
            </div>
          </Link>
        </div>
      </div>
    </DashboardLayout>
  );
};

const Phase2Dashboard: React.FC = () => {
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
        <div className="bg-brand-gradient rounded-card p-6 text-brand-fg shadow-[var(--shadow-lift-brand)]">
          <IdentityChip className="!bg-white/15 !border-white/30 !text-white">{roleLabel}</IdentityChip>
          <h2 className="mt-2 text-title-l">Welcome back, {user?.name || user?.email}</h2>
          <p className="mt-1 text-body-m text-white/80">
            {user?.role === 'SUPER_ADMIN'
              ? 'Platform management dashboard - all data from live backend'
              : 'Your business at a glance - powered by real-time data'}
          </p>
        </div>

        {todayMetrics ? (
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
              <div className="mt-1 text-label-s text-warn">{todayMetrics.pendingOrders} pending</div>
            </Card>
            <Card compact>
              <div className="mb-2 flex items-center gap-2">
                <TableIcon className="h-4 w-4 text-info" aria-hidden="true" />
                <span className="text-label-s uppercase text-muted">Table Occupancy</span>
              </div>
              <div className="text-title-l [font-variant-numeric:tabular-nums]">
                {todayMetrics.occupiedTables}/{todayMetrics.totalTables}
              </div>
              <div className="mt-1 text-body-m text-muted">{todayMetrics.occupancyRate}% occupied</div>
            </Card>
            <Card compact>
              <div className="mb-2 flex items-center gap-2">
                <DollarSign className="h-4 w-4 text-warn" aria-hidden="true" />
                <span className="text-label-s uppercase text-muted">Avg Order Value</span>
              </div>
              <div className="text-title-l [font-variant-numeric:tabular-nums]">
                {Number(todayMetrics.avgOrderValue).toLocaleString()} ETB
              </div>
            </Card>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Card compact>
              <span className="text-label-s uppercase text-muted">Total Tables</span>
              <div className="mt-1 text-title-l [font-variant-numeric:tabular-nums]">{tableCount}</div>
              <div className="mt-1 text-label-s text-info">{occupiedTables} occupied</div>
            </Card>
            <Card compact>
              <span className="text-label-s uppercase text-muted">Total Orders</span>
              <div className="mt-1 text-title-l [font-variant-numeric:tabular-nums]">{orderCount}</div>
            </Card>
            <Card compact>
              <span className="text-label-s uppercase text-muted">Available Tables</span>
              <div className="mt-1 text-title-l [font-variant-numeric:tabular-nums]">{tableCount - occupiedTables}</div>
            </Card>
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          <Card compact>
            <div className="mb-2 flex items-center gap-3">
              <Store className="h-5 w-5 text-brand-press" aria-hidden="true" />
              <h3 className="text-label-m text-ink">Merchants</h3>
            </div>
            <p className="text-body-m text-muted">Manage merchant tenant accounts</p>
          </Card>
          <Card compact>
            <div className="mb-2 flex items-center gap-3">
              <Building2 className="h-5 w-5 text-info" aria-hidden="true" />
              <h3 className="text-label-m text-ink">Branches</h3>
            </div>
            <p className="text-body-m text-muted">Manage restaurant branches</p>
          </Card>
          <Card compact>
            <div className="mb-2 flex items-center gap-3">
              <Users className="h-5 w-5 text-info" aria-hidden="true" />
              <h3 className="text-label-m text-ink">Users</h3>
            </div>
            <p className="text-body-m text-muted">Create and manage user accounts</p>
          </Card>
        </div>

        {tables && tables.length > 0 && (
          <Card>
            <h3 className="mb-4 flex items-center gap-2 text-label-m text-ink">
              <TableIcon className="h-4 w-4 text-muted" aria-hidden="true" />
              Recent Tables
            </h3>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6">
              {tables.slice(0, 12).map((table) => (
                <div
                  key={table.id}
                  className={`rounded-[var(--radius-surface-sm)] border border-line p-3 text-center ${
                    { success: 'bg-success-soft', warn: 'bg-warn-soft', info: 'bg-info-soft' }[TABLE_STATUS_GROUND[table.status as TableStatus]]
                  }`}
                >
                  <div className="text-label-m text-ink [font-variant-numeric:tabular-nums]">{table.tableNumber}</div>
                  <div className="text-label-s text-muted">Cap: {table.capacity}</div>
                  <div className="mt-1 text-label-s text-ink">{table.status}</div>
                </div>
              ))}
            </div>
          </Card>
        )}
      </div>
    </DashboardLayout>
  );
};
