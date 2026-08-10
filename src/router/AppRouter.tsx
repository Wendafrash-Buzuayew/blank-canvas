import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { LandingPage } from '../components/landing/LandingPage';
import { LoginPage } from '../pages/LoginPage';
import { CustomerMenuPage } from '../pages/CustomerMenuPage';
import { ProtectedRoute, getRoleHome } from './ProtectedRoute';
import { useAuth } from '../context/AuthContext';
import { Spinner } from '../components/ui/States';

// New real API-backed pages
import { DashboardPage } from '../pages/DashboardPage';
import { MerchantManagement } from '../pages/MerchantManagement';
import { BranchManagement } from '../pages/BranchManagement';
import { TableManagement } from '../pages/TableManagement';
import { WaiterManagement } from '../pages/WaiterManagement';
import { UserManagement } from '../pages/UserManagement';
import { AnalyticsPage } from '../pages/AnalyticsPage';
import { SettingsPage } from '../pages/SettingsPage';
import { WaiterRequestsPage } from '../pages/WaiterRequestsPage';
import { KitchenLivePage } from '../pages/KitchenLivePage';

// Root redirect component that sends users to their role-based home
const RootRedirect: React.FC = () => {
  const { isAuthenticated, user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
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
      <Routes>
        {/* ===== Public Routes ===== */}
        <Route path="/" element={<LandingPage
          onStartCustomerDemo={() => { window.location.href = '/menu/demo/main/1'; }}
          onStartMerchantDemo={() => { window.location.href = '/login'; }}
          onBookDemo={() => alert('Demo booking request sent! We will contact you at wendebuzu@gmail.com')}
        />} />

        <Route path="/login" element={<LoginPage />} />

        {/* Public QR Menu - /menu/{merchantSlug}/{tableNumber} */}
        <Route path="/menu/:merchantSlug/:branchSlug/:tableNumber" element={<CustomerMenuPage />} />
        <Route path="/menu/:merchantSlug/:tableNumber" element={<CustomerMenuPage />} />

        {/* ===== SUPER_ADMIN Routes ===== */}
        <Route
          path="/admin/dashboard"
          element={
            <ProtectedRoute allowedRoles={['SUPER_ADMIN']}>
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
            <ProtectedRoute allowedRoles={['SUPER_ADMIN']}>
              <BranchManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/users"
          element={
            <ProtectedRoute allowedRoles={['SUPER_ADMIN']}>
              <UserManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/tables"
          element={
            <ProtectedRoute allowedRoles={['SUPER_ADMIN']}>
              <TableManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/waiters"
          element={
            <ProtectedRoute allowedRoles={['SUPER_ADMIN']}>
              <WaiterManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/analytics"
          element={
            <ProtectedRoute allowedRoles={['SUPER_ADMIN']}>
              <AnalyticsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/subscriptions"
          element={
            <ProtectedRoute allowedRoles={['SUPER_ADMIN']}>
              <DashboardPage />
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
          path="/merchant/tables"
          element={
            <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']}>
              <TableManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/merchant/menu"
          element={
            <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']}>
              <DashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/merchant/orders"
          element={
            <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']}>
              <KitchenLivePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/merchant/waiters"
          element={
            <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']}>
              <WaiterManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/merchant/analytics"
          element={
            <ProtectedRoute allowedRoles={['MERCHANT_OWNER', 'SUPER_ADMIN']}>
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
            <ProtectedRoute allowedRoles={['BRANCH_MANAGER', 'MERCHANT_OWNER', 'SUPER_ADMIN']}>
              <DashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/branch/orders"
          element={
            <ProtectedRoute allowedRoles={['BRANCH_MANAGER', 'MERCHANT_OWNER', 'SUPER_ADMIN']}>
              <KitchenLivePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/branch/tables"
          element={
            <ProtectedRoute allowedRoles={['BRANCH_MANAGER', 'MERCHANT_OWNER', 'SUPER_ADMIN']}>
              <TableManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/branch/waiters"
          element={
            <ProtectedRoute allowedRoles={['BRANCH_MANAGER', 'MERCHANT_OWNER', 'SUPER_ADMIN']}>
              <WaiterManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/branch/kitchen"
          element={
            <ProtectedRoute allowedRoles={['BRANCH_MANAGER', 'MERCHANT_OWNER', 'SUPER_ADMIN']}>
              <KitchenLivePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/branch/reports"
          element={
            <ProtectedRoute allowedRoles={['BRANCH_MANAGER', 'MERCHANT_OWNER', 'SUPER_ADMIN']}>
              <AnalyticsPage />
            </ProtectedRoute>
          }
        />

        {/* ===== WAITER Routes ===== */}
        <Route
          path="/waiter/dashboard"
          element={
            <ProtectedRoute allowedRoles={['WAITER', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']}>
              <DashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/waiter/tables"
          element={
            <ProtectedRoute allowedRoles={['WAITER', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']}>
              <TableManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/waiter/orders"
          element={
            <ProtectedRoute allowedRoles={['WAITER', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']}>
              <DashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/waiter/requests"
          element={
            <ProtectedRoute allowedRoles={['WAITER', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']}>
              <WaiterRequestsPage />
            </ProtectedRoute>
          }
        />

        {/* ===== KITCHEN Routes ===== */}
        <Route
          path="/kitchen/dashboard"
          element={
            <ProtectedRoute allowedRoles={['KITCHEN', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']}>
              <KitchenLivePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/kitchen/incoming"
          element={
            <ProtectedRoute allowedRoles={['KITCHEN', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']}>
              <KitchenLivePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/kitchen/preparing"
          element={
            <ProtectedRoute allowedRoles={['KITCHEN', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']}>
              <KitchenLivePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/kitchen/ready"
          element={
            <ProtectedRoute allowedRoles={['KITCHEN', 'MERCHANT_OWNER', 'BRANCH_MANAGER', 'SUPER_ADMIN']}>
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
    </BrowserRouter>
  );
};