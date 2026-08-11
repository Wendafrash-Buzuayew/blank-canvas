import React, { useMemo, useState } from 'react';
import { Users, Plus, X, Loader2, Search } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { EntitySelect } from '../components/ui/EntitySelect';
import { useCreateUser } from '../hooks/useApiData';
import { useMerchantsLookup, useUsersLookup } from '../hooks/useLookups';
import { friendlyError } from '../lib/errors';
import { getRoleLabel } from '../router/ProtectedRoute';

const ROLES = [
  { id: 'WAITER', name: 'Waiter' },
  { id: 'KITCHEN', name: 'Kitchen Staff' },
  { id: 'CASHIER', name: 'Cashier' },
  { id: 'BRANCH_MANAGER', name: 'Branch Manager' },
  { id: 'MERCHANT_OWNER', name: 'Merchant Owner' },
  { id: 'SUPER_ADMIN', name: 'Super Admin' },
];

const TENANT_ROLES = ['MERCHANT_OWNER', 'BRANCH_MANAGER', 'WAITER', 'KITCHEN', 'CASHIER'];

export const UserManagement: React.FC = () => {
  const usersQuery = useUsersLookup();
  const merchantsQuery = useMerchantsLookup();
  const createMutation = useCreateUser();

  const [showForm, setShowForm] = useState(false);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    role: 'WAITER',
    merchantId: '',
  });
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const users = usersQuery.data ?? [];
  const merchants = merchantsQuery.data ?? [];
  const merchantNameById = useMemo(() => new Map(merchants.map((m) => [m.id, m.name])), [merchants]);

  const visibleUsers = useMemo(() => {
    const q = search.trim().toLowerCase();
    return users.filter((u) => {
      const matchesRole = !roleFilter || u.role === roleFilter;
      const matchesSearch =
        !q || u.name?.toLowerCase().includes(q) || u.email?.toLowerCase().includes(q);
      return matchesRole && matchesSearch;
    });
  }, [users, search, roleFilter]);

  const openCreate = () => {
    setFormData({
      name: '',
      email: '',
      password: '',
      role: 'WAITER',
      merchantId: merchants.length === 1 ? merchants[0].id : '',
    });
    setFormError(null);
    setSuccess(null);
    setShowForm(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    if (!formData.name.trim()) return setFormError('Please enter the person’s full name.');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email.trim()))
      return setFormError('Please enter a valid email address.');
    if (formData.password.length < 8)
      return setFormError('Password must be at least 8 characters long.');
    if (TENANT_ROLES.includes(formData.role) && !formData.merchantId)
      return setFormError('Please choose the merchant this person belongs to.');

    try {
      const result = await createMutation.mutateAsync({
        ...formData,
        name: formData.name.trim(),
        email: formData.email.trim(),
        role: formData.role as any,
        merchantId: formData.merchantId || undefined,
      });
      setSuccess(`${formData.name.trim()} was added as ${getRoleLabel(result.role)}.`);
      setShowForm(false);
      usersQuery.refetch();
    } catch (err) {
      setFormError(friendlyError(err, 'We could not create this user.'));
    }
  };

  const needsMerchant = TENANT_ROLES.includes(formData.role);

  return (
    <DashboardLayout title="User Management">
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <h2 className="text-xl font-black text-slate-900 flex items-center gap-2">
              <Users className="w-5 h-5 text-[#E60028]" />
              Users
            </h2>
            <p className="text-xs text-slate-500 mt-1">Staff and admin accounts with role-based access</p>
          </div>
          <button
            onClick={openCreate}
            className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-bold rounded-xl flex items-center justify-center gap-2 shadow-sm"
          >
            <Plus className="w-4 h-4" />
            Add User
          </button>
        </div>

        {success && (
          <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-xs font-bold text-emerald-700">
            {success}
          </div>
        )}

        {users.length > 0 && (
          <div className="flex flex-col sm:flex-row gap-3">
            <div className="relative flex-1">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search by name or email..."
                aria-label="Search users"
                className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-slate-300 bg-white focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
              />
            </div>
            <div className="sm:w-56">
              <EntitySelect
                placeholder="All roles"
                clearable
                value={roleFilter}
                onChange={(value) => setRoleFilter(value ?? '')}
                options={ROLES}
                searchThreshold={99}
              />
            </div>
          </div>
        )}

        {usersQuery.isLoading && <Spinner label="Loading users..." />}

        {!usersQuery.isLoading && usersQuery.error && (
          <ErrorState
            message={friendlyError(usersQuery.error, 'We could not load user accounts.')}
            onRetry={() => usersQuery.refetch()}
          />
        )}

        {!usersQuery.isLoading && !usersQuery.error && users.length === 0 && (
          <EmptyState
            title="No users available"
            description="Create a user before assigning waiters or kitchen staff."
            action={
              <button
                onClick={openCreate}
                className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-bold rounded-xl"
              >
                Add User
              </button>
            }
          />
        )}

        {users.length > 0 && visibleUsers.length === 0 && (
          <EmptyState title="No matching users" description="Try a different search term or role filter." />
        )}

        {visibleUsers.length > 0 && (
          <div className="bg-white rounded-2xl border border-slate-200 overflow-x-auto shadow-sm">
            <table className="w-full text-sm min-w-[700px]">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Name</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Email</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Role</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Merchant</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-slate-600 uppercase">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {visibleUsers.map((u) => (
                  <tr key={u.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-bold text-slate-900">{u.name}</td>
                    <td className="px-4 py-3 text-slate-600">{u.email}</td>
                    <td className="px-4 py-3">
                      <span className="text-[10px] font-bold px-2 py-1 bg-slate-100 text-slate-700 rounded uppercase tracking-wider">
                        {getRoleLabel(u.role)}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-slate-600">
                      {u.merchantId
                        ? merchantNameById.get(u.merchantId) ??
                          (merchantsQuery.isLoading ? 'Loading…' : '—')
                        : 'Platform'}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`text-[10px] font-bold px-2 py-1 rounded ${
                          u.enabled === false
                            ? 'bg-slate-100 text-slate-500'
                            : 'bg-emerald-50 text-emerald-700'
                        }`}
                      >
                        {u.enabled === false ? 'Inactive' : 'Active'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showForm && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4">
          <div className="bg-white rounded-t-2xl sm:rounded-2xl shadow-xl w-full sm:max-w-md max-h-[90vh] overflow-y-auto">
            <div className="flex items-start justify-between p-4 border-b border-slate-200">
              <div>
                <h3 className="font-bold text-sm">Create User</h3>
                <p className="text-[11px] text-slate-500 mt-0.5">
                  They will sign in with this email and password.
                </p>
              </div>
              <button
                onClick={() => setShowForm(false)}
                aria-label="Close"
                className="p-1 text-slate-400 hover:text-slate-900 rounded-lg"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="p-4 space-y-3">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Full Name *</label>
                <input
                  type="text"
                  required
                  maxLength={100}
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="e.g. Abebe Kebede"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Email *</label>
                <input
                  type="email"
                  required
                  maxLength={255}
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Temporary Password *</label>
                <input
                  type="password"
                  required
                  minLength={8}
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
                <p className="text-[11px] text-slate-500 mt-1">At least 8 characters.</p>
              </div>

              <EntitySelect
                label="Role"
                required
                value={formData.role}
                onChange={(value) => setFormData({ ...formData, role: value ?? 'WAITER' })}
                options={ROLES}
                searchThreshold={99}
              />

              {needsMerchant && (
                <EntitySelect
                  label="Merchant"
                  required
                  placeholder="Select merchant"
                  value={formData.merchantId}
                  onChange={(value) => setFormData({ ...formData, merchantId: value ?? '' })}
                  options={merchants}
                  descriptionKey="city"
                  isLoading={merchantsQuery.isLoading}
                  loadingMessage="Loading merchants..."
                  emptyMessage="No merchants found. Create a merchant first."
                />
              )}

              {formError && (
                <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700">
                  {formError}
                </div>
              )}

              <div className="flex gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => setShowForm(false)}
                  className="flex-1 py-2.5 border border-slate-200 text-slate-700 text-sm font-bold rounded-xl hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending}
                  className="flex-1 py-2.5 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-60 text-white text-sm font-bold rounded-xl flex items-center justify-center gap-2"
                >
                  {createMutation.isPending ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" /> Creating...
                    </>
                  ) : (
                    'Create User'
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
};
