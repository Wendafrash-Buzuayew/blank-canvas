/**
 * API Client for communicating with the QRServe backend microservices
 * via the API Gateway (port 8081)
 */
import {
  Merchant,
  Branch,
  Table,
  Category,
  MenuItem,
  Order,
  OrderStatus,
  TableStatus
} from '../types';

// Environment-based API base URL
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

// ============ Type Definitions (Backend Contract) ============

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface UserInfoResponse {
  id: string;
  name: string;
  email: string;
  role: string;
  merchantId?: string | null;
  branchId?: number | null;
  enabled?: boolean;
}

export interface CreateUserRequest {
  name: string;
  email: string;
  password: string;
  role: 'SUPER_ADMIN' | 'MERCHANT_OWNER' | 'BRANCH_MANAGER' | 'CASHIER' | 'WAITER' | 'KITCHEN';
  merchantId?: string;
}

export interface CreateUserResponse {
  message: string;
  userId: string;
  email: string;
  role: string;
}

export interface CreateMerchantRequest {
  name: string;
  phone: string;
  city: string;
  address: string;
  category: string;
}

export interface CreateBranchRequest {
  merchantId: string;
  name: string;
  phone: string;
  address?: string;
}

export interface CreateTableRequest {
  branchId: number;
  tableNumber: string;
  capacity: number;
}

export interface CreateTableResponse {
  id: number;
  tableNumber: string;
  capacity: number;
  qrUrl: string;
  qrToken: string;
}

export interface MenuResponse {
  categories: {
    id: number;
    name: string;
    items: {
      id: number;
      name: string;
      description: string;
      price: number;
      image: string;
      available: boolean;
      preparationTime: number;
    }[];
  }[];
}

export interface CreateOrderRequest {
  tableId: number;
  customerName?: string;
  note?: string;
  items: {
    productId: number;
    quantity: number;
    notes?: string;
  }[];
}

export interface CreateOrderResponse {
  id: string;
  orderNumber: string;
  status: string;
  estimatedTime: number;
  totalAmount: number;
}

export interface TodayAnalyticsResponse {
  todayRevenue: number;
  totalOrders: number;
  pendingOrders: number;
  avgOrderValue: number;
  occupiedTables: number;
  totalTables: number;
  occupancyRate: number;
}

export interface RevenueAnalyticsResponse {
  totalRevenue: number;
  salesHistory: {
    date: string;
    revenue: number;
    ordersCount: number;
  }[];
}

export interface PopularItemDto {
  productId: number;
  name: string;
  image: string;
  count: number;
  revenue: number;
}

export interface QrMetadataResponse {
  tableId: number;
  qrUrl: string;
  format: string;
  mimeType: string;
  base64Content: string;
}

// ============ Token Management ============

const ACCESS_TOKEN_KEY = 'qrserve_access_token';
const REFRESH_TOKEN_KEY = 'qrserve_refresh_token';
const USER_KEY = 'qrserve_user';

let authToken: string | null = localStorage.getItem(ACCESS_TOKEN_KEY);
let refreshToken: string | null = localStorage.getItem(REFRESH_TOKEN_KEY);
let refreshPromise: Promise<boolean> | null = null;

export function setTokens(access: string, refresh: string) {
  authToken = access;
  refreshToken = refresh;
  localStorage.setItem(ACCESS_TOKEN_KEY, access);
  localStorage.setItem(REFRESH_TOKEN_KEY, refresh);
}

