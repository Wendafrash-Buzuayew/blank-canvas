import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { isAuthenticated } from '../lib/api';
import { 
  analyticsApi, 
  authApi, 
  branchApi, 
  menuApi, 
  merchantApi, 
  orderApi, 
  qrApi, 
  tableApi,
  waiterApi,
  waiterTaskApi,
  publicApi,
  WaiterRequestType,
  CreateOrderRequest,
  MenuResponse,
  TodayAnalyticsResponse,
  RevenueAnalyticsResponse,
  PopularItemDto,
  KitchenOrder,
  OrderEntity,
  MerchantEntity,
  BranchEntity,
  TableEntity,
  WaiterEntity,
  ProductEntity,
  CreateMerchantRequest,
  CreateBranchRequest,
  CreateTableRequest,
} from '../lib/api';

// ============ Auth Queries ============

export const useLogin = () => {
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      authApi.login(email, password),
  });
};

export const useCreateUser = () => {
  return useMutation({
    mutationFn: authApi.createUser,
  });
};

export const useCurrentUser = () => {
  return useQuery({
    queryKey: ['auth', 'me'],
    queryFn: () => authApi.getMe(),
    enabled: isAuthenticated(),
  });
};

// ============ Merchant Queries ============

export const useMerchant = (merchantId: string | undefined) => {
  return useQuery({
    queryKey: ['merchant', merchantId],
    queryFn: () => merchantApi.getMerchant(merchantId!),
    enabled: !!merchantId && isAuthenticated(),
  });
};

export const useCreateMerchant = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: merchantApi.createMerchant,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['merchants'] });
    },
  });
};

export const useUpdateMerchant = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Parameters<typeof merchantApi.updateMerchant>[1] }) =>
      merchantApi.updateMerchant(id, data),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['merchant', variables.id] });
      queryClient.invalidateQueries({ queryKey: ['merchants'] });
    },
  });
};

export const useDeleteMerchant = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: merchantApi.deleteMerchant,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['merchants'] });
    },
  });
};

// ============ Branch Queries ============

export const useBranches = (merchantId: string | undefined) => {
  return useQuery({
    queryKey: ['branches', merchantId],
    queryFn: () => branchApi.getBranchesByMerchant(merchantId!),
    enabled: !!merchantId && isAuthenticated(),
  });
};

export const useCreateBranch = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: branchApi.createBranch,
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['branches', variables.merchantId] });
    },
  });
};

export const useUpdateBranch = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: CreateBranchRequest }) =>
      branchApi.updateBranch(id, data),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['branches', variables.data.merchantId] });
    },
  });
};

export const useDeleteBranch = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, merchantId }: { id: number; merchantId: string }) =>
      branchApi.deleteBranch(id),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['branches', variables.merchantId] });
    },
  });
};

// ============ Table Queries ============

export const useTables = () => {
  return useQuery({
    queryKey: ['tables'],
    queryFn: () => tableApi.getAllTables(),
    enabled: isAuthenticated(),
  });
};

export const useTable = (tableId: number | undefined) => {
  return useQuery({
    queryKey: ['table', tableId],
    queryFn: () => tableApi.getTable(tableId!),
    enabled: !!tableId && isAuthenticated(),
  });
};

export const useCreateTable = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: tableApi.createTable,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tables'] });
    },
  });
};

export const useUpdateTable = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<CreateTableRequest> }) =>
      tableApi.updateTable(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tables'] });
    },
  });
};

export const useUpdateTableStatus = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      tableApi.updateTableStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tables'] });
    },
  });
};

export const useDeleteTable = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: tableApi.deleteTable,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tables'] });
    },
  });
};

// ============ Waiter Queries ============

export const useWaiters = (params: { merchantId?: string; branchId?: number } = {}) => {
  return useQuery({
    queryKey: ['waiters', params],
    queryFn: () => waiterApi.getWaiters(params),
    enabled: isAuthenticated(),
  });
};

