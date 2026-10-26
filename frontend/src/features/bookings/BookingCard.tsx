import type { ReactNode } from 'react';
import { CalendarClock, Car, MapPin } from 'lucide-react';
import type { Booking } from '../../api/types';
import { Badge, type Tone } from '../../components/ui/Badge';
import { cn } from '../../lib/cn';
import { formatMoney } from '../../lib/format';
import { CAMPUS_TZ, formatDuration, formatRange, LOCALE } from '../../lib/time';
import type { BookingPhase } from './bookingRules';

const PHASE_BADGE: Record<BookingPhase, { tone: Tone; label: string }> = {
  upcoming: { tone: 'accent', label: 'Upcoming' },
  active: { tone: 'success', label: 'Active now' },
  past: { tone: 'neutral', label: 'Completed' },
  cancelled: { tone: 'danger', label: 'Cancelled' },
};

const monthFmt = new Intl.DateTimeFormat(LOCALE, { timeZone: CAMPUS_TZ, month: 'short' });
const dayFmt = new Intl.DateTimeFormat(LOCALE, { timeZone: CAMPUS_TZ, day: 'numeric' });

export function BookingCard({ booking, phase, actions }: { booking: Booking; phase: BookingPhase; actions?: ReactNode }) {
  const start = new Date(booking.startTime);
  const badge = PHASE_BADGE[phase];
  return (
    <article
      aria-label={`Booking ${booking.referenceCode}`}
      className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-4 shadow-card sm:flex-row sm:items-center sm:p-5"
    >
      <div
        className={cn(
          'grid w-14 shrink-0 place-items-center rounded-lg py-2 text-center',
          phase === 'cancelled' ? 'bg-surface-sunken text-fg-subtle' : 'bg-accent-soft text-accent-soft-fg',
        )}
        aria-hidden="true"
      >
        <span className="text-[11px] font-semibold tracking-wide uppercase">{monthFmt.format(start)}</span>
        <span className="text-xl leading-none font-semibold">{dayFmt.format(start)}</span>
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="font-semibold text-fg">
            Spot {booking.spot.label}
          </h3>
          <Badge tone={badge.tone}>{badge.label}</Badge>
          <span className="font-mono text-xs text-fg-subtle">{booking.referenceCode}</span>
        </div>
        <ul className="mt-1.5 flex flex-wrap gap-x-4 gap-y-1 text-sm text-fg-muted">
          <li className="flex items-center gap-1.5">
            <CalendarClock className="size-3.5" aria-hidden="true" />
            {formatRange(booking.startTime, booking.endTime)}
            <span className="text-fg-subtle">({formatDuration(Date.parse(booking.endTime) - Date.parse(booking.startTime))})</span>
          </li>
          <li className="flex items-center gap-1.5">
            <MapPin className="size-3.5" aria-hidden="true" />
            {booking.spot.lotName}
          </li>
          <li className="flex items-center gap-1.5">
            <Car className="size-3.5" aria-hidden="true" />
            <span className="font-mono">{booking.licencePlate}</span>
          </li>
        </ul>
      </div>
      <div className="flex flex-col gap-3 sm:items-end">
        <p className="tabular text-lg font-semibold sm:text-right">
          {formatMoney(booking.totalAmount)}
          <span className="block text-xs font-normal text-fg-subtle">deposit {formatMoney(booking.depositAmount)}</span>
        </p>
        {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
      </div>
    </article>
  );
}
