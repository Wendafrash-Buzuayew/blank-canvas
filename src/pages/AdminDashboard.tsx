import React, { useMemo } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import { getRoleLabel } from '../router/ProtectedRoute';
import { SuperAdminView } from '../components/admin/SuperAdminView';
import { Spinner } from '../components/ui/States';
import { Navbar } from '../components/Navbar';
import { useMerchant } from '../hooks/useApiData';

export const AdminDashboard: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const merchantId = user?.merchantId;
  const { data: merchantData, isLoading } = useMerchant(merchantId);

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

  const fallbackMerchant = {
    id: 'demo',
    name: 'QRServe Platform',
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

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-50">
        <Navbar
          currentView="superadmin"
          setCurrentView={(v) => { if (v === 'landing') navigate('/'); }}
          activeRole={(user?.role.toLowerCase() as any) || 'super_admin'}
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
        <Spinner label="Loading admin dashboard..." />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar
        currentView="superadmin"
        setCurrentView={(v) => { if (v === 'landing') navigate('/'); }}
        activeRole={(user?.role.toLowerCase() as any) || 'super_admin'}
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
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-6 flex items-center justify-between bg-indigo-50 border border-indigo-200 rounded-xl px-4 py-3">
          <div className="flex items-center gap-2 text-xs font-bold text-indigo-800">
            <span className="px-2 py-0.5 bg-indigo-600 text-white rounded-md uppercase tracking-wider text-[10px]">
              {getRoleLabel(user?.role || '')}
            </span>
            <span>Platform Management</span>
          </div>
          <span className="text-[11px] text-indigo-600 font-mono">{user?.email}</span>
        </div>
        <SuperAdminView
          merchants={merchant ? [merchant] : []}
          onSelectMerchant={() => navigate('/merchant')}
        />
      </div>
    </div>
  );
};