export const useCreateWaiter = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: waiterApi.createWaiter,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['waiters'] });
    },
  });
};

export const useUpdateWaiter = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data, merchantId }: { id: number; data: { status?: string; shift?: string }; merchantId?: string }) =>
      waiterApi.updateWaiter(id, data, merchantId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['waiters'] });
    },
  });
};

export const useDeleteWaiter = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, merchantId }: { id: number; merchantId?: string }) =>
      waiterApi.deleteWaiter(id, merchantId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['waiters'] });
    },
  });
};

// ============ Menu Queries ============

export const useMenu = (merchantId: string | undefined) => {
  return useQuery({
    queryKey: ['menu', merchantId],
    queryFn: () => menuApi.getFullMenu(merchantId!),
    enabled: !!merchantId && isAuthenticated(),
    staleTime: 60_000,
  });
};

export const useCreateCategory = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: menuApi.createCategory,
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['menu', variables.merchantId] });
    },
  });
};

export const useUpdateCategory = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: { name: string; displayOrder?: number } }) =>
      menuApi.updateCategory(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['menu'] });
    },
  });
};

export const useDeleteCategory = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: menuApi.deleteCategory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['menu'] });
    },
  });
};

export const useCreateProduct = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: menuApi.createProduct,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['menu'] });
    },
  });
};

export const useUpdateProduct = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<ProductEntity> }) =>
      menuApi.updateProduct(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['menu'] });
    },
  });
};

export const useDeleteProduct = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: menuApi.deleteProduct,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['menu'] });
    },
  });
};

// ============ Order Queries ============

export const useCreateOrder = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateOrderRequest) => orderApi.createOrder(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      queryClient.invalidateQueries({ queryKey: ['kitchen-orders'] });
    },
  });
};

export const useOrders = () => {
  return useQuery({
    queryKey: ['orders'],
    queryFn: () => orderApi.getAllOrders(),
    enabled: isAuthenticated(),
    refetchInterval: 15_000,
  });
};

export const useKitchenOrders = (params: { status?: string; branchId?: number; merchantId?: string } = {}) => {
  return useQuery({
    queryKey: ['kitchen-orders', params],
    queryFn: () => orderApi.getKitchenOrders(params),
    enabled: isAuthenticated(),
    refetchInterval: 10_000,
  });
};

export const useUpdateOrderStatus = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      orderApi.updateOrderStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      queryClient.invalidateQueries({ queryKey: ['kitchen-orders'] });
    },
  });
};

// ============ QR Queries ============

export const useTableQr = (tableId: number | undefined) => {
  return useQuery({
    queryKey: ['qr', tableId],
    queryFn: () => qrApi.getQrForTable(tableId!),
    enabled: !!tableId && isAuthenticated(),
  });
};

// ============ Analytics Queries ============

export const useTodayAnalytics = (merchantId?: string) => {
  return useQuery({
    queryKey: ['analytics', 'today', merchantId],
    queryFn: () => analyticsApi.getTodayMetrics(merchantId),
    enabled: isAuthenticated(),
    refetchInterval: 30_000,
  });
};

export const useRevenueAnalytics = (merchantId?: string) => {
  return useQuery({
    queryKey: ['analytics', 'revenue', merchantId],
    queryFn: () => analyticsApi.getRevenueAnalytics(merchantId),
    enabled: isAuthenticated(),
    refetchInterval: 60_000,
  });
};

export const usePopularItems = (merchantId?: string) => {
  return useQuery({
    queryKey: ['analytics', 'popular', merchantId],
    queryFn: () => analyticsApi.getPopularItems(merchantId),
    enabled: isAuthenticated(),
    refetchInterval: 60_000,
  });
};

// ============ Backend-to-Frontend Data Mappers ============

