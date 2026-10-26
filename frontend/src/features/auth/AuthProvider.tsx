import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { setAuthToken, setUnauthorizedHandler } from '../../api/client';
import { authApi } from '../../api/endpoints';
import type { LoginRequest, UserProfile } from '../../api/types';
import { AuthContext, type AuthContextValue, type SessionEndReason } from './authContext';
import { clearSession, loadSession, saveSession, type Session } from './session';

function initialSession(): Session | null {
  const session = loadSession();
  setAuthToken(session?.token ?? null);
  return session;
}

/**
 * Owns the JWT. The token lives in memory (and sessionStorage, so a reload survives); it is dropped on logout,
 * on any 401 from an authenticated request, and when it expires. Expiry sends the user to the login page with
 * a message and a `next` link back to where they were.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(initialSession);
  const [endReason, setEndReason] = useState<SessionEndReason | null>(null);
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const locationRef = useRef(location);
  useEffect(() => {
    locationRef.current = location;
  }, [location]);

  const endSession = useCallback(() => {
    setAuthToken(null);
    clearSession();
    setSession(null);
    queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'lots' });
  }, [queryClient]);

  const expire = useCallback(() => {
    setEndReason('expired');
    endSession();
    const { pathname, search } = locationRef.current;
    if (pathname === '/login') return;
    const next = encodeURIComponent(pathname + search);
    void navigate(`/login?reason=expired&next=${next}`, { replace: true });
  }, [endSession, navigate]);

  useEffect(() => {
    setUnauthorizedHandler(expire);
    return () => {
      setUnauthorizedHandler(null);
    };
  }, [expire]);

  useEffect(() => {
    if (!session) return;
    const timer = setTimeout(expire, Math.max(session.expiresAt - Date.now(), 0));
    return () => {
      clearTimeout(timer);
    };
  }, [session, expire]);

  const login = useCallback(async (credentials: LoginRequest) => {
    const response = await authApi.login(credentials);
    const next: Session = {
      token: response.accessToken,
      // renew a few seconds early so a request never goes out with a token about to lapse
      expiresAt: Date.now() + response.expiresIn * 1000 - 5000,
      user: response.user,
    };
    setAuthToken(next.token);
    saveSession(next);
    setSession(next);
    setEndReason(null);
    return response.user;
  }, []);

  const logout = useCallback(() => {
    setEndReason('signed-out');
    endSession();
    void navigate('/login?reason=signed-out', { replace: true });
  }, [endSession, navigate]);

  const updateUser = useCallback((user: UserProfile) => {
    setSession((current) => {
      if (!current) return current;
      const next = { ...current, user };
      saveSession(next);
      return next;
    });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ user: session?.user ?? null, endReason, login, logout, updateUser }),
    [session, endReason, login, logout, updateUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
