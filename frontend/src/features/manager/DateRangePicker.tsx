import { CalendarRange } from 'lucide-react';
import { cn } from '../../lib/cn';
import { Input } from '../../components/ui/Field';
import { campusToday } from '../../lib/time';
import { PRESETS, presetRange, type DayRange, type PresetKey } from './dateRange';

/** Preset ranges (segmented control) plus custom from/to dates, in campus days. */
export function DateRangePicker({
  value,
  preset,
  onChange,
}: {
  value: DayRange;
  preset: PresetKey | 'custom';
  onChange: (range: DayRange, preset: PresetKey | 'custom') => void;
}) {
  const today = campusToday();
  return (
    <div className="flex flex-wrap items-center gap-2">
      <div role="group" aria-label="Quick ranges" className="flex rounded-lg border border-border bg-surface-muted p-0.5">
        {PRESETS.map((p) => (
          <button
            key={p.key}
            type="button"
            aria-pressed={preset === p.key}
            onClick={() => onChange(presetRange(p.days), p.key)}
            className={cn(
              'rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
              preset === p.key ? 'bg-surface text-fg shadow-card' : 'text-fg-muted hover:text-fg',
            )}
          >
            {p.label}
          </button>
        ))}
      </div>
      <div className="flex items-center gap-1.5">
        <CalendarRange className="size-4 text-fg-subtle" aria-hidden="true" />
        <label className="sr-only" htmlFor="range-from">
          From
        </label>
        <Input
          id="range-from"
          type="date"
          className="h-9 w-[9.5rem]"
          max={value.toDate}
          value={value.fromDate}
          onChange={(e) => {
            if (e.target.value) onChange({ ...value, fromDate: e.target.value }, 'custom');
          }}
        />
        <span className="text-sm text-fg-subtle" aria-hidden="true">
          –
        </span>
        <label className="sr-only" htmlFor="range-to">
          To
        </label>
        <Input
          id="range-to"
          type="date"
          className="h-9 w-[9.5rem]"
          min={value.fromDate}
          max={today}
          value={value.toDate}
          onChange={(e) => {
            if (e.target.value) onChange({ ...value, toDate: e.target.value }, 'custom');
          }}
        />
      </div>
    </div>
  );
}
