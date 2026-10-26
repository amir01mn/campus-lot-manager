import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { ShieldAlert } from 'lucide-react';
import type { Role } from '../../api/types';
import { ButtonLink } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/Feedback';
import { useAuth } from './authContext';
import { hasRole } from './session';

/** Route guard: anonymous visitors go to the login page, signed-in users without the role see a 403 state. */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user, endReason } = useAuth();
  const location = useLocation();
  if (!user) {
    // after signing out, start fresh; otherwise come back here after signing in
    if (endReason === 'signed-out') return <Navigate to="/login?reason=signed-out" replace />;
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?${endReason === 'expired' ? 'reason=expired&' : ''}next=${next}`} replace />;
  }
  if (!hasRole(user, role)) {
    return (
      <EmptyState
        icon={<ShieldAlert className="size-6" />}
        title="You don’t have access to this page"
        description="This area is for lot managers. If you think you should have access, ask a super manager."
        action={<ButtonLink to="/">Back to lots</ButtonLink>}
      />
    );
  }
  return <>{children}</>;
}
