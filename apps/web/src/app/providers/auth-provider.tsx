import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useCurrentUserQuery } from '@/features/auth/hooks/use-auth-queries';
import type { CurrentUserResponse } from '@/features/auth/types/auth.types';
import { setAuthenticationFailureHandler } from '@/shared/api/api';
import { createCsrfTokenManager } from '@/shared/api/csrf';

interface AuthContextValue {
  user: CurrentUserResponse | null;
  isLoading: boolean;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const queryClient = useQueryClient();
  const [csrfInitialized, setCsrfInitialized] = useState(false);

  useEffect(() => {
    // Register global auth failure handler
    setAuthenticationFailureHandler(() => {
      queryClient.clear(); // Clear all queries on auth failure
    });
    
    // Initialize CSRF
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
    const csrfManager = createCsrfTokenManager({ baseUrl: apiBaseUrl });
    csrfManager.getToken().then(() => setCsrfInitialized(true)).catch(() => {
      // Even if CSRF fails initially, we allow the app to render, API client will retry
      setCsrfInitialized(true);
    });
  }, [queryClient]);

  const { data, isLoading: isQueryLoading, isFetching } = useCurrentUserQuery(csrfInitialized);

  const value = useMemo<AuthContextValue>(() => {
    return {
      user: data?.data ?? null,
      isLoading: !csrfInitialized || isQueryLoading || isFetching,
      isAuthenticated: !!data?.data,
    };
  }, [data, csrfInitialized, isQueryLoading, isFetching]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
