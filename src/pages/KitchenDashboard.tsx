import React, { useMemo } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import { getRoleLabel } from '../router/ProtectedRoute';
import { KitchenDisplaySystem } from '../components/kitchen/KitchenDisplaySystem';
import { Spinner } from '../components/ui/States';
import { Navbar } from '../components/Navbar';
import { useKitchenOrders, useUpdateOrderStatus, useMerchant } from '../hooks/useApiData';
import type { OrderStatus } from '../types';

export const KitchenDashboard: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const merchantId = user?.merchantId;

  const { data: ordersData, isLoading } = useKitchenOrders();
  const { data: merchantData } = useMerchant(merchantId);
  const updateOrderStatusMutation = useUpdateOrderStatus();

  const merchant = useMemo(() => {
    if (!merchantData) return null;
    return {
      id: merchantData.id,
      name: merchantData.name,
      slug: merchantData.slug,
      category: merchantData.category as any,
      logo: merchantData.logoUrl || '',
      coverImage: '',
      phone: merchantData.phone,
      address: merchantData.address,
      city: merchantData.city,
      description: '',
      currency: 'ETB',
      currencySymbol: 'Br',
      createdAt: merchantData.createdAt,
    };
  }, [merchantData]);

  const orders = useMemo(() => {
    if (!ordersData) return [];
    return ordersData.map(o => ({
      id: o.id,
      orderNumber: o.orderNumber,
      merchantId: o.merchantId,
      branchId: String(o.branchId),
      tableId: String(o.tableId),
      tableNumber: o.tableNumber || String(o.tableId),
      customerName: o.customerName,
      items: o.items.map(item => ({
        id: `${o.id}-${item.productId}`,
        menuItemId: String(item.productId),
        name: item.productName,
        price: Number(item.unitPrice),
        quantity: item.quantity,
        notes: item.notes,
      })),
      totalPrice: Number(o.totalAmount),
      status: (o.status === 'DELIVERED' ? 'Served' : o.status.charAt(0) + o.status.slice(1).toLowerCase()) as OrderStatus,
      notes: o.note,
      createdAt: o.createdAt,
      updatedAt: o.createdAt,
      paymentMethod: undefined,
      paymentStatus: undefined,
      estimatedPrepMinutes: 15,
    }));
  }, [ordersData]);

  const fallbackMerchant = {
    id: 'demo',
    name: 'QRServe Kitchen',
    slug: 'qrserve',
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

  const activeMerchant = merchant || fallbackMerchant;

  const handleUpdateOrderStatus = (orderId: string, newStatus: OrderStatus) => {
    updateOrderStatusMutation.mutate({ id: orderId, status: newStatus.toUpperCase() });
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-50">
        <Navbar
          currentView="kitchen"
          setCurrentView={(v) => { if (v === 'landing') navigate('/'); }}
          activeRole={(user?.role.toLowerCase() as any) || 'kitchen'}
          setActiveRole={() => {}}
          selectedMerchant={activeMerchant}
          merchants={merchant ? [merchant] : []}
          setSelectedMerchant={() => {}}
          activeTableNumber="1"
          setActiveTableNumber={() => {}}
          activeTab=""
          setActiveTab={() => {}}
          onResetData={() => {}}
          pendingOrdersCount={0}
          isAuthenticated={true}
          onLoginClick={() => {}}
          onLogout={() => { logout(); navigate('/login'); }}
        />
        <Spinner label="Loading kitchen board..." />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-950">
      <Navbar
        currentView="kitchen"
        setCurrentView={(v) => { if (v === 'landing') navigate('/'); }}
        activeRole={(user?.role.toLowerCase() as any) || 'kitchen'}
        setActiveRole={() => {}}
        selectedMerchant={activeMerchant}
        merchants={merchant ? [merchant] : []}
        setSelectedMerchant={() => {}}
        activeTableNumber="1"
        setActiveTableNumber={() => {}}
        activeTab=""
        setActiveTab={() => {}}
        onResetData={() => {}}
        pendingOrdersCount={orders.filter(o => ['Pending', 'Accepted'].includes(o.status)).length}
        isAuthenticated={true}
        onLoginClick={() => {}}
        onLogout={() => { logout(); navigate('/login'); }}
      />
      <div className="p-4 sm:p-6">
        <div className="mb-4 flex items-center justify-between bg-indigo-50/10 border border-indigo-500/30 rounded-xl px-4 py-3 text-indigo-300">
          <div className="flex items-center gap-2 text-xs font-bold">
            <span className="px-2 py-0.5 bg-indigo-600 text-white rounded-md uppercase tracking-wider text-[10px]">
              {getRoleLabel(user?.role || '')}
            </span>
            <span>Kitchen Display System</span>
          </div>
          <span className="text-[11px] opacity-70 font-mono">{user?.email}</span>
        </div>
        <KitchenDisplaySystem
          merchant={activeMerchant as any}
          orders={orders as any}
          onUpdateOrderStatus={handleUpdateOrderStatus}
        />
      </div>
    </div>
  );
};