import React, { createContext, useContext, useState, useCallback, useEffect, ReactNode } from 'react';
import { realtime } from '../lib/realtime';
import { authApi, setTokens, clearTokens, setUser, getUser, isAuthenticated, LoginResponse, UserInfoResponse } from '../lib/api';

export type AuthUser = {
  id: string;
  email: string;
  name: string;
  role: string;
  merchantId?: string;
  branchId?: number | null;
};

interface AuthContextType {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<AuthUser>;
  logout: () => Promise<void>;
  setUserProfile: (user: AuthUser) => void;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// Map backend UserInfoResponse to frontend AuthUser
function mapUserInfoToAuthUser(info: UserInfoResponse): AuthUser {
  return {
    id: info.id,
    email: info.email,
    name: info.name || info.email.split('@')[0],
    role: info.role,
    merchantId: info.merchantId ?? undefined,
    branchId: info.branchId ?? null,
  };
}

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUserState] = useState<AuthUser | null>(() => getUser());
  const [isLoading, setIsLoading] = useState(false);
  const [authState, setAuthState] = useState<boolean>(() => isAuthenticated());

  // On mount, if a token exists, refresh the user profile from /api/auth/me
  // This ensures permissions are correct after a page reload.
  useEffect(() => {
    if (isAuthenticated() && !user) {
      setIsLoading(true);
      authApi.getMe()
        .then((info) => {
          const authUser = mapUserInfoToAuthUser(info);
          setUser(authUser);
          setUserState(authUser);
          setAuthState(true);
        })
        .catch((err) => {
          console.error('[AuthContext] Failed to refresh user on mount:', err);
          clearTokens();
          setUserState(null);
          setAuthState(false);
        })
        .finally(() => setIsLoading(false));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Listen for storage events to sync auth state across tabs
  useEffect(() => {
    const handleStorageUpdate = () => {
      setUserState(getUser());
      setAuthState(isAuthenticated());
    };

    window.addEventListener('storage', handleStorageUpdate);
    window.addEventListener('qrserve_auth_update', handleStorageUpdate);

    return () => {
      window.removeEventListener('storage', handleStorageUpdate);
      window.removeEventListener('qrserve_auth_update', handleStorageUpdate);
    };
  }, []);

  const login = useCallback(async (email: string, password: string): Promise<AuthUser> => {
    setIsLoading(true);
    try {
      const response: LoginResponse = await authApi.login(email, password);
      setTokens(response.accessToken, response.refreshToken);

      // Fetch the real user profile from /api/auth/me
      let authUser: AuthUser;
      try {
        const info = await authApi.getMe();
        authUser = mapUserInfoToAuthUser(info);
      } catch (err) {
        // Fallback: decode JWT payload if /me fails
        const payload = JSON.parse(atob(response.accessToken.split('.')[1]));
        authUser = {
          id: payload.sub || payload.userId || '',
          email: payload.email || email,
          name: payload.name || payload.sub || email.split('@')[0],
          role: payload.role || payload.authorities?.[0]?.replace('ROLE_', '') || 'MERCHANT_OWNER',
          merchantId: payload.merchantId,
        };
      }

      setUser(authUser);
      setUserState(authUser);
      setAuthState(true);
      window.dispatchEvent(new CustomEvent('qrserve_auth_update'));
      return authUser;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // Ignore network errors on logout
    }
    clearTokens();
    realtime.disconnect();
    setUserState(null);
    setAuthState(false);
    window.dispatchEvent(new CustomEvent('qrserve_auth_update'));
  }, []);

  const setUserProfile = useCallback((profile: AuthUser) => {
    setUser(profile);
    setUserState(profile);
  }, []);

  const refreshUser = useCallback(async () => {
    if (!isAuthenticated()) return;
    try {
      const info = await authApi.getMe();
      const authUser = mapUserInfoToAuthUser(info);
      setUser(authUser);
      setUserState(authUser);
    } catch (err) {
      console.error('[AuthContext] Failed to refresh user:', err);
    }
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: authState,
        isLoading,
        login,
        logout,
        setUserProfile,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};