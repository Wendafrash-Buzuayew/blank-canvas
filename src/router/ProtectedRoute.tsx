import React, { useEffect } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Spinner } from '../components/ui/States';
import { getRoleHomeRoute, getNavigationForRole } from '../lib/navigation';
import { isPhase2Enabled, isRoleAllowedInPhase } from '../lib/phase';

export type AllowedRoles = Array<'SUPER_ADMIN' | 'MERCHANT_OWNER' | 'BRANCH_MANAGER' | 'KITCHEN' | 'WAITER' | 'CASHIER'>;

interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: AllowedRoles;
  /**
   * Marks a route as Phase 2 only. Set on every table/waiter/kitchen/order/
   * analytics/admin route in AppRouter.tsx - see
   * docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §4.3.
   */
  requiresPhase2?: boolean;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, allowedRoles, requiresPhase2 }) => {
  const { isAuthenticated, user, isLoading, logout } = useAuth();
  const location = useLocation();

  // A role that isn't allowed in the current phase (e.g. a WAITER's stored
  // session, restored on page load) must never reach a protected page - log
  // it out here rather than relying on every caller to re-check.
  const phaseBlocked = Boolean(user) && !isRoleAllowedInPhase(user!.role, isPhase2Enabled());

  useEffect(() => {
    if (phaseBlocked) {
      logout();
    }
  }, [phaseBlocked, logout]);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <Spinner label="Checking session..." />
      </div>
    );
  }

  if (!isAuthenticated) {
    // Redirect to login, preserve the intended destination
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (phaseBlocked) {
    return <Navigate to="/login" state={{ phaseBlocked: true }} replace />;
  }

  if (requiresPhase2 && !isPhase2Enabled()) {
    return <Navigate to={user ? getRoleHomeRoute(user.role) : '/login'} replace />;
  }

  // Role-based access control
  if (allowedRoles && user && !allowedRoles.includes(user.role as any)) {
    // Redirect to the user's home dashboard based on role
    return <Navigate to={getRoleHomeRoute(user.role)} replace />;
  }

  return <>{children}</>;
};

export function getRoleHome(role: string): string {
  return getRoleHomeRoute(role);
}

export function getRoleLabel(role: string): string {
  switch (role) {
    case 'SUPER_ADMIN': return 'Super Admin';
    case 'MERCHANT_OWNER': return 'Merchant';
    case 'BRANCH_MANAGER': return 'Branch Manager';
    case 'KITCHEN': return 'Kitchen';
    case 'WAITER': return 'Waiter';
    case 'CASHIER': return 'Cashier';
    default: return role;
  }
}
