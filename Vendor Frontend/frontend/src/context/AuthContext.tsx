import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { authApi } from '../api/endpoints';
import { tokenStore } from '../api/client';
import type { RoleName, UserProfile } from '../types';

interface AuthState {
  user: UserProfile | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<UserProfile>;
  logout: () => void;
  hasRole: (role: RoleName) => boolean;
  hasAnyRole: (...roles: RoleName[]) => boolean;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  // A token in storage is only a claim; the profile call is what
  // confirms the session is still good.
  useEffect(() => {
    if (!tokenStore.get()) {
      setLoading(false);
      return;
    }
    authApi.me()
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const response = await authApi.login(username, password);
    tokenStore.set(response.accessToken);
    setUser(response.user);
    return response.user;
  }, []);

  const logout = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  const hasRole = useCallback(
    (role: RoleName) => Boolean(user?.roles.includes(role)),
    [user],
  );

  const hasAnyRole = useCallback(
    (...roles: RoleName[]) => roles.some((role) => Boolean(user?.roles.includes(role))),
    [user],
  );

  const value = useMemo(
    () => ({ user, loading, login, logout, hasRole, hasAnyRole }),
    [user, loading, login, logout, hasRole, hasAnyRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}

/** The landing route for a user, based on the roles they hold. */
export function defaultRouteFor(user: UserProfile | null): string {
  if (!user) return '/login';
  if (user.roles.includes('MAKER')) return '/maker';
  if (user.roles.includes('APPROVER')) return '/approver';
  if (user.roles.includes('ACCOUNTS_PAYABLE')) return '/accounts-payable';
  if (user.roles.includes('ADMINISTRATOR')) return '/administrator';
  return '/login';
}
