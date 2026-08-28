import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import { QrCode, Loader2, AlertCircle, Lock, Mail, Eye, EyeOff } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../lib/api';
import { getRoleHome } from '../router/ProtectedRoute';
import { isPhase2Enabled, isRoleAllowedInPhase } from '../lib/phase';
import { getSuperAppToken } from '../lib/superApp';

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
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
        <div className="w-full max-w-md bg-white rounded-3xl shadow-xl border border-slate-200 p-8 text-center">
          <div className="w-12 h-12 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center mx-auto mb-4">
            <AlertCircle className="w-6 h-6" />
          </div>
          <h1 className="text-lg font-black text-slate-900">Merchant accounts only</h1>
          <p className="text-sm text-slate-500 mt-2">
            This app is available for merchant accounts during this phase. Please sign in with a merchant account.
          </p>
          <button
            type="button"
            onClick={() => setWasPhaseBlocked(false)}
            className="mt-5 text-sm font-bold text-[#E60028] hover:underline"
          >
            Back to sign in
          </button>
        </div>
      </div>
    );
  }

  if (superAppExchanging) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
        <div className="flex flex-col items-center gap-3 text-slate-500">
          <Loader2 className="w-6 h-6 animate-spin" />
          <p className="text-sm font-bold">Signing you in…</p>
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

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Brand */}
        <div className="text-center mb-8">
          <div className="w-14 h-14 rounded-2xl bg-[#E60028] text-white flex items-center justify-center mx-auto mb-3 shadow-lg">
            <QrCode className="w-7 h-7" />
          </div>
          <h1 className="text-2xl font-black text-slate-900">QRServe</h1>
          <p className="text-sm text-slate-500 mt-1">Smart QR Menu & Ordering Platform</p>
        </div>

        <div className="bg-white rounded-3xl shadow-xl border border-slate-200 overflow-hidden">
          {/* Header */}
          <div className="bg-[#1E1E1E] text-white p-6">
            <h2 className="text-lg font-black">Sign in to your account</h2>
            <p className="text-xs text-slate-400 mt-1">Access your role-based dashboard</p>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="p-6 space-y-4">
            {(error || superAppError) && (
              <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700 flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-red-500 shrink-0" />
                {error || superAppError}
              </div>
            )}

            <div>
              <label className="block text-xs font-bold text-gray-700 uppercase mb-1.5">Email Address</label>
              <div className="relative">
                <Mail className="absolute left-3 top-3 w-4 h-4 text-gray-400" />
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@restaurant.com"
                  className="w-full pl-10 pr-3 py-2.5 text-sm rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-bold text-gray-700 uppercase mb-1.5">Password</label>
              <div className="relative">
                <Lock className="absolute left-3 top-3 w-4 h-4 text-gray-400" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="w-full pl-10 pr-10 py-2.5 text-sm rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-2.5 text-gray-400 hover:text-gray-600"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={isLoading}
              className="w-full py-3 bg-[#E60028] hover:bg-[#CC0024] disabled:opacity-60 text-white text-sm font-black rounded-xl shadow-md flex items-center justify-center gap-2 transition-all"
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Signing in...
                </>
              ) : (
                'Sign In'
              )}
            </button>

            {isPhase2Enabled() && (
              <div className="pt-3 border-t border-gray-100">
                <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">Demo Accounts</p>
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
                      className="w-full text-left px-3 py-2 rounded-xl bg-gray-50 border border-gray-200 text-xs hover:border-[#E60028]/40 hover:bg-red-50/50 transition-colors"
                    >
                      <span className="font-bold text-gray-900">{acc.label}: </span>
                      <span className="text-gray-500 font-mono">{acc.email}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}
          </form>
        </div>

        <p className="text-center text-xs text-slate-400 mt-6">
          © 2026 QRServe. All rights reserved.
        </p>
      </div>
    </div>
  );
};