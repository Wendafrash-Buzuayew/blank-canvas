import React, { useMemo, useState } from 'react';
import { Users, Plus, Search } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { EntitySelect } from '../components/ui/EntitySelect';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { FormField } from '../components/ui/FormField';
import { IdentityChip, StatusChip } from '../components/ui/Chip';
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

    if (!formData.name.trim()) return setFormError("Please enter the person's full name.");
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
      <div className="mx-auto max-w-[80rem] space-y-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="flex items-center gap-2 text-title-m text-ink">
              <Users className="h-5 w-5 text-brand-press" aria-hidden="true" />
              Users
            </h2>
            <p className="mt-1 text-body-m text-muted">Staff and admin accounts with role-based access</p>
          </div>
          <Button onClick={openCreate}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Add User
          </Button>
        </div>

        {success && (
          <div role="status" className="rounded-control bg-success-soft px-3 py-3 text-label-s text-ink">
            {success}
          </div>
        )}

        {users.length > 0 && (
          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden="true" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search by name or email..."
                aria-label="Search users"
                className="h-11 w-full rounded-control border border-line bg-surface pl-9 pr-3 text-body-m text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark"
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
            action={<Button onClick={openCreate}>Add User</Button>}
          />
        )}

        {users.length > 0 && visibleUsers.length === 0 && (
          <EmptyState title="No matching users" description="Try a different search term or role filter." />
        )}

        {visibleUsers.length > 0 && (
          <>
            {/* DESIGN.md 6.6: below md, a table becomes a card list, not a horizontal scroll. */}
            <div className="space-y-3 md:hidden">
              {visibleUsers.map((u) => (
                <Card key={u.id} compact>
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-label-m text-ink">{u.name}</p>
                      <p className="truncate text-body-m text-muted">{u.email}</p>
                    </div>
                    <StatusChip status={u.enabled === false ? 'neutral' : 'success'} className="shrink-0">
                      {u.enabled === false ? 'Inactive' : 'Active'}
                    </StatusChip>
                  </div>
                  <div className="mt-2 flex items-center justify-between">
                    <IdentityChip>{getRoleLabel(u.role)}</IdentityChip>
                    <span className="text-label-s text-muted">
                      {u.merchantId ? merchantNameById.get(u.merchantId) ?? '-' : 'Platform'}
                    </span>
                  </div>
                </Card>
              ))}
            </div>

            <div className="hidden overflow-x-auto rounded-card border border-line bg-surface shadow-[var(--shadow-card)] md:block">
              <table className="w-full min-w-[44rem] text-body-m">
                <thead className="border-b border-line bg-surface-2">
                  <tr>
                    <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Name</th>
                    <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Email</th>
                    <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Role</th>
                    <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Merchant</th>
                    <th className="px-4 py-3 text-left text-label-s uppercase text-muted">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {visibleUsers.map((u) => (
                    <tr key={u.id} className="min-h-12 hover:bg-surface-2">
                      <td className="px-4 py-3 text-ink">{u.name}</td>
                      <td className="px-4 py-3 text-muted">{u.email}</td>
                      <td className="px-4 py-3">
                        <IdentityChip>{getRoleLabel(u.role)}</IdentityChip>
                      </td>
                      <td className="px-4 py-3 text-muted">
                        {u.merchantId
                          ? merchantNameById.get(u.merchantId) ??
                            (merchantsQuery.isLoading ? 'Loading...' : '-')
                          : 'Platform'}
                      </td>
                      <td className="px-4 py-3">
                        <StatusChip status={u.enabled === false ? 'neutral' : 'success'}>
                          {u.enabled === false ? 'Inactive' : 'Active'}
                        </StatusChip>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>

      <Modal open={showForm} onClose={() => setShowForm(false)} title="Create User">
        <form onSubmit={handleSubmit} className="space-y-3">
          <p className="-mt-1 text-label-s text-muted">They will sign in with this email and password.</p>
          <FormField
            label="Full Name"
            required
            maxLength={100}
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            placeholder="e.g. Abebe Kebede"
          />
          <FormField
            label="Email"
            type="email"
            required
            maxLength={255}
            value={formData.email}
            onChange={(e) => setFormData({ ...formData, email: e.target.value })}
          />
          <FormField
            label="Temporary Password"
            type="password"
            required
            minLength={8}
            value={formData.password}
            onChange={(e) => setFormData({ ...formData, password: e.target.value })}
            hint="At least 8 characters."
          />

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
            <div role="alert" className="rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink">
              {formError}
            </div>
          )}

          <div className="flex gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={() => setShowForm(false)} fullWidth>
              Cancel
            </Button>
            <Button type="submit" loading={createMutation.isPending} fullWidth>
              {createMutation.isPending ? 'Creating...' : 'Create User'}
            </Button>
          </div>
        </form>
      </Modal>
    </DashboardLayout>
  );
};
