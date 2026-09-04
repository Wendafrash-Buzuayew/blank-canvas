import React, { useEffect, useRef } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { QrCode, LogOut, ChevronLeft } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { getNavigationForRole } from '../lib/navigation';
import { getRoleLabel } from '../router/ProtectedRoute';

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

/**
 * Console navigation — DESIGN.md §5.4 frame C and §6.8.
 *
 * 256 wide on an --color-ink ground. Text on that ground uses
 * --color-on-ink / --color-on-ink-muted, NOT --color-muted, which measures
 * 2.63 against ink and fails AA (see the token comment in index.css).
 *
 * Below `lg` this is an off-canvas drawer, which per §6.4 means it owes the
 * same obligations as a modal: Escape closes it, and focus returns to the
 * trigger. Both were missing.
 */
export const Sidebar: React.FC<SidebarProps> = ({ open, onClose }) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  // Escape closes the drawer. Bound only while it is open so the console's
  // other Escape handlers are not shadowed when the sidebar is permanent.
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  // Move focus into the drawer on open so a keyboard user is not left behind
  // on the trigger, with the rest of the page still reachable behind a scrim.
  useEffect(() => {
    if (open) closeButtonRef.current?.focus();
  }, [open]);

  if (!user) return null;

  const navItems = getNavigationForRole(user.role);
  const roleLabel = getRoleLabel(user.role);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <>
      {/* Scrim — z-30 per the §5.6 scale, below the drawer's z-40 */}
      {open && (
        <div
          className="fixed inset-0 z-30 bg-ink/40 lg:hidden"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      <aside
        className={`fixed top-0 left-0 z-40 h-full w-64 transform bg-ink text-on-ink transition-transform duration-200 lg:translate-x-0 ${
          open ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex h-full flex-col">
          {/* Brand */}
          <div className="flex items-center justify-between border-b border-ink-2 px-4 py-4">
            <div className="flex items-center gap-2.5">
              {/* Brand mark. White on the hero green measures 2.88, which
                  WCAG exempts for a logotype (1.4.3 / 1.4.11 both carve out
                  brand marks) — and the glyph is decorative, with the name
                  beside it in text. This is the one sanctioned place the pair
                  appears; anywhere it labels or signals, use brand-dark. */}
              <div className="flex h-9 w-9 items-center justify-center rounded-control bg-brand text-brand-fg">
                <QrCode className="h-5 w-5" aria-hidden="true" />
              </div>
              <div>
                {/* The wordmark is the one shell element where 900 is the
                    correct weight — Proxima Nova Black. */}
                <span className="font-display text-title-s font-black tracking-tight">
                  QRServe
                </span>
                <span className="-mt-0.5 block text-label-s text-on-ink-muted">
                  Smart QR Menu &amp; Ordering
                </span>
              </div>
            </div>
            <button
              ref={closeButtonRef}
              onClick={onClose}
              aria-label="Close navigation"
              className="flex h-11 w-11 items-center justify-center rounded-control text-on-ink-muted hover:bg-ink-2 hover:text-on-ink lg:hidden"
            >
              <ChevronLeft className="h-5 w-5" aria-hidden="true" />
            </button>
          </div>

          {/* Signed-in user. Role is identity, not status, so it is a neutral
              identity-chip — never a coloured badge (§6.2, decision 2). */}
          <div className="border-b border-ink-2 px-4 py-3">
            <div className="flex items-center gap-2">
              <div className="flex h-8 w-8 items-center justify-center rounded-pill bg-ink-2 text-label-s uppercase">
                {user.name?.charAt(0) || user.email?.charAt(0) || 'U'}
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-label-m text-on-ink">{user.name || user.email}</p>
                {/* The identity-chip utility carries the light-ground palette;
                    on the ink sidebar the same shape takes the on-ink pair. */}
                <span className="mt-0.5 inline-flex items-center rounded-pill border border-ink-2 bg-ink-2 px-2 py-0.5 text-label-s text-on-ink-muted">
                  {roleLabel}
                </span>
              </div>
            </div>
          </div>

          {/* Navigation. NavLink sets aria-current="page" on the active item.
              Active state shifts weight as well as colour, so colour is not
              its only carrier (§7.3). */}
          <nav aria-label="Primary" className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
            {navItems.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  onClick={onClose}
                  className={({ isActive }) =>
                    // Active fill is brand-dark, not the hero green: white on
                    // the hero green measures 2.88 and fails. brand-dark gives
                    // 4.87 for the label and still separates from the ink
                    // ground at 3.27.
                    `flex min-h-11 items-center gap-3 rounded-control px-3 text-label-m transition-colors ${
                      isActive
                        ? 'bg-brand-dark font-semibold text-brand-fg'
                        : 'font-medium text-on-ink-muted hover:bg-ink-2 hover:text-on-ink'
                    }`
                  }
                >
                  <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                  <span className="truncate">{item.label}</span>
                </NavLink>
              );
            })}
          </nav>

          {/* Sign out. Not destructive — it discards no data — so it is a ghost
              control, not danger (§6.1). */}
          <div className="border-t border-ink-2 px-3 py-3">
            <button
              onClick={handleLogout}
              className="flex min-h-11 w-full items-center gap-3 rounded-control px-3 text-label-m text-on-ink-muted transition-colors hover:bg-ink-2 hover:text-on-ink"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
              Sign Out
            </button>
          </div>
        </div>
      </aside>
    </>
  );
};
