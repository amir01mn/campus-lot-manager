import type { ReactNode } from 'react';
import { AlertTriangle, CheckCircle2, Info, RefreshCw, XCircle } from 'lucide-react';
import { cn } from '../../lib/cn';
import { errorMessage } from '../../api/problem';
import { Button } from './Button';

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cn('animate-shimmer rounded-md bg-surface-sunken', className)} />;
}

/** Wraps skeleton placeholders so assistive tech hears a single "Loading" instead of nothing. */
export function LoadingRegion({ label = 'Loading', children }: { label?: string; children: ReactNode }) {
  return (
    <div role="status" aria-live="polite" aria-busy="true">
      <span className="sr-only">{label}…</span>
      {children}
    </div>
  );
}

type AlertTone = 'info' | 'success' | 'warning' | 'danger';

const alertStyles: Record<AlertTone, { box: string; icon: ReactNode }> = {
  info: { box: 'bg-info-soft text-info-soft-fg border-info-soft-fg/20', icon: <Info className="size-4" /> },
  success: {
    box: 'bg-success-soft text-success-soft-fg border-success/25',
    icon: <CheckCircle2 className="size-4" />,
  },
  warning: {
    box: 'bg-warning-soft text-warning-soft-fg border-warning/30',
    icon: <AlertTriangle className="size-4" />,
  },
  danger: { box: 'bg-danger-soft text-danger-soft-fg border-danger/25', icon: <XCircle className="size-4" /> },
};

export function Alert({
  tone = 'info',
  title,
  children,
  className,
  action,
}: {
  tone?: AlertTone;
  title?: ReactNode;
  children?: ReactNode;
  className?: string;
  action?: ReactNode;
}) {
  const style = alertStyles[tone];
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={cn('flex gap-3 rounded-lg border px-3.5 py-3 text-sm', style.box, className)}
    >
      <span className="mt-0.5 shrink-0" aria-hidden="true">
        {style.icon}
      </span>
      <div className="min-w-0 flex-1 space-y-0.5">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className="leading-relaxed">{children}</div>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
}: {
  icon?: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('flex flex-col items-center px-6 py-12 text-center', className)}>
      {icon && (
        <div className="mb-4 grid size-12 place-items-center rounded-full bg-accent-soft text-accent-soft-fg" aria-hidden="true">
          {icon}
        </div>
      )}
      <h3 className="text-base font-semibold text-fg">{title}</h3>
      {description && <p className="mt-1 max-w-sm text-sm text-fg-muted">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function ErrorState({
  error,
  onRetry,
  title = 'Couldn’t load this',
  className,
}: {
  error: unknown;
  onRetry?: () => void;
  title?: string;
  className?: string;
}) {
  return (
    <div role="alert" className={cn('flex flex-col items-center px-6 py-10 text-center', className)}>
      <div className="mb-3 grid size-11 place-items-center rounded-full bg-danger-soft text-danger-soft-fg" aria-hidden="true">
        <AlertTriangle className="size-5" />
      </div>
      <h3 className="text-base font-semibold text-fg">{title}</h3>
      <p className="mt-1 max-w-sm text-sm text-fg-muted">{errorMessage(error)}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" className="mt-4" icon={<RefreshCw className="size-4" />} onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}
