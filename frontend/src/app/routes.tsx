import { lazy, Suspense, type ReactNode } from 'react';
import type { RouteObject } from 'react-router';
import { PageSkeleton } from '../components/layout/PageSkeleton';
import { AppShell } from '../components/layout/AppShell';
import { NotFound } from '../components/layout/NotFound';
import { AuthProvider } from '../features/auth/AuthProvider';
import { LoginPage } from '../features/auth/LoginPage';
import { RegisterPage } from '../features/auth/RegisterPage';
import { RequireRole } from '../features/auth/RequireRole';
import { MyBookingsPage } from '../features/bookings/MyBookingsPage';
import { ReceiptPage } from '../features/bookings/ReceiptPage';
import { LotDetailPage } from '../features/lots/LotDetailPage';
import { LotsPage } from '../features/lots/LotsPage';
import { NotificationsPage } from '../features/notifications/NotificationsPage';
import { ProfilePage } from '../features/profile/ProfilePage';

const DashboardPage = lazy(() => import('../features/manager/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const LotManagementPage = lazy(() => import('../features/manager/LotManagementPage').then((m) => ({ default: m.LotManagementPage })));
const ManagerBookingsPage = lazy(() => import('../features/manager/ManagerBookingsPage').then((m) => ({ default: m.ManagerBookingsPage })));
const AdminPage = lazy(() => import('../features/admin/AdminPage').then((m) => ({ default: m.AdminPage })));
/** Manager and admin screens (and the charting library) load on demand, keeping the driver bundle small. */
function Deferred({ children }: { children: ReactNode }) {
  return <Suspense fallback={<PageSkeleton />}>{children}</Suspense>;
}

export const routes: RouteObject[] = [
  {
    element: (
      <AuthProvider>
        <AppShell />
      </AuthProvider>
    ),
    children: [
      { index: true, element: <LotsPage /> },
      { path: 'lots/:lotId', element: <LotDetailPage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
      { path: 'bookings', element: <RequireRole role="USER"><MyBookingsPage /></RequireRole> },
      { path: 'bookings/:bookingId/receipt', element: <RequireRole role="USER"><ReceiptPage /></RequireRole> },
      { path: 'notifications', element: <RequireRole role="USER"><NotificationsPage /></RequireRole> },
      { path: 'profile', element: <RequireRole role="USER"><ProfilePage /></RequireRole> },
      { path: 'manager', element: <RequireRole role="MANAGER"><Deferred><DashboardPage /></Deferred></RequireRole> },
      { path: 'manager/bookings', element: <RequireRole role="MANAGER"><Deferred><ManagerBookingsPage /></Deferred></RequireRole> },
      { path: 'manager/lots', element: <RequireRole role="MANAGER"><Deferred><LotManagementPage /></Deferred></RequireRole> },
      { path: 'admin', element: <RequireRole role="SUPER_MANAGER"><Deferred><AdminPage /></Deferred></RequireRole> },
      { path: '*', element: <NotFound /> },
    ],
  },
];
