import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Spinner } from '../components/ui/States';
import { getRoleHomeRoute, getNavigationForRole } from '../lib/navigation';

export type AllowedRoles = Array<'SUPER_ADMIN' | 'MERCHANT_OWNER' | 'BRANCH_MANAGER' | 'KITCHEN' | 'WAITER' | 'CASHIER'>;

interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: AllowedRoles;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, allowedRoles }) => {
  const { isAuthenticated, user, isLoading } = useAuth();
  const location = useLocation();

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