import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import { QrCode, Loader2, AlertCircle, Lock, Mail, Eye, EyeOff } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../lib/api';
import { getRoleHome } from '../router/ProtectedRoute';
import { isPhase2Enabled, isRoleAllowedInPhase } from '../lib/phase';
import { getSuperAppToken } from '../lib/superApp';
import { Button } from '../components/ui/Button';

export const LoginPage: React.FC = () => {
  const { login, loginWithSuperAppToken, isLoading, isAuthenticated, user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState(isPhase2Enabled() ? 'admin@hotel.com' : '');
  const [password, setPassword] = useState(isPhase2Enabled() ? 'password' : '');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [wasPhaseBlocked, setWasPhaseBlocked] = useState(false);

  // Read once per mount: a Super App launch hands this off via the URL, and
  // it must not be re-read after the exchange consumes it (e.g. on an
  // in-page state change), or a failed exchange would retry forever.
  const superAppToken = useMemo(() => getSuperAppToken(), []);
  const [superAppExchanging, setSuperAppExchanging] = useState(Boolean(superAppToken));
  const [superAppError, setSuperAppError] = useState<string | null>(null);

  useEffect(() => {
    if (!superAppToken) return;
    // Strip the token from the visible URL/history immediately - it must not
    // persist in browser history, a Referer header, or access logs for any
    // longer than it takes to read it once here.
    window.history.replaceState({}, '', window.location.pathname);
    loginWithSuperAppToken(superAppToken)
      .catch((err) => {
        setSuperAppError(
          err instanceof ApiError ? err.message : 'Could not sign in from the Super App. Please try again.',
        );
      })
      .finally(() => setSuperAppExchanging(false));
    // Runs once per mount against the token captured above - loginWithSuperAppToken
    // is stable (useCallback with an empty dependency array in AuthContext).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [superAppToken]);

  const restoredPhaseBlock = Boolean(
    (location.state as { phaseBlocked?: boolean } | null)?.phaseBlocked,
  );
  const justLoggedInBlocked =
    isAuthenticated && Boolean(user) && !isRoleAllowedInPhase(user!.role, isPhase2Enabled());
  const phaseBlocked = restoredPhaseBlock || justLoggedInBlocked || wasPhaseBlocked;

  // A role that isn't allowed in the current phase still authenticates
  // successfully (the backend has no notion of phase), so it must be logged
  // out here rather than shown a dashboard it has no navigation for.
  // wasPhaseBlocked latches so the message survives the logout() completing
  // and isAuthenticated flipping back to false mid-render.
  useEffect(() => {
    if (justLoggedInBlocked) {
      setWasPhaseBlocked(true);
      logout();
    }
  }, [justLoggedInBlocked, logout]);

  // ProtectedRoute redirects here with `state: { phaseBlocked: true }` for a
  // restored session it just logged out. Browsers persist history.state
  // across a same-entry reload (F5), so if we left it in place, this message
  // would keep re-appearing on this tab forever - even for an unrelated,
  // legitimate login attempt later. Latch it into wasPhaseBlocked (so the
  // message keeps showing for the rest of this render pass) and immediately
  // replace the history entry with a clean state so a reload - or a fresh
  // login attempt - doesn't re-trigger it.
  useEffect(() => {
    if (restoredPhaseBlock) {
      setWasPhaseBlocked(true);
      navigate(location.pathname, { replace: true, state: null });
    }
  }, [restoredPhaseBlock, navigate, location.pathname]);

  if (phaseBlocked) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas p-4">
        <div className="w-full max-w-md rounded-card border border-line bg-surface p-8 text-center shadow-[var(--shadow-lift)]">
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-pill bg-warn-soft">
            <AlertCircle className="h-6 w-6 text-warn" aria-hidden="true" />
          </div>
          <h1 className="text-title-s text-ink">Account not available yet</h1>
          <p className="mt-2 text-body-m text-muted">
            This app is available for merchant and admin accounts during this phase. Please sign in with one of those.
          </p>
          <Button variant="link" onClick={() => setWasPhaseBlocked(false)} className="mt-5">
            Back to sign in
          </Button>
        </div>
      </div>
    );
  }

  if (superAppExchanging) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas p-4">
        <div role="status" aria-live="polite" className="flex flex-col items-center gap-3 text-muted">
          <Loader2 className="h-6 w-6 animate-spin text-brand-dark" aria-hidden="true" />
          <p className="text-label-m">Signing you in…</p>
        </div>
      </div>
    );
  }

  // If already authenticated with an allowed role, redirect to role home
  if (isAuthenticated && user) {
    return <Navigate to={getRoleHome(user.role)} replace />;
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await login(email, password);
      // After login, user state updates and the redirect above handles navigation
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError('Unable to connect to the server. Please try again.');
      }
    }
  };

  const demoAccounts = [
    { label: 'Super Admin', email: 'admin@hotel.com', password: 'password', role: 'SUPER_ADMIN' },
  ];

  const inputClasses =
    'h-11 w-full rounded-control border border-line bg-surface pl-10 pr-3 text-body-m text-ink ' +
    'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-dark';

  return (
    <div className="flex min-h-screen items-center justify-center bg-canvas p-4">
      <div className="w-full max-w-md">
        {/* Brand */}
        <div className="mb-8 text-center">
          <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-card bg-brand text-ink shadow-[var(--shadow-lift-brand)]">
            <QrCode className="h-7 w-7" aria-hidden="true" />
          </div>
          <h1 className="font-display text-title-l text-ink">QRServe</h1>
          <p className="mt-1 text-body-m text-muted">Smart QR Menu &amp; Ordering Platform</p>
        </div>

        <div className="overflow-hidden rounded-card border border-line bg-surface shadow-[var(--shadow-lift)]">
          {/* Header */}
          <div className="bg-ink p-6 text-on-ink">
            <h2 className="text-title-s">Sign in to your account</h2>
            <p className="mt-1 text-label-s text-on-ink-muted">Access your role-based dashboard</p>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-4 p-6">
            {(error || superAppError) && (
              <div role="alert" className="flex items-center gap-2 rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink">
                <AlertCircle className="h-4 w-4 shrink-0 text-danger" aria-hidden="true" />
                {error || superAppError}
              </div>
            )}

            <div>
              <label htmlFor="login-email" className="mb-1.5 block text-label-s uppercase text-muted">
                Email Address
              </label>
              <div className="relative">
                <Mail className="pointer-events-none absolute left-3 top-3.5 h-4 w-4 text-muted" aria-hidden="true" />
                <input
                  id="login-email"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@restaurant.com"
                  className={inputClasses}
                />
              </div>
            </div>

            <div>
              <label htmlFor="login-password" className="mb-1.5 block text-label-s uppercase text-muted">
                Password
              </label>
              <div className="relative">
                <Lock className="pointer-events-none absolute left-3 top-3.5 h-4 w-4 text-muted" aria-hidden="true" />
                <input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className={`${inputClasses} pr-11`}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  className="absolute right-1 top-1 flex h-9 w-9 items-center justify-center rounded-control text-muted hover:text-ink"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
                </button>
              </div>
            </div>

            <Button type="submit" loading={isLoading} fullWidth size="lg">
              {isLoading ? 'Signing in...' : 'Sign In'}
            </Button>

            {isPhase2Enabled() && (
              <div className="border-t border-line pt-3">
                <p className="mb-2 text-label-s uppercase text-muted">Demo Accounts</p>
                <div className="space-y-1.5">
                  {demoAccounts.map(acc => (
                    <button
                      key={acc.email}
                      type="button"
                      onClick={() => {
                        setEmail(acc.email);
                        setPassword(acc.password);
                        setError(null);
                      }}
                      className="w-full rounded-control border border-line bg-surface-2 px-3 py-2 text-left text-label-s transition-colors hover:border-brand-dark/40 hover:bg-brand-soft"
                    >
                      <span className="text-ink">{acc.label}: </span>
                      <span className="text-muted">{acc.email}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}
          </form>
        </div>

        <p className="mt-6 text-center text-label-s text-muted">
          © 2026 QRServe. All rights reserved.
        </p>
      </div>
    </div>
  );
};
