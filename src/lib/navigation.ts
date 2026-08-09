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
  type LucideIcon,
} from 'lucide-react';

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
    { label: 'Settings', path: '/admin/settings', icon: Settings },
  ],
  MERCHANT_ADMIN: [
    { label: 'Dashboard', path: '/merchant/dashboard', icon: LayoutDashboard },
    { label: 'Branches', path: '/merchant/branches', icon: Building2 },
    { label: 'Tables', path: '/merchant/tables', icon: TableIcon },
    { label: 'Menu', path: '/merchant/menu', icon: Utensils },
    { label: 'Orders', path: '/merchant/orders', icon: ShoppingBag },
    { label: 'Waiters', path: '/merchant/waiters', icon: UserCog },
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
 * Get the navigation items for a given role.
 * Falls back to an empty array if the role is unknown.
 */
export function getNavigationForRole(role: string): NavItem[] {
  const items = ROLE_NAVIGATION[role] || [];
  console.log('[Navigation] Role:', role, 'sidebarItems:', items);
  return items;
}

/**
 * Get the home route for a given role.
 */
export function getRoleHomeRoute(role: string): string {
  const items = ROLE_NAVIGATION[role];
  if (items && items.length > 0) {
    return items[0].path;
  }
  return '/login';
}