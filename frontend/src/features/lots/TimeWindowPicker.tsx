import { Clock } from 'lucide-react';
import { Field, Input, Select } from '../../components/ui/Field';
import { addDays, campusToday, formatClock, HALF_HOURS, type CampusWindow } from '../../lib/time';

/**
 * Date + start + end in campus time (America/Toronto), in half-hour steps. An end at or before the start is
 * read as the next day, so overnight bookings work without a second date field.
 */
export function TimeWindowPicker({
  value,
  onChange,
  error,
}: {
  value: CampusWindow;
  onChange: (next: CampusWindow) => void;
  error?: string | null;
}) {
  const today = campusToday();
  return (
    <fieldset>
      <legend className="sr-only">Booking time window (campus time)</legend>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)_minmax(0,1fr)]">
        <Field label="Date" className="col-span-2 sm:col-span-1">
          <Input
            type="date"
            min={today}
            max={addDays(today, 90)}
            value={value.date}
            onChange={(e) => {
              if (e.target.value) onChange({ ...value, date: e.target.value });
            }}
          />
        </Field>
        <Field label="Start">
          <Select value={value.startTime} onChange={(e) => onChange({ ...value, startTime: e.target.value })}>
            {HALF_HOURS.map((t) => (
              <option key={t} value={t}>
                {formatClock(t)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="End">
          <Select value={value.endTime} onChange={(e) => onChange({ ...value, endTime: e.target.value })}>
            {HALF_HOURS.map((t) => (
              <option key={t} value={t}>
                {formatClock(t)}
                {t <= value.startTime ? ' (next day)' : ''}
              </option>
            ))}
          </Select>
        </Field>
      </div>
      <p className="mt-2 flex items-center gap-1.5 text-xs text-fg-subtle">
        <Clock className="size-3.5" aria-hidden="true" />
        Campus time (Toronto). Bookings last 30 minutes to 24 hours and are billed per started hour.
      </p>
      {error && (
        <p role="alert" className="mt-2 text-sm font-medium text-danger">
          {error}
        </p>
      )}
    </fieldset>
  );
}
