import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router';
import { Bell, BellOff } from 'lucide-react';
import { cn } from '../../lib/cn';
import { Skeleton } from '../../components/ui/Feedback';
import { useMarkAllRead, useMarkRead, useNotifications, useUnreadCount } from './hooks';
import { NotificationItem } from './NotificationItem';

/** Navbar bell: unread badge (polled) and a dropdown with the latest notifications. */
export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  const containerRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const unread = useUnreadCount(true);
  const list = useNotifications(0, 6, open);
  const markRead = useMarkRead();
  const markAll = useMarkAllRead();
  const count = unread.data ?? 0;

  useEffect(() => {
    if (!open) return;
    function onPointer(event: PointerEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) setOpen(false);
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false);
        buttonRef.current?.focus();
      }
    }
    document.addEventListener('pointerdown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div ref={containerRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        aria-label={count > 0 ? `Notifications, ${count} unread` : 'Notifications'}
        onClick={() => setOpen((o) => !o)}
        className="relative grid size-9 place-items-center rounded-lg text-fg-muted transition-colors hover:bg-surface-muted hover:text-fg"
      >
        <Bell className="size-5" aria-hidden="true" />
        {count > 0 && (
          <span
            aria-hidden="true"
            className="tabular absolute -top-0.5 -right-0.5 grid h-[18px] min-w-[18px] place-items-center rounded-full bg-danger px-1 text-[10px] font-semibold text-white ring-2 ring-surface"
          >
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>
      {open && (
        <div
          id={panelId}
          role="region"
          aria-label="Latest notifications"
          className="fixed inset-x-3 top-16 z-40 overflow-hidden rounded-xl border border-border bg-surface shadow-pop sm:absolute sm:inset-x-auto sm:top-auto sm:right-0 sm:mt-2 sm:w-96"
        >
          <div className="flex items-center justify-between border-b border-border px-4 py-3">
            <p className="text-sm font-semibold">Notifications</p>
            <button
              type="button"
              className="text-xs font-medium text-accent hover:underline disabled:pointer-events-none disabled:opacity-50"
              disabled={count === 0 || markAll.isPending}
              onClick={() => markAll.mutate()}
            >
              Mark all read
            </button>
          </div>
          <div className="max-h-[60vh] divide-y divide-border overflow-y-auto">
            {list.isPending ? (
              <div className="space-y-3 p-4" aria-busy="true">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="flex gap-3">
                    <Skeleton className="size-8 rounded-full" />
                    <div className="flex-1 space-y-2">
                      <Skeleton className="h-3 w-1/3" />
                      <Skeleton className="h-3 w-full" />
                    </div>
                  </div>
                ))}
              </div>
            ) : list.data?.content.length ? (
              list.data.content.map((n) => (
                <NotificationItem key={n.id} notification={n} compact onMarkRead={(id) => markRead.mutate(id)} />
              ))
            ) : (
              <div className="flex flex-col items-center gap-2 px-4 py-10 text-center text-sm text-fg-muted">
                <BellOff className="size-5" aria-hidden="true" />
                You’re all caught up.
              </div>
            )}
          </div>
          <Link
            to="/notifications"
            onClick={() => setOpen(false)}
            className={cn('block border-t border-border px-4 py-2.5 text-center text-sm font-medium text-accent hover:bg-surface-muted')}
          >
            View all notifications
          </Link>
        </div>
      )}
    </div>
  );
}
