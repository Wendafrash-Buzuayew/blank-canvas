import React, { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { LandingPage } from '../components/landing/LandingPage';
import { LoginPage } from '../pages/LoginPage';
import { ProtectedRoute, getRoleHome } from './ProtectedRoute';
import { useAuth } from '../context/AuthContext';
import { Spinner } from '../components/ui/States';

// Route-level code splitting: each page is loaded on demand so the initial
// bundle only contains the landing/login pages and the shared shell.
const CustomerMenuPage = lazy(() => import('../pages/CustomerMenuPage').then((m) => ({ default: m.CustomerMenuPage })));
const DigitalMenuPage = lazy(() => import('../pages/DigitalMenuPage').then((m) => ({ default: m.DigitalMenuPage })));
const DashboardPage = lazy(() => import('../pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const MerchantManagement = lazy(() => import('../pages/MerchantManagement').then((m) => ({ default: m.MerchantManagement })));
const BackOfficePage = lazy(() => import('../pages/BackOfficePage').then((m) => ({ default: m.BackOfficePage })));
const TemplateManagement = lazy(() => import('../pages/TemplateManagement').then((m) => ({ default: m.TemplateManagement })));
const BranchManagement = lazy(() => import('../pages/BranchManagement').then((m) => ({ default: m.BranchManagement })));
const TableManagement = lazy(() => import('../pages/TableManagement').then((m) => ({ default: m.TableManagement })));
const WaiterManagement = lazy(() => import('../pages/WaiterManagement').then((m) => ({ default: m.WaiterManagement })));
const UserManagement = lazy(() => import('../pages/UserManagement').then((m) => ({ default: m.UserManagement })));
const AnalyticsPage = lazy(() => import('../pages/AnalyticsPage').then((m) => ({ default: m.AnalyticsPage })));
const SettingsPage = lazy(() => import('../pages/SettingsPage').then((m) => ({ default: m.SettingsPage })));
const WaiterRequestsPage = lazy(() => import('../pages/WaiterRequestsPage').then((m) => ({ default: m.WaiterRequestsPage })));
const KitchenLivePage = lazy(() => import('../pages/KitchenLivePage').then((m) => ({ default: m.KitchenLivePage })));
const MenuBuilderPage = lazy(() => import('../pages/MenuBuilderPage').then((m) => ({ default: m.MenuBuilderPage })));
const ReviewsPage = lazy(() => import('../pages/ReviewsPage').then((m) => ({ default: m.ReviewsPage })));
const WaiterDashboardPage = lazy(() => import('../pages/WaiterDashboardPage').then((m) => ({ default: m.WaiterDashboardPage })));
const OnboardingPage = lazy(() => import('../pages/OnboardingPage').then((m) => ({ default: m.OnboardingPage })));
const DevSuperAppLoginPage = lazy(() => import('../pages/DevSuperAppLoginPage').then((m) => ({ default: m.DevSuperAppLoginPage })));

// Fallback shown while a lazy route chunk is loading.
const RouteFallback: React.FC = () => (
  <div className="min-h-screen bg-canvas flex items-center justify-center">
    <Spinner label="Loading…" />
  </div>
);

// Root redirect component that sends users to their role-based home
const RootRedirect: React.FC = () => {
  const { isAuthenticated, user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen bg-canvas flex items-center justify-center">
        <Spinner label="Loading..." />
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" replace />;
  }

  return <Navigate to={getRoleHome(user.role)} replace />;
};

export const AppRouter: React.FC = () => {
  return (
    <BrowserRouter>
      <Suspense fallback={<RouteFallback />}>
        <Routes>
          {/* ===== Public Routes ===== */}
          <Route path="/" element={<LandingPage
            // /menu/demo/main/1 pointed at the parked table-scoped ordering
            // route with a merchant slug ("demo") that never existed - the
            // real Phase 1 customer experience is the path-based digital
            // menu, and this is the one seeded merchant it can demo.
            onStartCustomerDemo={() => { window.location.href = '/m/sunrise-coffee'; }}
            onStartMerchantDemo={() => { window.location.href = '/login'; }}
            onBookDemo={() => alert('Demo booking request sent! We will contact you at wendebuzu@gmail.com')}
          />} />

          <Route path="/login" element={<LoginPage />} />

          {/* Local-dev-only Super App handoff simulator — see
              DevSuperAppLoginPage's header comment. import.meta.env.DEV is a
              Vite compile-time constant, so this branch (and its lazy chunk)
              is dead-code-eliminated from a production build entirely. */}
          {import.meta.env.DEV && (
            <Route path="/dev/superapp-login" element={<DevSuperAppLoginPage />} />
          )}

          {/* Post-Super-App-login business profile form - see ProtectedRoute's
              onboardingComplete gate. Auth-only, not role-restricted: any
              freshly auto-registered account lands here regardless of role. */}
          <Route
            path="/onboarding"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']}>
                <OnboardingPage />
              </ProtectedRoute>
            }
          />

          {/* Public QR Menu - /menu/{merchantSlug}/{tableNumber} */}
          <Route path="/menu/:merchantSlug/:branchSlug/:tableNumber" element={<CustomerMenuPage />} />
          <Route path="/menu/:merchantSlug/:tableNumber" element={<CustomerMenuPage />} />

          {/* Public Digital Menu - /m/{merchantSlug}[/{branchSlug}] (read-only, no cart/ordering) */}
          <Route path="/m/:merchantSlug" element={<DigitalMenuPage />} />
          <Route path="/m/:merchantSlug/:branchSlug" element={<DigitalMenuPage />} />

          {/* ===== SUPER_ADMIN Routes ===== */}
          <Route
            path="/admin/dashboard"
            element={
              <ProtectedRoute allowedRoles={['SUPER_ADMIN']} requiresPhase2>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/merchants"
            element={
              <ProtectedRoute allowedRoles={['SUPER_ADMIN']}>
                <MerchantManagement />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/branches"
            element={
              <ProtectedRoute allowedRoles={['SUPER_ADMIN']} requiresPhase2>
                <BranchManagement />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/users"
            element={
              <ProtectedRoute allowedRoles={['SUPER_ADMIN']} requiresPhase2>
                <UserManagement />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/tables"
            element={
              <ProtectedRoute allowedRoles={['SUPER_ADMIN']} requiresPhase2>
                <TableManagement />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/waiters"
            element={
              <ProtectedRoute allowedRoles={['SUPER_ADMIN']} requiresPhase2>
                <WaiterManagement />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/analytics"
            element={
              <ProtectedRoute allowedRoles={['SUPER_ADMIN']} requiresPhase2>
                <AnalyticsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/subscriptions"
            element={
              <ProtectedRoute allowedRoles={['SUPER_ADMIN']} requiresPhase2>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/back-office"
            element={
              <ProtectedRoute allowedRoles={['SUPER_ADMIN']}>
                <BackOfficePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/templates"
            element={
              <ProtectedRoute allowedRoles={['SUPER_ADMIN']}>
                <TemplateManagement />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/settings"
            element={
              <ProtectedRoute allowedRoles={['SUPER_ADMIN']}>
                <SettingsPage />
              </ProtectedRoute>
            }
          />

          {/* ===== MERCHANT_ADMIN Routes ===== */}
          <Route
            path="/merchant/dashboard"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']}>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/merchant/branches"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']}>
                <BranchManagement />
              </ProtectedRoute>
            }
          />
          <Route
            path="/merchant/users"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']} requiresPhase2>
                <UserManagement />
              </ProtectedRoute>
            }
          />
          <Route
            path="/merchant/tables"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']} requiresPhase2>
                <TableManagement />
              </ProtectedRoute>
            }
          />
          <Route
            path="/merchant/menu"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']}>
                <MenuBuilderPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/merchant/reviews"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']}>
                <ReviewsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/merchant/orders"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']} requiresPhase2>
                <KitchenLivePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/merchant/waiters"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']} requiresPhase2>
                <WaiterManagement />
              </ProtectedRoute>
            }
          />
          <Route
            path="/merchant/analytics"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']} requiresPhase2>
                <AnalyticsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/merchant/settings"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']}>
                <SettingsPage />
              </ProtectedRoute>
            }
          />

          {/* ===== BRANCH_MANAGER Routes ===== */}
          <Route
            path="/branch/dashboard"
            element={
              <ProtectedRoute allowedRoles={['BRANCH_MANAGER', 'MERCHANT_OWNER', 'SUPER_ADMIN']} requiresPhase2>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/branch/orders"
            element={
              <ProtectedRoute allowedRoles={['BRANCH_MANAGER', 'MERCHANT_OWNER', 'SUPER_ADMIN']} requiresPhase2>
                <KitchenLivePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/branch/tables"
            element={
              <ProtectedRoute allowedRoles={['BRANCH_MANAGER', 'MERCHANT_OWNER', 'SUPER_ADMIN']} requiresPhase2>
                <TableManagement />
              </ProtectedRoute>
            }
          />
          <Route
            path="/branch/waiters"
            element={
              <ProtectedRoute allowedRoles={['BRANCH_MANAGER', 'MERCHANT_OWNER', 'SUPER_ADMIN']} requiresPhase2>
                <WaiterManagement />
              </ProtectedRoute>
            }
          />
          <Route
            path="/branch/kitchen"
            element={
              <ProtectedRoute allowedRoles={['BRANCH_MANAGER', 'MERCHANT_OWNER', 'SUPER_ADMIN']} requiresPhase2>
                <KitchenLivePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/branch/reports"
            element={
              <ProtectedRoute allowedRoles={['BRANCH_MANAGER', 'MERCHANT_OWNER', 'SUPER_ADMIN']} requiresPhase2>
                <AnalyticsPage />
              </ProtectedRoute>
            }
          />

          {/* ===== WAITER Routes ===== */}
          <Route
            path="/waiter/dashboard"
            element={
              <ProtectedRoute allowedRoles={['WAITER', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']} requiresPhase2>
                <WaiterDashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/waiter/tables"
            element={
              <ProtectedRoute allowedRoles={['WAITER', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']} requiresPhase2>
                <TableManagement />
              </ProtectedRoute>
            }
          />
          <Route
            path="/waiter/orders"
            element={
              <ProtectedRoute allowedRoles={['WAITER', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']} requiresPhase2>
                <WaiterDashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/waiter/requests"
            element={
              <ProtectedRoute allowedRoles={['WAITER', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']} requiresPhase2>
                <WaiterRequestsPage />
              </ProtectedRoute>
            }
          />

          {/* ===== KITCHEN Routes ===== */}
          <Route
            path="/kitchen/dashboard"
            element={
              <ProtectedRoute allowedRoles={['KITCHEN', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']} requiresPhase2>
                <KitchenLivePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/kitchen/incoming"
            element={
              <ProtectedRoute allowedRoles={['KITCHEN', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']} requiresPhase2>
                <KitchenLivePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/kitchen/preparing"
            element={
              <ProtectedRoute allowedRoles={['KITCHEN', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']} requiresPhase2>
                <KitchenLivePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/kitchen/ready"
            element={
              <ProtectedRoute allowedRoles={['KITCHEN', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']} requiresPhase2>
                <KitchenLivePage />
              </ProtectedRoute>
            }
          />

          {/* Legacy redirects for old routes */}
          <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />
          <Route path="/merchant" element={<Navigate to="/merchant/dashboard" replace />} />
          <Route path="/branch" element={<Navigate to="/branch/dashboard" replace />} />
          <Route path="/waiter" element={<Navigate to="/waiter/dashboard" replace />} />
          <Route path="/kitchen" element={<Navigate to="/kitchen/dashboard" replace />} />

          {/* Catch all - redirect to role home */}
          <Route path="*" element={<RootRedirect />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
};