export function mapMenuToFrontend(menu: MenuResponse) {
  const categories: any[] = [];
  const menuItems: any[] = [];

  menu.categories.forEach((cat, catIdx) => {
    const category = {
      id: String(cat.id),
      merchantId: '',
      name: cat.name,
      icon: 'Utensils',
      sortOrder: catIdx + 1,
    };
    categories.push(category);

    cat.items.forEach((item) => {
      menuItems.push({
        id: String(item.id),
        merchantId: '',
        categoryId: String(cat.id),
        name: item.name,
        description: item.description || '',
        price: Number(item.price),
        discountPrice: undefined,
        image: item.image || '',
        available: item.available,
        preparationTimeMinutes: item.preparationTime || 10,
        tags: [],
      });
    });
  });

  return { categories, menuItems };
}

export function mapKitchenOrderToFrontend(order: KitchenOrder): OrderEntity {
  return {
    id: order.id,
    orderNumber: order.orderNumber,
    merchantId: order.merchantId,
    branchId: order.branchId,
    tableId: order.tableId,
    tableNumber: order.tableNumber,
    customerName: order.customerName,
    status: order.status,
    totalAmount: Number(order.totalAmount),
    note: order.note,
    createdAt: order.createdAt,
    updatedAt: order.createdAt,
  };
}
// ============ v1 Queries (waiter tasks, public menu resolution) ============

export const useWaiterTasks = (params: {
  merchantId?: string | null;
  branchId?: number | null;
  waiterId?: number;
  userId?: string;
  refetchInterval?: number;
}) => {
  const { merchantId, branchId, waiterId, userId, refetchInterval } = params;
  return useQuery({
    queryKey: ['waiter-tasks', merchantId, branchId, waiterId, userId],
    queryFn: () =>
      waiterTaskApi.getTasks({ merchantId: merchantId!, branchId: branchId!, waiterId, userId }),
    enabled: !!merchantId && branchId != null && isAuthenticated(),
    refetchInterval: refetchInterval ?? 30000,
  });
};

export const useResolveRequest = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      requestId,
      status,
      merchantId,
    }: {
      requestId: number;
      status: 'ACKNOWLEDGED' | 'COMPLETED' | 'CANCELLED';
      merchantId?: string;
    }) => waiterTaskApi.resolveRequest(requestId, status, merchantId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['waiter-tasks'] });
      queryClient.invalidateQueries({ queryKey: ['customer-requests'] });
    },
  });
};

export const useAssignWaiterV1 = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      tableId,
      branchId,
      waiterId,
      shift,
    }: {
      tableId: number;
      branchId: number;
      waiterId: number;
      shift?: string;
    }) => waiterTaskApi.assignWaiter(tableId, { branchId, waiterId, shift }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['waiter-tasks'] });
      queryClient.invalidateQueries({ queryKey: ['tables'] });
    },
  });
};

/** Resolve a QR-scanned public menu URL to merchant/branch/table identifiers. */
export const usePublicMenuResolution = (
  merchantSlug?: string,
  branchSlug?: string,
  tableNumber?: string,
  signature?: string
) => {
  return useQuery({
    queryKey: ['public-menu-resolution', merchantSlug, branchSlug, tableNumber, signature],
    queryFn: () => publicApi.resolveMenu(merchantSlug!, branchSlug!, tableNumber!, signature),
    enabled: !!merchantSlug && !!branchSlug && !!tableNumber,
    retry: 1,
  });
};

/** Full public menu for a resolved merchant. */
export const usePublicMenu = (merchantId?: string | null) => {
  return useQuery({
    queryKey: ['public-menu', merchantId],
    queryFn: () => menuApi.getFullMenu(merchantId!),
    enabled: !!merchantId,
  });
};

export const useCreateTableRequest = () => {
  return useMutation({
    mutationFn: ({
      tableId,
      requestType,
      note,
      customerName,
      merchantId,
      branchId,
    }: {
      tableId: number;
      requestType: WaiterRequestType;
      note?: string;
      customerName?: string;
      merchantId?: string;
      branchId?: number;
    }) => publicApi.createTableRequest(tableId, { requestType, note, customerName, merchantId, branchId }),
  });
};
