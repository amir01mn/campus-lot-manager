import { useId, type ReactNode } from 'react';
import { cn } from '../../lib/cn';

/** Accessible on/off switch (role="switch") with a visible label and optional description. */
export function Switch({
  checked,
  onChange,
  label,
  description,
  disabled,
  tone = 'accent',
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: ReactNode;
  description?: ReactNode;
  disabled?: boolean;
  tone?: 'accent' | 'warning';
}) {
  const labelId = useId();
  const descriptionId = useId();
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="min-w-0">
        <p id={labelId} className="text-sm font-medium text-fg">
          {label}
        </p>
        {description && (
          <p id={descriptionId} className="mt-0.5 text-xs text-fg-muted">
            {description}
          </p>
        )}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-labelledby={labelId}
        aria-describedby={description ? descriptionId : undefined}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={cn(
          'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors disabled:cursor-not-allowed disabled:opacity-50',
          checked ? (tone === 'warning' ? 'bg-warning' : 'bg-accent') : 'bg-border-strong',
        )}
      >
        <span
          aria-hidden="true"
          className={cn('inline-block size-5 rounded-full bg-white shadow transition-transform', checked ? 'translate-x-[22px]' : 'translate-x-0.5')}
        />
      </button>
    </div>
  );
}
