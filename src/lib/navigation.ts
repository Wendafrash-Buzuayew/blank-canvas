import {
  LayoutDashboard,
  Store,
  Building2,
  Users,
  Table as TableIcon,
  UserCog,
  BarChart3,
  Settings,
  CreditCard,
  Utensils,
  ShoppingBag,
  ChefHat,
  ClipboardList,
  CheckCircle2,
  QrCode,
  Star,
  ShieldCheck,
  type LucideIcon,
} from 'lucide-react';

import { isPhase2Enabled } from './phase';

export interface NavItem {
  label: string;
  path: string;
  icon: LucideIcon;
}

/**
 * Role-based navigation configuration.
 * Each role gets a specific set of navigation items that map to real routes.
 */
export const ROLE_NAVIGATION: Record<string, NavItem[]> = {
  SUPER_ADMIN: [
    { label: 'Dashboard', path: '/admin/dashboard', icon: LayoutDashboard },
    { label: 'Merchants', path: '/admin/merchants', icon: Store },
    { label: 'Branches', path: '/admin/branches', icon: Building2 },
    { label: 'Users', path: '/admin/users', icon: Users },
    { label: 'Tables', path: '/admin/tables', icon: TableIcon },
    { label: 'Waiters', path: '/admin/waiters', icon: UserCog },
    { label: 'Analytics', path: '/admin/analytics', icon: BarChart3 },
    { label: 'Subscriptions', path: '/admin/subscriptions', icon: CreditCard },
    { label: 'Back Office', path: '/admin/back-office', icon: ShieldCheck },
    { label: 'Settings', path: '/admin/settings', icon: Settings },
  ],
  MERCHANT_OWNER: [
    { label: 'Dashboard', path: '/merchant/dashboard', icon: LayoutDashboard },
    { label: 'Branches', path: '/merchant/branches', icon: Building2 },
    { label: 'Users', path: '/merchant/users', icon: Users },
    { label: 'Tables', path: '/merchant/tables', icon: TableIcon },
    { label: 'Menu', path: '/merchant/menu', icon: Utensils },
    { label: 'Orders', path: '/merchant/orders', icon: ShoppingBag },
    { label: 'Waiters', path: '/merchant/waiters', icon: UserCog },
    { label: 'Reviews', path: '/merchant/reviews', icon: Star },
    { label: 'Analytics', path: '/merchant/analytics', icon: BarChart3 },
    { label: 'Settings', path: '/merchant/settings', icon: Settings },
  ],
  BRANCH_MANAGER: [
    { label: 'Dashboard', path: '/branch/dashboard', icon: LayoutDashboard },
    { label: 'Orders', path: '/branch/orders', icon: ShoppingBag },
    { label: 'Tables', path: '/branch/tables', icon: TableIcon },
    { label: 'Waiters', path: '/branch/waiters', icon: UserCog },
    { label: 'Kitchen', path: '/branch/kitchen', icon: ChefHat },
    { label: 'Reports', path: '/branch/reports', icon: BarChart3 },
  ],
  WAITER: [
    { label: 'Dashboard', path: '/waiter/dashboard', icon: LayoutDashboard },
    { label: 'Assigned Tables', path: '/waiter/tables', icon: TableIcon },
    { label: 'Orders', path: '/waiter/orders', icon: ShoppingBag },
    { label: 'Requests', path: '/waiter/requests', icon: ClipboardList },
  ],
  KITCHEN: [
    { label: 'Dashboard', path: '/kitchen/dashboard', icon: LayoutDashboard },
    { label: 'Incoming Orders', path: '/kitchen/incoming', icon: ShoppingBag },
    { label: 'Preparing', path: '/kitchen/preparing', icon: ChefHat },
    { label: 'Ready Orders', path: '/kitchen/ready', icon: CheckCircle2 },
  ],
};

/**
 * Phase 1 navigation: only MERCHANT_OWNER has anything to show. Every other
 * role's Phase 1 experience is "no navigation, log out" - see
 * isRoleAllowedInPhase in ./phase.ts and its use in ProtectedRoute/LoginPage.
 */
export const ROLE_NAVIGATION_PHASE1: Record<string, NavItem[]> = {
  MERCHANT_OWNER: [
    { label: 'Dashboard', path: '/merchant/dashboard', icon: LayoutDashboard },
    { label: 'Menu & QR', path: '/merchant/menu', icon: QrCode },
    { label: 'Reviews', path: '/merchant/reviews', icon: Star },
    { label: 'Settings', path: '/merchant/settings', icon: Settings },
  ],
};

/**
 * Get the navigation items for a given role, respecting the current phase.
 * In Phase 1, ROLE_NAVIGATION_PHASE1 is used instead of the full table, so
 * every role except MERCHANT_OWNER gets an empty list rather than a filtered
 * one - falling back to '/login' below, not a route with no nav to reach it.
 */
export function getNavigationForRole(
  role: string,
  phase2Enabled: boolean = isPhase2Enabled(),
): NavItem[] {
  const table = phase2Enabled ? ROLE_NAVIGATION : ROLE_NAVIGATION_PHASE1;
  return table[role] || [];
}

/**
 * Get the home route for a given role, respecting the current phase.
 */
export function getRoleHomeRoute(
  role: string,
  phase2Enabled: boolean = isPhase2Enabled(),
): string {
  const items = getNavigationForRole(role, phase2Enabled);
  if (items.length > 0) {
    return items[0].path;
  }
  return '/login';
}