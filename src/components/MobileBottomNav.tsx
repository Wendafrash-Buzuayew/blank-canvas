import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getNavigationForRole } from '../lib/navigation';

/**
 * Fixed bottom tab bar for the Phase 1 Merchant Mini App shell - replaces
 * the sidebar entirely (not just collapsed for mobile). See
 * docs/superpowers/specs/2026-08-27-mini-app-phase1-decoupling-design.md §4.2.
 */
export const MobileBottomNav: React.FC = () => {
  const { user } = useAuth();
  if (!user) return null;

  const navItems = getNavigationForRole(user.role);
  if (navItems.length === 0) return null;

  return (
    <nav
      aria-label="Primary"
      className="safe-b fixed inset-x-0 bottom-0 z-40 flex border-t border-slate-200 bg-white"
    >
      {navItems.map((item) => {
        const Icon = item.icon;
        return (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              `flex min-h-[48px] flex-1 flex-col items-center justify-center gap-1 py-2.5 text-[11px] font-semibold transition-colors ${
                isActive ? 'text-[#0DA64B]' : 'text-slate-500'
              }`
            }
          >
            <Icon className="w-5 h-5" />
            <span>{item.label}</span>
          </NavLink>
        );
      })}
    </nav>
  );
};
