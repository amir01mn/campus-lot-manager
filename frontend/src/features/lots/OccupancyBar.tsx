import { cn } from '../../lib/cn';

/** Horizontal meter of occupied spots out of the total, with the percentage in text. */
export function OccupancyBar({ occupied, total, className }: { occupied: number; total: number; className?: string }) {
  const percent = total > 0 ? Math.round((occupied / total) * 100) : 0;
  const tone = percent >= 85 ? 'bg-danger' : percent >= 60 ? 'bg-warning' : 'bg-success';
  return (
    <div className={className}>
      <div className="mb-1.5 flex items-baseline justify-between text-xs">
        <span className="text-fg-muted">Occupancy</span>
        <span className="tabular font-medium text-fg">{percent}%</span>
      </div>
      <div
        role="meter"
        aria-label="Occupancy"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        aria-valuetext={`${occupied} of ${total} spots occupied (${percent}%)`}
        className="h-2 overflow-hidden rounded-full bg-surface-sunken"
      >
        <div className={cn('h-full rounded-full transition-[width] duration-500', tone)} style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}
