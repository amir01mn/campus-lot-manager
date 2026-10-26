import type { Booking } from '../../api/types';
import { formatMoney } from '../../lib/format';
import { formatDateTime, HOUR_MS, MINUTE_MS } from '../../lib/time';

/** Free-cancellation notice, mirroring the backend: cancel at least this long before the start for a refund. */
export const FREE_CANCELLATION_NOTICE_MS = HOUR_MS;

export type BookingPhase = 'upcoming' | 'active' | 'past' | 'cancelled';

/** Which My-bookings tab a booking belongs to. A confirmed booking that has ended waits for auto-checkout in "active". */
export function bookingPhase(booking: Pick<Booking, 'status' | 'startTime'>, now: Date = new Date()): BookingPhase {
  if (booking.status === 'CANCELLED') return 'cancelled';
  if (booking.status === 'COMPLETED') return 'past';
  return Date.parse(booking.startTime) > now.getTime() ? 'upcoming' : 'active';
}

export type CancellationOutcome = {
  /** True when the deposit will be refunded. */
  refundable: boolean;
  /** Deposit refunded (0 when not refundable). */
  refundAmount: number;
  /** Last moment (ISO) a cancellation still gets the refund. */
  refundDeadline: string;
  /** True once the booking has started. */
  started: boolean;
};

/** What cancelling now would do. The deposit is refunded when cancelling at least 1 hour before the start. */
export function cancellationOutcome(
  booking: Pick<Booking, 'startTime' | 'depositAmount'>,
  now: Date = new Date(),
): CancellationOutcome {
  const start = Date.parse(booking.startTime);
  const deadline = start - FREE_CANCELLATION_NOTICE_MS;
  const refundable = now.getTime() <= deadline;
  return {
    refundable,
    refundAmount: refundable ? booking.depositAmount : 0,
    refundDeadline: new Date(deadline).toISOString(),
    started: now.getTime() >= start,
  };
}

/** One-sentence explanation shown before the user confirms a cancellation. */
export function cancellationMessage(outcome: CancellationOutcome, deposit: number): string {
  if (outcome.refundable) {
    return `You’re cancelling at least 1 hour before the start, so your ${formatMoney(outcome.refundAmount)} deposit will be refunded in full.`;
  }
  if (outcome.started) {
    return `This booking has already started, so the ${formatMoney(deposit)} deposit is kept. Nothing else will be charged.`;
  }
  return `It’s less than 1 hour before the start (the free-cancellation cut-off was ${formatDateTime(outcome.refundDeadline)}), so the ${formatMoney(deposit)} deposit is kept. Nothing else will be charged.`;
}

const MAX_DURATION_MS = 24 * HOUR_MS;

/** New end options: half-hour steps after the current end, keeping the whole booking within 24 hours. */
export function extensionOptions(booking: Pick<Booking, 'startTime' | 'endTime'>): string[] {
  const start = Date.parse(booking.startTime);
  const end = Date.parse(booking.endTime);
  const options: string[] = [];
  for (let t = end + 30 * MINUTE_MS; t - start <= MAX_DURATION_MS && options.length < 16; t += 30 * MINUTE_MS) {
    options.push(new Date(t).toISOString());
  }
  return options;
}
