import type { Role, UserProfile } from '../../api/types';

/** A signed-in session. Kept in memory and mirrored to sessionStorage so a reload keeps you signed in. */
export type Session = { token: string; expiresAt: number; user: UserProfile };

const STORAGE_KEY = 'campuspark.session';

function isSession(value: unknown): value is Session {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return typeof v.token === 'string' && typeof v.expiresAt === 'number' && typeof v.user === 'object' && v.user !== null;
}

export function loadSession(now: number = Date.now()): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    if (!isSession(parsed) || parsed.expiresAt <= now) {
      sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function saveSession(session: Session): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  } catch {
    // storage unavailable: the session lives in memory only
  }
}

export function clearSession(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // nothing to clear
  }
}

const ROLE_RANK: Record<Role, number> = { USER: 0, MANAGER: 1, SUPER_MANAGER: 2 };

/** Roles are hierarchical, as on the backend: a super manager can do everything a manager can. */
export function hasRole(user: Pick<UserProfile, 'role'> | null | undefined, required: Role): boolean {
  return !!user && ROLE_RANK[user.role] >= ROLE_RANK[required];
}

/** Where to land after signing in when no `next` was requested. */
export function homeFor(user: Pick<UserProfile, 'role'>): string {
  return hasRole(user, 'MANAGER') ? '/manager' : '/';
}

/** Only same-app paths are accepted as a post-login redirect (no open redirects). */
export function safeNext(next: string | null): string | null {
  if (!next?.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) return null;
  return next;
}
