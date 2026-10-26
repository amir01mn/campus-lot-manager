import { cn } from '../../lib/cn';

export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={cn('size-8', className)} aria-hidden="true">
      <rect width="32" height="32" rx="8" className="fill-accent" />
      <path
        d="M12 24V8h6a5 5 0 0 1 0 10h-6"
        fill="none"
        className="stroke-accent-fg"
        strokeWidth="3.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function Logo({ inverted = false }: { inverted?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      {inverted ? (
        <svg viewBox="0 0 32 32" className="size-8" aria-hidden="true">
          <rect width="32" height="32" rx="8" fill="white" fillOpacity="0.95" />
          <path d="M12 24V8h6a5 5 0 0 1 0 10h-6" fill="none" stroke="#4f46e5" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      ) : (
        <LogoMark />
      )}
      <span className={cn('text-[1.05rem] font-semibold tracking-tight', inverted ? 'text-white' : 'text-fg')}>
        Campus<span className={inverted ? 'text-white/75' : 'text-accent'}>Park</span>
      </span>
    </span>
  );
}
