import { createContext, useContext } from 'react';

export type ToastTone = 'success' | 'info' | 'warning' | 'danger';
export type ToastInput = { title: string; description?: string; tone?: ToastTone };

export const ToastContext = createContext<((toast: ToastInput) => void) | null>(null);

/** Shows a transient, screen-reader-announced message. */
export function useToast(): (toast: ToastInput) => void {
  const push = useContext(ToastContext);
  if (!push) throw new Error('useToast must be used inside <ToastProvider>');
  return push;
}
