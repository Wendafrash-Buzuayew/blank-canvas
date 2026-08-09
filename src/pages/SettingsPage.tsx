import React from 'react';
import { Settings as SettingsIcon, User, Shield, Bell } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { useAuth } from '../context/AuthContext';
import { getRoleLabel } from '../router/ProtectedRoute';

export const SettingsPage: React.FC = () => {
  const { user } = useAuth();

  return (
    <DashboardLayout title="Settings">
      <div className="space-y-6">
        <div>
          <h2 className="text-xl font-black text-slate-900 flex items-center gap-2">
            <SettingsIcon className="w-5 h-5 text-[#E60028]" />
            Settings
          </h2>
          <p className="text-xs text-slate-500 mt-1">Manage your account and platform settings</p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm">
          <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2 mb-4">
            <User className="w-4 h-4 text-slate-400" />
            Account Information
          </h3>
          <div className="space-y-3">
            <div className="flex items-center justify-between py-2 border-b border-slate-100">
              <span className="text-xs font-bold text-slate-500">Name</span>
              <span className="text-sm font-bold text-slate-900">{user?.name || '-'}</span>
            </div>
            <div className="flex items-center justify-between py-2 border-b border-slate-100">
              <span className="text-xs font-bold text-slate-500">Email</span>
              <span className="text-sm font-bold text-slate-900">{user?.email || '-'}</span>
            </div>
            <div className="flex items-center justify-between py-2 border-b border-slate-100">
              <span className="text-xs font-bold text-slate-500">Role</span>
              <span className="text-xs font-bold px-2 py-0.5 bg-indigo-600 text-white rounded uppercase tracking-wider">
                {getRoleLabel(user?.role || '')}
              </span>
            </div>
            <div className="flex items-center justify-between py-2 border-b border-slate-100">
              <span className="text-xs font-bold text-slate-500">User ID</span>
              <span className="text-xs font-mono text-slate-600">{user?.id || '-'}</span>
            </div>
            {user?.merchantId && (
              <div className="flex items-center justify-between py-2 border-b border-slate-100">
                <span className="text-xs font-bold text-slate-500">Merchant ID</span>
                <span className="text-xs font-mono text-slate-600">{user.merchantId}</span>
              </div>
            )}
          </div>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm">
          <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2 mb-4">
            <Shield className="w-4 h-4 text-slate-400" />
            Security
          </h3>
          <p className="text-xs text-slate-500">
            Your session is secured with JWT authentication. Tokens are automatically refreshed when needed.
          </p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm">
          <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2 mb-4">
            <Bell className="w-4 h-4 text-slate-400" />
            Notifications
          </h3>
          <p className="text-xs text-slate-500">
            Notification preferences will be configured here in a future update.
          </p>
        </div>
      </div>
    </DashboardLayout>
  );
};