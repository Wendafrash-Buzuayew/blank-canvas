export type UserRole = 'customer' | 'merchant_owner' | 'manager' | 'cashier' | 'waiter' | 'kitchen' | 'super_admin';

export type BusinessCategory = 'Restaurant' | 'Coffee Shop' | 'Bar' | 'Hotel' | 'Fast Food' | 'Lounge' | 'Bakery';

/**
 * @deprecated LEGACY DEMO CONTRACT — does NOT match the API.
 *
 * The backend emits UPPERCASE values (AVAILABLE / OCCUPIED / RESERVED); anything
 * compared against these Title-case strings can never match a real response.
 * Retained only because the dead `pages/MerchantDashboard` +
 * `pages/KitchenDashboard` component island still references it.
 *
 * For live code use `TABLE_STATUS` from `src/lib/orderStatus.ts`.
 */
export type TableStatus = 'Available' | 'Occupied' | 'Reserved';

/**
 * @deprecated LEGACY DEMO CONTRACT — does NOT match the API.
 *
 * Two faults: the casing is wrong (the backend emits PENDING, PREPARING, ...) and
 * 'Served' is a state the backend never produces — the real terminal serve state
 * is DELIVERED. Retained only for the dead component island noted above.
 *
 * For live code use `ORDER_STATUS` / `OrderStatus` from `src/lib/orderStatus.ts`,
 * which mirrors `com.qrserve.shared.common.OrderStatus`.
 */
export type OrderStatus = 'Pending' | 'Accepted' | 'Preparing' | 'Ready' | 'Served' | 'Paid' | 'Cancelled';

export type ItemTag = 'Vegan' | 'Vegetarian' | 'Gluten-Free' | 'Hot' | 'Spicy' | 'Bestseller' | 'Chef Special' | 'Organic';

export interface Merchant {
  id: string;
  name: string;
  slug: string;
  category: BusinessCategory;
  logo: string;
  coverImage: string;
  phone: string;
  address: string;
  city: string;
  description: string;
  currency: string;
  currencySymbol: string;
  createdAt: string;
}

export interface Branch {
  id: string;
  merchantId: string;
  name: string;
  address: string;
  phone: string;
}

export interface Floor {
  id: string;
  branchId: string;
  name: string; // e.g. "Main Floor", "Rooftop", "VIP Lounge", "Patio"
}

export interface Table {
  id: string;
  branchId: string;
  floorId: string;
  tableNumber: string; // e.g. "Table 12", "VIP-1"
  capacity: number;
  qrCodeUrl: string;
  status: TableStatus;
}

export interface Category {
  id: string;
  merchantId: string;
  name: string;
  icon?: string;
  sortOrder: number;
}

export interface MenuItem {
  id: string;
  merchantId: string;
  categoryId: string;
  name: string;
  description: string;
  price: number;
  discountPrice?: number;
  image: string;
  available: boolean;
  preparationTimeMinutes: number;
  tags: ItemTag[];
}

export interface CartItem {
  menuItem: MenuItem;
  quantity: number;
  notes: string;
}

export interface OrderItem {
  id: string;
  menuItemId: string;
  name: string;
  price: number;
  quantity: number;
  notes?: string;
}

export interface Order {
  id: string;
  orderNumber: string;
  merchantId: string;
  branchId: string;
  tableId: string;
  tableNumber: string;
  customerName?: string;
  customerPhone?: string;
  items: OrderItem[];
  totalPrice: number;
  status: OrderStatus;
  notes?: string;
  createdAt: string;
  updatedAt: string;
  paymentMethod?: 'Cash' | 'Card' | 'M-PESA' | 'Telebirr' | 'Pay at Counter';
  paymentStatus?: 'Unpaid' | 'Paid';
  estimatedPrepMinutes: number;
}

export interface QRDesignConfig {
  primaryColor: string;
  accentColor: string;
  backgroundColor: string;
  patternColor: string;
  frameStyle: 'card' | 'minimal' | 'modern' | 'luxury' | 'stand';
  showLogo: boolean;
  headerTitle: string;
  subTitle: string;
  callToAction: string;
  template: 'Modern' | 'Elegant' | 'Coffee' | 'Luxury Hotel';
}

export interface AnalyticsSummary {
  todaySales: number;
  salesGrowthPercent: number;
  ordersToday: number;
  activeTablesCount: number;
  totalTablesCount: number;
  avgOrderValue: number;
  popularItems: { name: string; salesCount: number; revenue: number }[];
  hourlySales: { hour: string; sales: number; orders: number }[];
}
