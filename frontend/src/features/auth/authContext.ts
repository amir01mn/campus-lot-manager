import { createContext, useContext } from 'react';
import type { LoginRequest, UserProfile } from '../../api/types';

export type SessionEndReason = 'expired' | 'signed-out';

export type AuthContextValue = {
  user: UserProfile | null;
  /** Why the last session ended (expiry/401 or sign-out), until the next sign-in. Guards use it for the login message. */
  endReason: SessionEndReason | null;
  login: (credentials: LoginRequest) => Promise<UserProfile>;
  logout: () => void;
  /** Replaces the cached profile (e.g. after GET /users/me). */
  updateUser: (user: UserProfile) => void;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>');
  return value;
}
