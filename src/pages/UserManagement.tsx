import React, { useState } from 'react';
import { Users, Plus, X, Loader2 } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { useCreateUser } from '../hooks/useApiData';
import { ApiError } from '../lib/api';

export const UserManagement: React.FC = () => {
  const createMutation = useCreateUser();
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    role: 'WAITER' as 'SUPER_ADMIN' | 'MERCHANT_OWNER' | 'BRANCH_MANAGER' | 'CASHIER' | 'WAITER' | 'KITCHEN',
    merchantId: '',
  });
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  console.log('[UserManagement] form data:', formData);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    try {
      const result = await createMutation.mutateAsync(formData);
      setSuccess(`User created: ${result.email} (${result.role})`);
      setShowForm(false);
      setFormData({ name: '', email: '', password: '', role: 'WAITER', merchantId: '' });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to create user');
    }
  };

  return (
    <DashboardLayout title="User Management">
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-black text-slate-900 flex items-center gap-2">
              <Users className="w-5 h-5 text-[#E60028]" />
              Users
            </h2>
            <p className="text-xs text-slate-500 mt-1">Create user accounts for staff and admins</p>
          </div>
          <button
            onClick={() => { setShowForm(true); setError(null); setSuccess(null); }}
            className="px-4 py-2 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-bold rounded-xl flex items-center gap-2 shadow-sm"
          >
            <Plus className="w-4 h-4" />
            Add User
          </button>
        </div>

        {error && (
          <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700">
            {error}
          </div>
        )}

        {success && (
          <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-xs font-bold text-emerald-700">
            {success}
          </div>
        )}

        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm">
          <p className="text-sm text-slate-600">
            Create new user accounts for waiters, kitchen staff, branch managers, and merchant owners.
            Each user will receive credentials to log in to their role-based dashboard.
          </p>
        </div>
      </div>

      {showForm && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md">
            <div className="flex items-center justify-between p-4 border-b border-slate-200">
              <h3 className="font-bold text-sm">Create User</h3>
              <button onClick={() => setShowForm(false)} className="p-1 text-slate-400 hover:text-slate-900 rounded-lg">
                <X className="w-4 h-4" />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="p-4 space-y-3">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Full Name</label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Email</label>
                <input
                  type="email"
                  required
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Password</label>
                <input
                  type="password"
                  required
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Role</label>
                <select
                  value={formData.role}
                  onChange={(e) => setFormData({ ...formData, role: e.target.value as any })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                >
                  <option value="WAITER">Waiter</option>
                  <option value="KITCHEN">Kitchen Staff</option>
                  <option value="BRANCH_MANAGER">Branch Manager</option>
                  <option value="CASHIER">Cashier</option>
                  <option value="MERCHANT_OWNER">Merchant Owner</option>
                  <option value="SUPER_ADMIN">Super Admin</option>
                </select>
              </div>
              {(formData.role === 'MERCHANT_OWNER' || formData.role === 'BRANCH_MANAGER' || formData.role === 'WAITER' || formData.role === 'KITCHEN' || formData.role === 'CASHIER') && (
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Merchant ID (optional)</label>
                  <input
                    type="text"
                    value={formData.merchantId}
                    onChange={(e) => setFormData({ ...formData, merchantId: e.target.value })}
                    placeholder="UUID of the merchant"
                    className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                  />
                </div>
              )}
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="w-full py-2.5 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-60 text-white text-sm font-bold rounded-xl flex items-center justify-center gap-2"
              >
                {createMutation.isPending ? <><Loader2 className="w-4 h-4 animate-spin" /> Creating...</> : 'Create User'}
              </button>
            </form>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
};