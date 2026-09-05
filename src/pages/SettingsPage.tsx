import React from 'react';
import { Settings as SettingsIcon, User, Shield, Bell } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Card } from '../components/ui/Card';
import { IdentityChip } from '../components/ui/Chip';
import { useAuth } from '../context/AuthContext';
import { getRoleLabel } from '../router/ProtectedRoute';

export const SettingsPage: React.FC = () => {
  const { user } = useAuth();

  return (
    <DashboardLayout title="Settings">
      {/* DESIGN.md 5.4: forms and settings are the 720-max-width case in frame C. */}
      <div className="mx-auto max-w-[45rem] space-y-6">
        <div>
          <h2 className="flex items-center gap-2 text-title-m text-ink">
            <SettingsIcon className="h-5 w-5 text-brand-press" aria-hidden="true" />
            Settings
          </h2>
          <p className="mt-1 text-body-m text-muted">Manage your account and platform settings</p>
        </div>

        <Card>
          <h3 className="mb-4 flex items-center gap-2 text-label-m text-ink">
            <User className="h-4 w-4 text-muted" aria-hidden="true" />
            Account Information
          </h3>
          <div className="space-y-3">
            <div className="flex items-center justify-between border-b border-line py-2">
              <span className="text-label-s text-muted">Name</span>
              <span className="text-label-m text-ink">{user?.name || '-'}</span>
            </div>
            <div className="flex items-center justify-between border-b border-line py-2">
              <span className="text-label-s text-muted">Email</span>
              <span className="text-label-m text-ink">{user?.email || '-'}</span>
            </div>
            <div className="flex items-center justify-between border-b border-line py-2">
              <span className="text-label-s text-muted">Role</span>
              <IdentityChip>{getRoleLabel(user?.role || '')}</IdentityChip>
            </div>
            <div className="flex items-center justify-between border-b border-line py-2">
              <span className="text-label-s text-muted">User ID</span>
              <span className="font-mono text-label-s text-muted">{user?.id || '-'}</span>
            </div>
            {user?.merchantId && (
              <div className="flex items-center justify-between border-b border-line py-2">
                <span className="text-label-s text-muted">Merchant ID</span>
                <span className="font-mono text-label-s text-muted">{user.merchantId}</span>
              </div>
            )}
          </div>
        </Card>

        <Card>
          <h3 className="mb-4 flex items-center gap-2 text-label-m text-ink">
            <Shield className="h-4 w-4 text-muted" aria-hidden="true" />
            Security
          </h3>
          <p className="text-body-m text-muted">
            Your session is secured with JWT authentication. Tokens are automatically refreshed when needed.
          </p>
        </Card>

        <Card>
          <h3 className="mb-4 flex items-center gap-2 text-label-m text-ink">
            <Bell className="h-4 w-4 text-muted" aria-hidden="true" />
            Notifications
          </h3>
          <p className="text-body-m text-muted">
            Notification preferences will be configured here in a future update.
          </p>
        </Card>
      </div>
    </DashboardLayout>
  );
};
