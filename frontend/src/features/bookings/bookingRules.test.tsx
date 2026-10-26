import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { booking, student } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { bookingPhase, cancellationMessage, cancellationOutcome, extensionOptions } from './bookingRules';
import { CancelBookingDialog } from './CancelBookingDialog';

const start = '2026-10-27T13:00:00Z'; // 9:00 a.m. campus time
const b = booking({ startTime: start, endTime: '2026-10-27T16:00:00Z', depositAmount: 5 });
const at = (iso: string) => new Date(iso);

describe('cancellation refund rule', () => {
  it('refunds the deposit when cancelling at least 1 hour before the start', () => {
    const outcome = cancellationOutcome(b, at('2026-10-27T10:00:00Z'));
    expect(outcome).toMatchObject({ refundable: true, refundAmount: 5, started: false });
    expect(cancellationMessage(outcome, 5)).toBe(
      'You’re cancelling at least 1 hour before the start, so your $5.00 deposit will be refunded in full.',
    );
  });

  it('treats exactly 1 hour before the start as still refundable (same as the backend)', () => {
    expect(cancellationOutcome(b, at('2026-10-27T12:00:00Z')).refundable).toBe(true);
    expect(cancellationOutcome(b, at('2026-10-27T12:00:01Z')).refundable).toBe(false);
  });

  it('keeps the deposit within the last hour and names the cut-off in campus time', () => {
    const outcome = cancellationOutcome(b, at('2026-10-27T12:30:00Z'));
    expect(outcome).toMatchObject({ refundable: false, refundAmount: 0, started: false });
    expect(cancellationMessage(outcome, 5)).toMatch(/less than 1 hour before the start \(the free-cancellation cut-off was Tue, Oct 27, 8:00 a\.m\.\), so the \$5\.00 deposit is kept/);
  });

  it('explains that a started booking keeps its deposit', () => {
    const outcome = cancellationOutcome(b, at('2026-10-27T14:00:00Z'));
    expect(outcome.started).toBe(true);
    expect(cancellationMessage(outcome, 5)).toBe('This booking has already started, so the $5.00 deposit is kept. Nothing else will be charged.');
  });

  it('shows the explanation in the confirmation dialog before cancelling', () => {
    const farAhead = booking({ startTime: '2099-01-01T15:00:00Z', endTime: '2099-01-01T17:00:00Z' });
    renderWithProviders(<CancelBookingDialog booking={farAhead} onClose={() => undefined} />, { user: student });
    expect(screen.getByText('Full deposit refund')).toBeInTheDocument();
    expect(screen.getByTestId('refund-message')).toHaveTextContent('your $5.00 deposit will be refunded in full');
    expect(screen.getByRole('button', { name: 'Cancel booking' })).toBeInTheDocument();
  });

  it('warns in the dialog when the deposit will be kept', () => {
    const soon = booking({ startTime: new Date(Date.now() + 20 * 60_000).toISOString(), endTime: new Date(Date.now() + 2 * 3_600_000).toISOString() });
    renderWithProviders(<CancelBookingDialog booking={soon} onClose={() => undefined} />, { user: student });
    expect(screen.getByText('No refund')).toBeInTheDocument();
    expect(screen.getByTestId('refund-message')).toHaveTextContent('deposit is kept');
  });
});

describe('bookingPhase', () => {
  it('sorts bookings into the My bookings tabs', () => {
    expect(bookingPhase(b, at('2026-10-27T12:00:00Z'))).toBe('upcoming');
    expect(bookingPhase(b, at('2026-10-27T13:30:00Z'))).toBe('active');
    expect(bookingPhase({ ...b, status: 'COMPLETED' }, at('2026-10-27T12:00:00Z'))).toBe('past');
    expect(bookingPhase({ ...b, status: 'CANCELLED' }, at('2026-10-27T12:00:00Z'))).toBe('cancelled');
  });
});

describe('extensionOptions', () => {
  it('offers half-hour steps that keep the booking within 24 hours', () => {
    const options = extensionOptions({ startTime: '2026-10-27T13:00:00Z', endTime: '2026-10-28T11:00:00Z' });
    expect(options).toEqual(['2026-10-28T11:30:00.000Z', '2026-10-28T12:00:00.000Z', '2026-10-28T12:30:00.000Z', '2026-10-28T13:00:00.000Z']);
  });
});
