import React, { useState } from 'react';
import { Menu, QrCode, LogOut } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Sidebar } from './Sidebar.tsx';
import { MobileBottomNav } from './MobileBottomNav.tsx';
import { useAuth } from '../context/AuthContext';
import { getRoleLabel } from '../router/ProtectedRoute';
import { isPhase2Enabled } from '../lib/phase';

interface DashboardLayoutProps {
  children: React.ReactNode;
  title?: string;
}

/**
 * The two authenticated shells from DESIGN.md §5.4.
 *
 * Frame B (mini app): 430 frame, 56 header, 56 bottom nav, 16 gutter.
 * Frame C (console):  256 sidebar, 64 header, 16/24/32 gutter.
 *
 * The QRServe mark is Safaricom Green (--color-brand) in BOTH shells. It used
 * to be M-PESA green in the mini app, which §3.6 forbids: on this palette that
 * colour sits 0.06 OKLab from the brand greens, so it would read as the brand
 * itself rather than as a partner, and at 2.95 it cannot signal anything.
 *
 * White on the hero green measures 2.88 and would fail anywhere it labelled
 * something — but a brand mark is exempt under WCAG 1.4.3 and 1.4.11, the
 * glyph here is decorative (aria-hidden), and the product name sits beside it
 * as real text. This is the only sanctioned use of that pair; every label,
 * button and active state uses --color-brand-dark (§3.1 rule 1).
 */
export const DashboardLayout: React.FC<DashboardLayoutProps> = ({ children, title }) => {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  if (!isPhase2Enabled()) {
    // Frame B — the Merchant Mini App. No sidebar at all: a fixed bottom tab
    // bar instead, with content constrained to a phone frame.
    return (
      <div className="min-h-screen bg-canvas">
        <header className="sticky top-0 z-20 border-b border-line bg-surface">
          <div className="mx-auto flex h-14 max-w-[430px] items-center gap-2 px-4">
            <div className="flex h-8 w-8 items-center justify-center rounded-control bg-brand text-brand-fg">
              <QrCode className="h-4 w-4" aria-hidden="true" />
            </div>
            {title && <h1 className="text-title-s text-ink">{title}</h1>}
            <button
              onClick={handleLogout}
              aria-label="Sign out"
              className="ml-auto flex h-11 w-11 items-center justify-center rounded-control text-muted transition-colors hover:bg-surface-2 hover:text-ink"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
        </header>
        {/* pb-24 clears the 56 bottom nav plus its safe-area inset (§5.4). */}
        <main className="mx-auto max-w-[430px] px-4 pt-4 pb-24">{children}</main>
        <MobileBottomNav />
      </div>
    );
  }

  // Frame C — the full console.
  return (
    <div className="min-h-screen bg-canvas">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      {/* Content is offset by the sidebar's 256 from lg up. */}
      <div className="lg:ml-64">
        <header className="sticky top-0 z-20 border-b border-line bg-surface">
          <div className="flex h-16 items-center justify-between px-4 sm:px-6">
            <div className="flex items-center gap-3">
              <button
                onClick={() => setSidebarOpen(true)}
                aria-label="Open navigation"
                aria-expanded={sidebarOpen}
                className="flex h-11 w-11 items-center justify-center rounded-control text-muted transition-colors hover:bg-surface-2 hover:text-ink lg:hidden"
              >
                <Menu className="h-5 w-5" aria-hidden="true" />
              </button>

              {/* Brand shows only where the sidebar is hidden. */}
              <div className="flex items-center gap-2 lg:hidden">
                <div className="flex h-8 w-8 items-center justify-center rounded-control bg-brand text-brand-fg">
                  <QrCode className="h-4 w-4" aria-hidden="true" />
                </div>
                <span className="font-display-black text-title-s font-black tracking-tight text-ink">
                  QRServe
                </span>
              </div>

              {title && <h1 className="hidden text-title-m text-ink sm:block">{title}</h1>}
            </div>

            {user && (
              <div className="flex items-center gap-2">
                {/* Email is metadata and the header is a --color-surface ground,
                    where --color-muted is in budget at 4.83 (§3.2). Role is
                    identity, so it is a neutral chip, not a coloured badge. */}
                <span className="hidden max-w-[220px] truncate text-body-m text-muted sm:block">
                  {user.email}
                </span>
                <span className="identity-chip">{getRoleLabel(user.role)}</span>
              </div>
            )}
          </div>
        </header>

        <main className="p-4 sm:p-6 lg:p-8">{children}</main>
      </div>
    </div>
  );
};
