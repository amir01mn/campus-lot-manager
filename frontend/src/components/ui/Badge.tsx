import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

export type Tone = 'neutral' | 'accent' | 'success' | 'warning' | 'danger' | 'info';

const tones: Record<Tone, string> = {
  neutral: 'bg-surface-sunken text-fg-muted ring-border',
  accent: 'bg-accent-soft text-accent-soft-fg ring-accent/20',
  success: 'bg-success-soft text-success-soft-fg ring-success/25',
  warning: 'bg-warning-soft text-warning-soft-fg ring-warning/30',
  danger: 'bg-danger-soft text-danger-soft-fg ring-danger/25',
  info: 'bg-info-soft text-info-soft-fg ring-info-soft-fg/20',
};

export function Badge({
  tone = 'neutral',
  icon,
  children,
  className,
}: {
  tone?: Tone;
  icon?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap ring-1 ring-inset',
        tones[tone],
        className,
      )}
    >
      {icon}
      {children}
    </span>
  );
}
