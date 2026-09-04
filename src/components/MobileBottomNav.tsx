import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getNavigationForRole } from '../lib/navigation';

/**
 * Fixed bottom tab bar for the Phase 1 Merchant Mini App shell — replaces
 * the sidebar entirely (not just collapsed for mobile). See
 * docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §4.2
 * and DESIGN.md §5.4 frame B / §6.8.
 *
 * Items are 56 tall and full-height tappable, above the §5.7 minimum of 44 for
 * a thumb in motion.
 *
 * Active state is --color-brand-dark, which measures 4.87 on the surface
 * ground. The hero green cannot be used here: at 2.88 it fails as text, and
 * on the Safaricom palette M-PESA green would read as the brand itself
 * (0.06 OKLab from it) rather than as a state (§3.6).
 */
export const MobileBottomNav: React.FC = () => {
  const { user } = useAuth();
  if (!user) return null;

  const navItems = getNavigationForRole(user.role);
  if (navItems.length === 0) return null;

  return (
    <nav
      aria-label="Primary"
      className="safe-b fixed inset-x-0 bottom-0 z-40 flex border-t border-line bg-surface"
    >
      {navItems.map((item) => {
        const Icon = item.icon;
        return (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              // Weight shifts with the active state so colour is not its only
              // carrier (§7.3). NavLink supplies aria-current="page".
              `flex min-h-14 flex-1 flex-col items-center justify-center gap-1 py-2.5 text-label-s transition-colors ${
                isActive ? 'font-semibold text-brand-dark' : 'font-medium text-muted'
              }`
            }
          >
            <Icon className="h-5 w-5" aria-hidden="true" />
            <span>{item.label}</span>
          </NavLink>
        );
      })}
    </nav>
  );
};
