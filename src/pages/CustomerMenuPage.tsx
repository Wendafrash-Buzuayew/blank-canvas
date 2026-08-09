import React, { useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { CustomerMenuView } from '../components/customer/CustomerMenuView';
import { Spinner, ErrorState } from '../components/ui/States';
import { useMenu, useTables } from '../hooks/useApiData';
import { merchantApi } from '../lib/api';
import { useQuery } from '@tanstack/react-query';

export const CustomerMenuPage: React.FC = () => {
  const { merchantSlug, tableNumber } = useParams<{ merchantSlug: string; tableNumber: string }>();

  // Public menu fetch - no auth required for this endpoint
  const { data: merchantData, isLoading: merchantLoading, error: merchantError } = useQuery({
    queryKey: ['public-merchant', merchantSlug],
    queryFn: async () => {
      // The menu endpoint requires merchantId, so we need to resolve the slug.
      // For now, use the raw menu endpoint which takes merchantId.
      // In production, the backend would resolve slug → merchant.
      throw new Error('Merchant resolution by slug requires backend support');
    },
    enabled: false,
  });

  // Load all tables to find the one matching the tableNumber param
  const { data: tablesData, isLoading: tablesLoading } = useTables();

  // Public menu is fetched via merchantId - for now use a demo merchant
  // This is a known limitation: the backend menu endpoint requires merchantId (UUID),
  // not a slug. Full slug resolution requires backend support.
  const { data: menuData, isLoading: menuLoading, error: menuError } = useQuery({
    queryKey: ['public-menu', merchantSlug],
    queryFn: async () => {
      // For public QR menu, we need the merchant ID. Since QR URLs currently use
      // /menu/{merchantSlug}/{branchId}/{tableId}, and the backend uses merchantId,
      // we'll need a slug→merchant resolution endpoint.
      // For now, return empty to show an informative state.
      throw new Error('Public menu requires merchant resolution. Please use the QR code from a registered merchant.');
    },
    enabled: false,
  });

  const fallbackMerchant = {
    id: 'public',
    name: 'QRServe Restaurant',
    slug: merchantSlug || 'restaurant',
    category: 'Restaurant' as const,
    logo: '',
    coverImage: '',
    phone: '',
    address: '',
    city: '',
    description: '',
    currency: 'ETB',
    currencySymbol: 'Br',
    createdAt: '',
  };

  const fallbackTables = useMemo(() => {
    if (!tablesData) return [];
    return tablesData.map(t => ({
      id: String(t.id),
      branchId: String(t.branchId),
      floorId: '',
      tableNumber: t.tableNumber,
      capacity: t.capacity,
      qrCodeUrl: '',
      status: (t.status === 'OCCUPIED' ? 'Occupied' : t.status === 'RESERVED' ? 'Reserved' : 'Available') as any,
    }));
  }, [tablesData]);

  const selectedTableNumber = tableNumber || fallbackTables[0]?.tableNumber || '1';

  return (
    <div className="min-h-screen bg-slate-50">
      <CustomerMenuView
        merchant={fallbackMerchant}
        categories={[]}
        menuItems={[]}
        tables={fallbackTables}
        selectedTableNumber={selectedTableNumber}
        setSelectedTableNumber={() => {}}
        orders={[]}
        onPlaceOrder={() => {}}
      />
      {/* Informational overlay when public menu is empty */}
      <div className="max-w-md mx-auto p-4 -mt-10 relative z-10">
        <div className="bg-white rounded-2xl border border-amber-200 p-4 shadow-md">
          <p className="text-xs font-bold text-amber-800">
            ⚠️ Public QR Menu: This demo shows the customer ordering interface. 
            To fully populate the menu, the backend must support resolving the merchant by slug 
            (e.g. <code className="font-mono">GET /api/menu/{merchantSlug}</code>).
          </p>
        </div>
      </div>
    </div>
  );
};