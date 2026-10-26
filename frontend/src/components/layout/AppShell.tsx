import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import {
  BarChart3,
  Bell,
  CalendarDays,
  ChevronDown,
  LayoutGrid,
  LogOut,
  Menu,
  ParkingSquare,
  ShieldCheck,
  SquareParking,
  TableProperties,
  UserRound,
  X,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { Role } from '../../api/types';
import { useAuth } from '../../features/auth/authContext';
import { hasRole } from '../../features/auth/session';
import { NotificationBell } from '../../features/notifications/NotificationBell';
import { cn } from '../../lib/cn';
import { ROLE_LABEL, USER_TYPE_LABEL } from '../../lib/format';
import { ButtonLink } from '../ui/Button';
import { Logo } from './Logo';
import { ThemeToggle } from './ThemeToggle';

type NavItem = { to: string; label: string; icon: LucideIcon; role?: Role; end?: boolean };

const NAV: NavItem[] = [
  { to: '/', label: 'Find parking', icon: SquareParking, end: true },
  { to: '/bookings', label: 'My bookings', icon: CalendarDays, role: 'USER' },
  { to: '/manager', label: 'Dashboard', icon: BarChart3, role: 'MANAGER', end: true },
  { to: '/manager/bookings', label: 'All bookings', icon: TableProperties, role: 'MANAGER' },
  { to: '/manager/lots', label: 'Lots', icon: LayoutGrid, role: 'MANAGER' },
  { to: '/admin', label: 'Managers', icon: ShieldCheck, role: 'SUPER_MANAGER' },
];

function navClass({ isActive }: { isActive: boolean }) {
  return cn(
    'flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
    isActive ? 'bg-accent-soft text-accent-soft-fg' : 'text-fg-muted hover:bg-surface-muted hover:text-fg',
  );
}

function UserMenu() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function close(event: PointerEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false);
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false);
    }
    document.addEventListener('pointerdown', close);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', close);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  if (!user) return null;
  const initials = `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase();
  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        aria-expanded={open}
        aria-haspopup="menu"
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-2 rounded-lg py-1 pr-1.5 pl-1 text-sm hover:bg-surface-muted"
      >
        <span className="grid size-7 place-items-center rounded-full bg-accent text-xs font-semibold text-accent-fg" aria-hidden="true">
          {initials}
        </span>
        <span className="hidden max-w-32 truncate font-medium md:inline">{user.firstName}</span>
        <span className="sr-only">Account menu for {user.firstName} {user.lastName}</span>
        <ChevronDown className="size-4 text-fg-subtle" aria-hidden="true" />
      </button>
      {open && (
        <div role="menu" className="absolute right-0 z-40 mt-2 w-60 overflow-hidden rounded-xl border border-border bg-surface shadow-pop">
          <div className="border-b border-border px-4 py-3">
            <p className="truncate text-sm font-semibold">
              {user.firstName} {user.lastName}
            </p>
            <p className="truncate text-xs text-fg-muted">{user.email}</p>
            <p className="mt-1 text-xs text-fg-subtle">
              {USER_TYPE_LABEL[user.userType]} · {ROLE_LABEL[user.role]}
            </p>
          </div>
          <Link role="menuitem" to="/profile" onClick={() => setOpen(false)} className="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-surface-muted">
            <UserRound className="size-4 text-fg-subtle" aria-hidden="true" /> Profile
          </Link>
          <Link role="menuitem" to="/notifications" onClick={() => setOpen(false)} className="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-surface-muted">
            <Bell className="size-4 text-fg-subtle" aria-hidden="true" /> Notifications
          </Link>
          <button
            role="menuitem"
            type="button"
            onClick={logout}
            className="flex w-full items-center gap-2 border-t border-border px-4 py-2.5 text-left text-sm text-danger hover:bg-surface-muted"
          >
            <LogOut className="size-4" aria-hidden="true" /> Sign out
          </button>
        </div>
      )}
    </div>
  );
}

export function AppShell() {
  const { user } = useAuth();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const items = NAV.filter((item) => !item.role || hasRole(user, item.role));
  const bare = location.pathname === '/login' || location.pathname === '/register';

  return (
    <div className="flex min-h-dvh flex-col">
      <a
        href="#main"
        className="sr-only z-50 rounded-md bg-accent px-3 py-2 text-accent-fg focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Skip to content
      </a>
      <header className="no-print sticky top-0 z-30 border-b border-border bg-surface/85 backdrop-blur supports-[backdrop-filter]:bg-surface/75">
        <div className="mx-auto flex h-16 max-w-7xl items-center gap-3 px-4 sm:px-6">
          <Link to="/" className="mr-2 shrink-0 rounded-lg" aria-label="CampusPark home">
            <Logo />
          </Link>
          {!bare && (
            <nav aria-label="Main" className="hidden flex-1 items-center gap-1 lg:flex">
              {items.map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end} className={navClass}>
                  <item.icon className="size-4" aria-hidden="true" />
                  {item.label}
                </NavLink>
              ))}
            </nav>
          )}
          <div className="ml-auto flex items-center gap-1.5">
            <ThemeToggle />
            {user ? (
              <>
                <NotificationBell />
                <UserMenu />
              </>
            ) : (
              !bare && (
                <>
                  <ButtonLink to="/login" variant="ghost" size="sm">
                    Sign in
                  </ButtonLink>
                  <ButtonLink to="/register" size="sm" className="hidden sm:inline-flex">
                    Create account
                  </ButtonLink>
                </>
              )
            )}
            {!bare && (
              <button
                type="button"
                className="grid size-9 place-items-center rounded-lg text-fg-muted hover:bg-surface-muted lg:hidden"
                aria-expanded={mobileOpen}
                aria-controls="mobile-nav"
                aria-label={mobileOpen ? 'Close menu' : 'Open menu'}
                onClick={() => setMobileOpen((o) => !o)}
              >
                {mobileOpen ? <X className="size-5" /> : <Menu className="size-5" />}
              </button>
            )}
          </div>
        </div>
        {mobileOpen && !bare && (
          <nav id="mobile-nav" aria-label="Main" className="border-t border-border px-4 py-3 lg:hidden">
            <div className="grid gap-1">
              {items.map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end} className={navClass} onClick={() => setMobileOpen(false)}>
                  <item.icon className="size-4" aria-hidden="true" />
                  {item.label}
                </NavLink>
              ))}
            </div>
          </nav>
        )}
      </header>
      <main id="main" className={cn('flex-1', !bare && 'mx-auto w-full max-w-7xl px-4 py-8 sm:px-6')}>
        <Outlet />
      </main>
      {!bare && (
        <footer className="no-print border-t border-border">
          <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-2 px-4 py-5 text-xs text-fg-subtle sm:px-6">
            <span className="inline-flex items-center gap-1.5">
              <ParkingSquare className="size-3.5" aria-hidden="true" /> CampusPark · times shown in campus time (Toronto) · prices in CAD
            </span>
            <a href="/swagger-ui/index.html" className="hover:text-fg">
              API docs
            </a>
          </div>
        </footer>
      )}
    </div>
  );
}
