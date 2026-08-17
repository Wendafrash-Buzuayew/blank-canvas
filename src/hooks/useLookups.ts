import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  authApi,
  branchApi,
  merchantApi,
  tableApi,
  waiterApi,
  BranchEntity,
  MerchantEntity,
  TableEntity,
  UserInfoResponse,
  WaiterEntity,
} from '../lib/api';
import { useAuth } from '../context/AuthContext';

/**
 * Central lookup layer.
 *
 * Every relationship in QRServe is stored as a UUID / numeric id, but the UI must
 * always render human-readable names. These hooks fetch each collection ONCE via
 * TanStack Query and expose lookup maps so rows can resolve names locally
 * (no N+1 requests).
 */

const LOOKUP_STALE_TIME = 5 * 60_000;

export const useMerchantsLookup = () => {
  const { user, isAuthenticated: isAuth } = useAuth();
  const isSuperAdmin = user?.role === 'SUPER_ADMIN';

  return useQuery({
    queryKey: ['lookup', 'merchants', isSuperAdmin ? 'all' : user?.merchantId],
    queryFn: async (): Promise<MerchantEntity[]> => {
      if (isSuperAdmin) return merchantApi.getAllMerchants();
      if (!user?.merchantId) return [];
      const merchant = await merchantApi.getMerchant(user.merchantId);
      return merchant ? [merchant] : [];
    },
    enabled: isAuth && (isSuperAdmin || !!user?.merchantId),
    staleTime: LOOKUP_STALE_TIME,
  });
};

/** All branches the current user can see, fetched once per merchant and cached. */
export const useBranchesLookup = (merchantIdFilter?: string | null) => {
  const { isAuthenticated: isAuth } = useAuth();
  const merchantsQuery = useMerchantsLookup();
  const merchants = merchantsQuery.data ?? [];
  const merchantIds = useMemo(() => merchants.map((m) => m.id).sort(), [merchants]);

  const query = useQuery({
    queryKey: ['lookup', 'branches', merchantIds],
    queryFn: async (): Promise<BranchEntity[]> => {
      const results = await Promise.all(
        merchantIds.map((id) => branchApi.getBranchesByMerchant(id).catch(() => [] as BranchEntity[]))
      );
      return results.flat();
    },
    enabled: isAuth && merchantIds.length > 0,
    staleTime: LOOKUP_STALE_TIME,
  });

  const data = useMemo(() => {
    const all = query.data ?? [];
    return merchantIdFilter ? all.filter((b) => b.merchantId === merchantIdFilter) : all;
  }, [query.data, merchantIdFilter]);

  return {
    ...query,
    data,
    isLoading: query.isLoading || merchantsQuery.isLoading,
  };
};

export const useUsersLookup = () => {
  const { user, isAuthenticated: isAuth } = useAuth();
  return useQuery({
    queryKey: ['lookup', 'users', user?.merchantId ?? 'all'],
    queryFn: (): Promise<UserInfoResponse[]> => authApi.listUsers(user?.merchantId ?? undefined),
    enabled: isAuth,
    staleTime: LOOKUP_STALE_TIME,
  });
};

export const useTablesLookup = () => {
  const { isAuthenticated: isAuth } = useAuth();
  return useQuery({
    queryKey: ['tables'],
    queryFn: (): Promise<TableEntity[]> => tableApi.getAllTables(),
    enabled: isAuth,
    staleTime: 60_000,
  });
};

export const useWaitersLookup = (params: { branchId?: number } = {}) => {
  const { isAuthenticated: isAuth } = useAuth();
  return useQuery({
    queryKey: ['waiters', params],
    queryFn: (): Promise<WaiterEntity[]> => waiterApi.getWaiters(params),
    enabled: isAuth,
    staleTime: 60_000,
  });
};

export interface RelationshipResolvers {
  merchants: MerchantEntity[];
  branches: BranchEntity[];
  users: UserInfoResponse[];
  tables: TableEntity[];
  waiters: WaiterEntity[];
  merchantName: (id?: string | null) => string;
  branchName: (id?: number | null) => string;
  userName: (id?: string | null) => string;
  userEmail: (id?: string | null) => string;
  tableLabel: (id?: number | null) => string;
  waiterName: (id?: number | null) => string;
  isLoading: boolean;
}

/**
 * One-stop relationship resolver. Fetches each collection once and returns
 * `Map`-backed helpers so lists never trigger a request per row.
 */
export function useRelationships(): RelationshipResolvers {
  const merchantsQuery = useMerchantsLookup();
  const branchesQuery = useBranchesLookup();
  const usersQuery = useUsersLookup();
  const tablesQuery = useTablesLookup();
  const waitersQuery = useWaitersLookup();

  const merchants = merchantsQuery.data ?? [];
  const branches = branchesQuery.data ?? [];
  const users = usersQuery.data ?? [];
  const tables = tablesQuery.data ?? [];
  const waiters = waitersQuery.data ?? [];

  const merchantMap = useMemo(() => new Map(merchants.map((m) => [m.id, m])), [merchants]);
  const branchMap = useMemo(() => new Map(branches.map((b) => [b.id, b])), [branches]);
  const userMap = useMemo(() => new Map(users.map((u) => [u.id, u])), [users]);
  const tableMap = useMemo(() => new Map(tables.map((t) => [t.id, t])), [tables]);
  const waiterMap = useMemo(() => new Map(waiters.map((w) => [w.id, w])), [waiters]);

  const isLoading =
    merchantsQuery.isLoading ||
    branchesQuery.isLoading ||
    usersQuery.isLoading ||
    tablesQuery.isLoading ||
    waitersQuery.isLoading;

  const placeholder = (loading: boolean) => (loading ? 'Loading…' : '—');

  return {
    merchants,
    branches,
    users,
    tables,
    waiters,
    isLoading,
    merchantName: (id) =>
      !id ? '—' : merchantMap.get(id)?.name ?? placeholder(merchantsQuery.isLoading),
    branchName: (id) =>
      id == null ? '—' : branchMap.get(id)?.name ?? placeholder(branchesQuery.isLoading),
    userName: (id) => (!id ? '—' : userMap.get(id)?.name ?? placeholder(usersQuery.isLoading)),
    userEmail: (id) => (!id ? '' : userMap.get(id)?.email ?? ''),
    tableLabel: (id) => {
      if (id == null) return '—';
      const table = tableMap.get(id);
      return table ? `Table ${table.tableNumber}` : placeholder(tablesQuery.isLoading);
    },
    waiterName: (id) => {
      if (id == null) return 'Unassigned';
      const waiter = waiterMap.get(id);
      if (!waiter) return placeholder(waitersQuery.isLoading);
      return userMap.get(waiter.userId)?.name ?? placeholder(usersQuery.isLoading);
    },
  };
}