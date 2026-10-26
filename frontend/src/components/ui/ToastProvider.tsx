import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { AlertTriangle, CheckCircle2, Info, X, XCircle } from 'lucide-react';
import { cn } from '../../lib/cn';
import { ToastContext, type ToastInput, type ToastTone } from './toastContext';

type Toast = ToastInput & { id: number };

const icons: Record<ToastTone, ReactNode> = {
  success: <CheckCircle2 className="size-5 text-success" />,
  info: <Info className="size-5 text-accent" />,
  warning: <AlertTriangle className="size-5 text-warning" />,
  danger: <XCircle className="size-5 text-danger" />,
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((t) => t.id !== id));
    const timer = timers.current.get(id);
    if (timer) clearTimeout(timer);
    timers.current.delete(id);
  }, []);

  const push = useCallback(
    (toast: ToastInput) => {
      const id = nextId.current++;
      setToasts((current) => [...current.slice(-3), { ...toast, id }]);
      timers.current.set(
        id,
        setTimeout(() => {
          dismiss(id);
        }, toast.tone === 'warning' || toast.tone === 'danger' ? 9000 : 5000),
      );
    },
    [dismiss],
  );

  useEffect(() => {
    const pending = timers.current;
    return () => {
      pending.forEach((timer) => {
        clearTimeout(timer);
      });
    };
  }, []);

  return (
    <ToastContext.Provider value={push}>
      {children}
      <div
        aria-live="polite"
        className="no-print pointer-events-none fixed inset-x-0 bottom-0 z-50 flex flex-col items-center gap-2 p-4 sm:items-end"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role={toast.tone === 'danger' ? 'alert' : 'status'}
            className={cn(
              'pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl border border-border bg-surface p-4 shadow-pop',
            )}
          >
            <span aria-hidden="true">{icons[toast.tone ?? 'success']}</span>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-fg">{toast.title}</p>
              {toast.description && <p className="mt-0.5 text-sm text-fg-muted">{toast.description}</p>}
            </div>
            <button
              type="button"
              className="rounded p-0.5 text-fg-subtle hover:text-fg"
              aria-label="Dismiss notification"
              onClick={() => dismiss(toast.id)}
            >
              <X className="size-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
