import { AlertTriangle, CalendarCheck, CalendarPlus, CalendarX, CheckCheck, ShieldAlert, Wrench } from 'lucide-react';
import type { ReactNode } from 'react';
import type { Notification, NotificationType } from '../../api/types';
import { cn } from '../../lib/cn';
import { NOTIFICATION_LABEL } from '../../lib/format';
import { formatRelative } from '../../lib/time';

const ICONS: Record<NotificationType, { icon: ReactNode; className: string }> = {
  BOOKING_CONFIRMED: { icon: <CalendarCheck className="size-4" />, className: 'bg-success-soft text-success-soft-fg' },
  BOOKING_EXTENDED: { icon: <CalendarPlus className="size-4" />, className: 'bg-accent-soft text-accent-soft-fg' },
  BOOKING_CANCELLED: { icon: <CalendarX className="size-4" />, className: 'bg-surface-sunken text-fg-muted' },
  BOOKING_COMPLETED: { icon: <CheckCheck className="size-4" />, className: 'bg-info-soft text-info-soft-fg' },
  OVERSTAY_DETECTED: { icon: <AlertTriangle className="size-4" />, className: 'bg-warning-soft text-warning-soft-fg' },
  UNAUTHORIZED_PARKING: { icon: <ShieldAlert className="size-4" />, className: 'bg-danger-soft text-danger-soft-fg' },
  MAINTENANCE_FLAGGED: { icon: <Wrench className="size-4" />, className: 'bg-warning-soft text-warning-soft-fg' },
};

export function NotificationItem({
  notification,
  onMarkRead,
  compact = false,
}: {
  notification: Notification;
  onMarkRead?: (id: string) => void;
  compact?: boolean;
}) {
  const style = ICONS[notification.type];
  return (
    <div className={cn('flex gap-3', compact ? 'px-4 py-3' : 'px-5 py-4', !notification.read && 'bg-accent-soft/40')}>
      <span className={cn('grid size-8 shrink-0 place-items-center rounded-full', style.className)} aria-hidden="true">
        {style.icon}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-baseline justify-between gap-x-3">
          <p className="text-sm font-medium text-fg">
            {NOTIFICATION_LABEL[notification.type]}
            {!notification.read && <span className="sr-only"> (unread)</span>}
          </p>
          <time dateTime={notification.createdAt} className="text-xs text-fg-subtle">
            {formatRelative(notification.createdAt)}
          </time>
        </div>
        <p className="mt-0.5 text-sm break-words text-fg-muted">{notification.message}</p>
        {!notification.read && onMarkRead && (
          <button
            type="button"
            className="mt-1.5 text-xs font-medium text-accent hover:underline"
            onClick={() => onMarkRead(notification.id)}
          >
            Mark as read
          </button>
        )}
      </div>
      {!notification.read && <span className="mt-1.5 size-2 shrink-0 rounded-full bg-accent" aria-hidden="true" />}
    </div>
  );
}
