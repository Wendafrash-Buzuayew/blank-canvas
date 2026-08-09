import React, { useState } from 'react';
import { Menu, QrCode } from 'lucide-react';
import { Sidebar } from './Sidebar.tsx';
import { useAuth } from '../context/AuthContext';
import { getRoleLabel } from '../router/ProtectedRoute';

interface DashboardLayoutProps {
  children: React.ReactNode;
  title?: string;
}

export const DashboardLayout: React.FC<DashboardLayoutProps> = ({ children, title }) => {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const { user } = useAuth();

  return (
    <div className="min-h-screen bg-slate-50">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      {/* Main content area - offset for sidebar on desktop */}
      <div className="lg:ml-64">
        {/* Top bar */}
        <header className="sticky top-0 z-20 bg-white border-b border-slate-200 shadow-xs">
          <div className="flex items-center justify-between h-16 px-4 sm:px-6">
            <div className="flex items-center gap-3">
              {/* Mobile menu button */}
              <button
                onClick={() => setSidebarOpen(true)}
                className="lg:hidden p-2 text-slate-500 hover:text-slate-900 hover:bg-slate-100 rounded-lg"
              >
                <Menu className="w-5 h-5" />
              </button>

              {/* Mobile brand */}
              <div className="lg:hidden flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-[#E60028] flex items-center justify-center text-white">
                  <QrCode className="w-4 h-4" />
                </div>
                <span className="font-bold text-sm">QRServe</span>
              </div>

              {/* Page title */}
              {title && (
                <h1 className="hidden sm:block text-lg font-bold text-slate-900">{title}</h1>
              )}
            </div>

            <div className="flex items-center gap-2">
              {user && (
                <div className="flex items-center gap-2 px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg">
                  <span className="text-xs font-bold text-slate-700">{user.email}</span>
                  <span className="text-[10px] font-bold px-1.5 py-0.5 bg-indigo-600 text-white rounded uppercase tracking-wider">
                    {getRoleLabel(user.role)}
                  </span>
                </div>
              )}
            </div>
          </div>
        </header>

        {/* Page content */}
        <main className="p-4 sm:p-6 lg:p-8">
          {children}
        </main>
      </div>
    </div>
  );
};