export function clearTokens() {
  authToken = null;
  refreshToken = null;
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function getAuthToken(): string | null {
  return authToken;
}

export function isAuthenticated(): boolean {
  return !!authToken;
}

export function setUser(user: { id: string; email: string; name: string; role: string; merchantId?: string }) {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function getUser(): { id: string; email: string; name: string; role: string; merchantId?: string } | null {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function getTenantId(): string | null {
  return getUser()?.merchantId || null;
}

// ============ Error Handling ============

export class ApiError extends Error {
  status: number;
  code?: string;
  details?: any;

  constructor(status: number, message: string, code?: string, details?: any) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

function getErrorMessage(status: number): string {
  switch (status) {
    case 400: return 'Invalid request. Please check your input.';
    case 401: return 'Your session has expired. Please log in again.';
    case 403: return 'You do not have permission to perform this action.';
    case 404: return 'The requested resource was not found.';
    case 409: return 'A conflict occurred. This item may already exist.';
    case 422: return 'Validation failed. Please check your input.';
    case 500: return 'An unexpected server error occurred. Please try again.';
    case 503: return 'The service is temporarily unavailable. Please try again later.';
    default: return `Request failed with status ${status}.`;
  }
}

// ============ Generic Request Helper ============

async function tryRefreshToken(): Promise<boolean> {
  if (!refreshToken) return false;
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (response.ok) {
        const data = await response.json();
        setTokens(data.accessToken, data.refreshToken);
        return true;
      }
    } catch {
      // Network error during refresh
    }
    clearTokens();
    return false;
  })();

  try {
    return await refreshPromise;
  } finally {
    refreshPromise = null;
  }
}

async function request<T>(
  endpoint: string,
  options: RequestInit & { skipAuth?: boolean; isBlob?: boolean } = {}
): Promise<T> {
  const headers: Record<string, string> = {
    ...(options.body && !(options.body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
    ...((options.headers as Record<string, string>) || {}),
  };

  const token = options.skipAuth ? null : authToken;
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const tenantId = options.skipAuth ? null : getTenantId();
  if (tenantId) {
    headers['X-Tenant-ID'] = tenantId;
  }

  const baseUrl = API_BASE_URL.endsWith('/') ? API_BASE_URL.slice(0, -1) : API_BASE_URL;
  const cleanEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;

  const doFetch = async (): Promise<Response> => {
    return fetch(`${baseUrl}${cleanEndpoint}`, {
      ...options,
      headers,
    });
  };

  let response = await doFetch();

  if (response.status === 401 && !options.skipAuth && refreshToken) {
    const refreshed = await tryRefreshToken();
    if (refreshed) {
      headers['Authorization'] = `Bearer ${authToken}`;
      response = await doFetch();
    }
  }

  if (!response.ok) {
    let errorMessage = getErrorMessage(response.status);
    let errorCode: string | undefined;
    let errorDetails: any;

    try {
      const errorBody = await response.json();
      if (errorBody.message) errorMessage = errorBody.message;
      if (errorBody.error) errorMessage = errorBody.error;
      if (errorBody.code) errorCode = errorBody.code;
      if (errorBody.details) errorDetails = errorBody.details;
      if (errorBody.errors && Array.isArray(errorBody.errors)) {
        errorMessage = errorBody.errors.map((e: any) => e.message || e.defaultMessage).join(', ');
        errorDetails = errorBody.errors;
      }
    } catch {
      // Non-JSON error body
    }

    throw new ApiError(response.status, errorMessage, errorCode, errorDetails);
  }

  if (response.status === 204) {
    return null as T;
  }

  if (options.isBlob) {
    return (await response.blob()) as unknown as T;
  }

  return response.json();
}

// ============ Auth API ============
export const authApi = {
  login: (email: string, password: string) =>
    request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
      skipAuth: true,
    }),

  refresh: (token: string) =>
    request<LoginResponse>('/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken: token }),
      skipAuth: true,
    }),

  logout: () =>
    request<{ message: string }>('/auth/logout', {
      method: 'POST',
    }).catch(() => ({ message: 'Logged out' })),

  getMe: () =>
    request<UserInfoResponse>('/auth/me'),

  createUser: (data: CreateUserRequest) =>
    request<CreateUserResponse>('/auth/users', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
};

// ============ Merchant API ============

export interface MerchantEntity {
  id: string;
  name: string;
  slug: string;
  phone: string;
  city: string;
  address: string;
  logoUrl?: string;
  category: string;
  createdAt: string;
  updatedAt?: string;
}

export type MerchantAPI = {
  createMerchant: (data: CreateMerchantRequest) => Promise<MerchantEntity>;
  getAllMerchants: () => Promise<MerchantEntity[]>;
  getMerchant: (id: string) => Promise<MerchantEntity>;
  updateMerchant: (id: string, data: CreateMerchantRequest) => Promise<MerchantEntity>;
  deleteMerchant: (id: string) => Promise<void>;
};

export const merchantApi: MerchantAPI = {
  createMerchant: (data: CreateMerchantRequest) =>
    request<MerchantEntity>('/merchants', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getAllMerchants: () =>
    request<MerchantEntity[]>('/merchants'),

  getMerchant: (id: string) =>
    request<MerchantEntity>(`/merchants/${id}`),

  updateMerchant: (id: string, data: CreateMerchantRequest) =>
    request<MerchantEntity>(`/merchants/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  deleteMerchant: (id: string) =>
    request<void>(`/merchants/${id}`, {
      method: 'DELETE',
    }),
};

// ============ Branch API ============

export interface BranchEntity {
  id: number;
  merchantId: string;
  name: string;
  phone: string;
  address?: string;
}

export const branchApi = {
  createBranch: (data: CreateBranchRequest) =>
    request<BranchEntity>('/branches', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getBranchesByMerchant: (merchantId: string) =>
    request<BranchEntity[]>(`/branches/merchant/${merchantId}`),

  updateBranch: (id: number, data: CreateBranchRequest) =>
    request<BranchEntity>(`/branches/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  deleteBranch: (id: number) =>
    request<void>(`/branches/${id}`, {
      method: 'DELETE',
    }),
};

// ============ Table API ============

export interface TableEntity {
  id: number;
  branchId: number;
  merchantId: string;
  tableNumber: string;
  capacity: number;
  status: string;
  qrToken: string;
  createdAt: string;
}

export const tableApi = {
  createTable: (data: CreateTableRequest) =>
    request<CreateTableResponse>('/tables', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getTable: (id: number) =>
    request<TableEntity>(`/tables/${id}`),

  getAllTables: () =>
    request<TableEntity[]>('/tables/all'),

  updateTable: (id: number, data: Partial<CreateTableRequest>) =>
    request<TableEntity>(`/tables/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  updateTableStatus: (id: number, status: string) =>
    request<TableEntity>(`/tables/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),

  deleteTable: (id: number) =>
    request<void>(`/tables/${id}`, {
      method: 'DELETE',
    }),
};

// ============ Menu API ============

export interface CategoryEntity {
  id: number;
  merchantId: string;
  name: string;
  displayOrder: number;
}

export interface ProductEntity {
  id: number;
  merchantId: string;
  categoryId: number;
  name: string;
  description: string;
  price: number;
  image?: string;
  available: boolean;
  preparationTime: number;
}

export const menuApi = {
  createCategory: (data: { merchantId: string; name: string; displayOrder?: number }) =>
    request<CategoryEntity>('/categories', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getCategories: (merchantId: string) =>
    request<CategoryEntity[]>(`/categories?merchantId=${merchantId}`),

  updateCategory: (id: number, data: { name: string; displayOrder?: number }) =>
    request<CategoryEntity>(`/categories/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  deleteCategory: (id: number) =>
    request<void>(`/categories/${id}`, {
      method: 'DELETE',
    }),

  createProduct: (data: { categoryId: number; name: string; description: string; price: number; image?: string; preparationTime?: number }) =>
    request<ProductEntity>('/products', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getProducts: (params: { categoryId?: number; merchantId?: string } = {}) => {
    const query = new URLSearchParams();
    if (params.categoryId) query.set('categoryId', String(params.categoryId));
    if (params.merchantId) query.set('merchantId', params.merchantId);
    const qs = query.toString();
    return request<ProductEntity[]>(`/products${qs ? `?${qs}` : ''}`);
  },

  getProduct: (id: number) =>
    request<ProductEntity>(`/products/${id}`),

  updateProduct: (id: number, data: Partial<ProductEntity>) =>
    request<ProductEntity>(`/products/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  deleteProduct: (id: number) =>
    request<void>(`/products/${id}`, {
      method: 'DELETE',
    }),

  getFullMenu: (merchantId: string) =>
    request<MenuResponse>(`/menu/${merchantId}`),
};

// ============ Order API ============

export interface OrderEntity {
  id: string;
  orderNumber: string;
  merchantId: string;
  branchId: number;
  tableId: number;
  tableNumber?: string;
  customerName?: string;
  status: string;
  totalAmount: number;
  note?: string;
  paymentMethod?: string;
  paymentStatus?: string;
  createdAt: string;
  updatedAt: string;
}

export interface KitchenOrderItem {
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
  notes?: string;
}

export interface KitchenOrder {
  id: string;
  orderNumber: string;
  merchantId: string;
  branchId: number;
  tableId: number;
  tableNumber?: string;
  customerName?: string;
  status: string;
  totalAmount: number;
  note?: string;
  createdAt: string;
  items: KitchenOrderItem[];
}

export const orderApi = {
  createOrder: (data: CreateOrderRequest) =>
    request<CreateOrderResponse>('/orders', {
      method: 'POST',
      body: JSON.stringify(data),
      skipAuth: true,
    }),

  getAllOrders: () =>
    request<OrderEntity[]>('/orders'),

  getOrder: (id: string) =>
    request<OrderEntity>(`/orders/${id}`),

  updateOrderStatus: (id: string, status: string) =>
    request<OrderEntity>(`/orders/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),

  getKitchenOrders: (params: { status?: string; branchId?: number; tableId?: number; merchantId?: string } = {}) => {
    const query = new URLSearchParams();
    if (params.status) query.set('status', params.status);
    if (params.branchId != null) query.set('branchId', String(params.branchId));
    if (params.tableId != null) query.set('tableId', String(params.tableId));
    if (params.merchantId) query.set('merchantId', params.merchantId);
    const qs = query.toString();
    return request<KitchenOrder[]>(`/kitchen/orders${qs ? `?${qs}` : ''}`);
  },
};

// ============ QR API ============

export const qrApi = {
  getQrForTable: (tableId: number) =>
    request<QrMetadataResponse>(`/qr/${tableId}`),

  exportPng: (data: { tableId: number; format?: string; brandColor?: string; titleText?: string }) =>
    request<Blob>('/qr/export/png', {
      method: 'POST',
      body: JSON.stringify(data),
      isBlob: true,
    }),

  exportPdf: (data: { tableId: number; format?: string; brandColor?: string; titleText?: string }) =>
    request<Blob>('/qr/export/pdf', {
      method: 'POST',
      body: JSON.stringify(data),
      isBlob: true,
    }),
};

// ============ Waiter API ============

export interface WaiterEntity {
  id: number;
  merchantId: string;
  branchId: number;
  userId: string;
  status: string;
  shift?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface TableAssignmentEntity {
  id: number;
  merchantId: string;
  branchId: number;
  tableId: number;
  waiterId: number;
  assignedAt: string;
  endedAt?: string;
  status: string;
  shift?: string;
}

export interface CustomerRequestEntity {
  id: number;
  merchantId: string;
  branchId: number;
  tableId: number;
  requestType: string;
  status: string;
  note?: string;
  createdAt: string;
  resolvedAt?: string;
}

export const waiterApi = {
  createWaiter: (data: { merchantId: string; branchId: number; userId: string; status?: string; shift?: string }) =>
    request<WaiterEntity>('/waiters', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getWaiters: (params: { merchantId?: string; branchId?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.merchantId) query.set('merchantId', params.merchantId);
    if (params.branchId != null) query.set('branchId', String(params.branchId));
    const qs = query.toString();
    return request<WaiterEntity[]>(`/waiters${qs ? `?${qs}` : ''}`);
  },

  getWaiter: (id: number, merchantId?: string) => {
    const query = merchantId ? `?merchantId=${merchantId}` : '';
    return request<WaiterEntity>(`/waiters/${id}${query}`);
  },

  updateWaiter: (id: number, data: { status?: string; shift?: string }, merchantId?: string) => {
    const query = merchantId ? `?merchantId=${merchantId}` : '';
    return request<WaiterEntity>(`/waiters/${id}${query}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  deleteWaiter: (id: number, merchantId?: string) => {
    const query = merchantId ? `?merchantId=${merchantId}` : '';
    return request<void>(`/waiters/${id}${query}`, {
      method: 'DELETE',
    });
  },
};

export const tableAssignmentApi = {
  assignWaiter: (data: { merchantId: string; branchId: number; tableId: number; waiterId: number; shift?: string }) =>
    request<TableAssignmentEntity>('/table-assignments', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getAssignmentsByTable: (tableId: number) =>
    request<TableAssignmentEntity[]>(`/table-assignments/table/${tableId}`),

  getActiveAssignmentByTable: (tableId: number) =>
    request<TableAssignmentEntity | null>(`/table-assignments/table/${tableId}/active`),

  getActiveByWaiter: (waiterId: number) =>
    request<TableAssignmentEntity[]>(`/table-assignments/waiter/${waiterId}/active`),

  getByMerchant: (merchantId: string) =>
    request<TableAssignmentEntity[]>(`/table-assignments/merchant/${merchantId}`),

  endAssignment: (id: number, merchantId: string) =>
    request<TableAssignmentEntity>(`/table-assignments/${id}/end?merchantId=${merchantId}`, {
      method: 'PUT',
    }),
};

export const customerRequestApi = {
  createRequest: (data: { merchantId: string; branchId: number; tableId: number; requestType: string; note?: string }) =>
    request<CustomerRequestEntity>('/customer-requests', {
      method: 'POST',
      body: JSON.stringify(data),
      skipAuth: true,
    }),

  getRequests: (params: { merchantId?: string; branchId?: number; status?: string } = {}) => {
    const query = new URLSearchParams();
    if (params.merchantId) query.set('merchantId', params.merchantId);
    if (params.branchId != null) query.set('branchId', String(params.branchId));
    if (params.status) query.set('status', params.status);
    const qs = query.toString();
    return request<CustomerRequestEntity[]>(`/customer-requests${qs ? `?${qs}` : ''}`);
  },

  getRequestsByTable: (tableId: number) =>
    request<CustomerRequestEntity[]>(`/customer-requests/table/${tableId}`),

  updateRequestStatus: (id: number, status: string, merchantId?: string) => {
    const query = merchantId ? `?merchantId=${merchantId}` : '';
    return request<CustomerRequestEntity>(`/customer-requests/${id}${query}`, {
      method: 'PUT',
      body: JSON.stringify({ status }),
    });
  },
};

// ============ Analytics API ============

export const analyticsApi = {
  getTodayMetrics: (merchantId?: string) => {
    const query = merchantId ? `?merchantId=${merchantId}` : '';
    return request<TodayAnalyticsResponse>(`/analytics/today${query}`);
  },

  getRevenueAnalytics: (merchantId?: string) => {
    const query = merchantId ? `?merchantId=${merchantId}` : '';
    return request<RevenueAnalyticsResponse>(`/analytics/revenue${query}`);
  },

  getPopularItems: (merchantId?: string) => {
    const query = merchantId ? `?merchantId=${merchantId}` : '';
    return request<PopularItemDto[]>(`/analytics/popular-items${query}`);
  },
};
// ============================================================================
// v1 API surface (Phase 2/3 contracts served by merchant-service)
// ============================================================================

export type WaiterRequestType = 'CALL_WAITER' | 'REQUEST_WATER' | 'REQUEST_BILL';

export interface PublicMenuResolutionResponse {
  merchantId: string;
  merchantName?: string;
  merchantSlug?: string;
  branchId: number;
  branchName?: string;
  branchSlug?: string;
  tableId: number;
  tableNumber: string;
  currency?: string;
}

export interface WaiterAssignmentDto {
  assignmentId?: number;
  merchantId?: string;
  branchId: number;
  tableId: number;
  tableNumber?: string;
  waiterId: number;
  userId?: string;
  waiterName?: string;
  shift?: string;
  status?: string;
  assignedAt?: string;
  endedAt?: string;
}

export interface WaiterTasksResponse {
  waiterId: number | null;
  merchantId: string;
  branchId: number;
  assignedTables: WaiterAssignmentDto[];
  pendingRequests: CustomerRequestEntity[];
}

/** GET /api/v1/public/menu/{merchantSlug}/{branchSlug}/{tableNumber} */
export const publicApi = {
  resolveMenu: (merchantSlug: string, branchSlug: string, tableNumber: string, signature?: string) =>
    request<PublicMenuResolutionResponse>(
      `/v1/public/menu/${encodeURIComponent(merchantSlug)}/${encodeURIComponent(branchSlug)}/${encodeURIComponent(tableNumber)}${signature ? `?signature=${encodeURIComponent(signature)}` : ''}`,
      { skipAuth: true }
    ),

  health: () => request<{ status: string }>('/v1/public/menu/health', { skipAuth: true }),

  /** POST /api/v1/tables/{tableId}/requests — customer service call from a table */
  createTableRequest: (
    tableId: number,
    body: { requestType: WaiterRequestType; note?: string; customerName?: string; merchantId?: string; branchId?: number }
  ) =>
    request<CustomerRequestEntity>(`/v1/tables/${tableId}/requests`, {
      method: 'POST',
      body: JSON.stringify(body),
      skipAuth: true,
    }),
};

/** Waiter task board + request resolution (v1) */
export const waiterTaskApi = {
  /** GET /api/v1/waiters/tasks */
  getTasks: (params: { merchantId: string; branchId: number; waiterId?: number; userId?: string }) => {
    const query = new URLSearchParams();
    query.set('merchantId', params.merchantId);
    query.set('branchId', String(params.branchId));
    if (params.waiterId != null) query.set('waiterId', String(params.waiterId));
    if (params.userId) query.set('userId', params.userId);
    return request<WaiterTasksResponse>(`/v1/waiters/tasks?${query.toString()}`);
  },

  /** PATCH /api/v1/requests/{requestId}/resolve */
  resolveRequest: (requestId: number, status: 'ACKNOWLEDGED' | 'COMPLETED' | 'CANCELLED', merchantId?: string) =>
    request<CustomerRequestEntity>(
      `/v1/requests/${requestId}/resolve${merchantId ? `?merchantId=${merchantId}` : ''}`,
      { method: 'PATCH', body: JSON.stringify({ status }) }
    ),

  /** POST /api/v1/tables/{tableId}/assign-waiter */
  assignWaiter: (tableId: number, body: { branchId: number; waiterId: number; shift?: string }) =>
    request<WaiterAssignmentDto>(`/v1/tables/${tableId}/assign-waiter`, {
      method: 'POST',
      body: JSON.stringify({ ...body, tableId }),
    }),
};
