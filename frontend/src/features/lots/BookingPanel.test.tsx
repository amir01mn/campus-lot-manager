import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { CreateBookingRequest } from '../../api/types';
import { server } from '../../test/server';
import { availableSpotIds, booking, lotA, quote, spots, student } from '../../test/fixtures';
import { renderApp } from '../../test/render';

function lotHandlers() {
  return [
    http.get('*/api/lots/1', () => HttpResponse.json(lotA)),
    http.get('*/api/lots/1/spots', () => HttpResponse.json(spots)),
    http.get('*/api/lots/1/availability', ({ request }) => {
      const url = new URL(request.url);
      return HttpResponse.json({
        lotId: 1,
        start: url.searchParams.get('start'),
        end: url.searchParams.get('end'),
        availableCount: availableSpotIds.length,
        spots: spots.filter((s) => availableSpotIds.includes(s.id)),
      });
    }),
    http.get('*/api/pricing/quote', () => HttpResponse.json(quote)),
    http.get('*/api/notifications/unread-count', () => HttpResponse.json({ count: 0 })),
  ];
}

async function pickSpotAndPlate() {
  const user = userEvent.setup();
  await user.click(await screen.findByRole('radio', { name: 'Spot A-001, Free' }));
  await user.type(screen.getByLabelText('Licence plate'), 'abcd 123');
  return user;
}

describe('booking form', () => {
  it('shows the price quote for the signed-in user', async () => {
    server.use(...lotHandlers());
    renderApp({ route: '/lots/1', user: student });

    const breakdown = await screen.findByLabelText('Price quote');
    expect(within(breakdown).getByText('$5.00/h × 2 hours')).toBeInTheDocument();
    expect(within(breakdown).getByTestId('quote-total')).toHaveTextContent('$10.00');
    expect(within(breakdown).getByText('Deposit charged now').nextSibling).toHaveTextContent('$5.00');
    expect(screen.getByRole('button', { name: 'Confirm and pay $5.00 deposit' })).toBeDisabled();
  });

  it('books the selected spot with an upper-cased plate', async () => {
    let sent: CreateBookingRequest | null = null;
    server.use(
      ...lotHandlers(),
      http.post('*/api/bookings', async ({ request }) => {
        sent = (await request.json()) as CreateBookingRequest;
        return HttpResponse.json(booking({ spot: { id: 1, label: 'A-001', lotId: 1, lotName: lotA.name }, referenceCode: 'CP-NEW123' }), { status: 201 });
      }),
    );
    renderApp({ route: '/lots/1', user: student });
    const user = await pickSpotAndPlate();
    await user.click(await screen.findByRole('button', { name: 'Confirm and pay $5.00 deposit' }));

    expect(await screen.findByText('Booking confirmed')).toBeInTheDocument();
    expect(screen.getByText('CP-NEW123')).toBeInTheDocument();
    expect(sent).toMatchObject({ spotId: 1, plate: 'ABCD 123', paymentMethod: 'CREDIT_CARD' });
  });

  it('renders a friendly message when the spot was taken meanwhile (409)', async () => {
    server.use(
      ...lotHandlers(),
      http.post('*/api/bookings', () =>
        HttpResponse.json(
          {
            type: 'urn:campus-parking:problem:conflict',
            title: 'Conflict',
            status: 409,
            detail: 'Spot A-001 is already booked for part of that time.',
          },
          { status: 409 },
        ),
      ),
    );
    renderApp({ route: '/lots/1', user: student });
    const user = await pickSpotAndPlate();
    await user.click(await screen.findByRole('button', { name: 'Confirm and pay $5.00 deposit' }));

    const alert = await screen.findByText('That spot was just taken');
    expect(alert.parentElement).toHaveTextContent('Spot A-001 is already booked for part of that time. Pick another spot or change your time.');
    // the stale selection is cleared so the user picks again
    expect(screen.getByText('Select a free spot on the map.')).toBeInTheDocument();
  });

  it('maps a 422 business-rule code to plain language', async () => {
    server.use(
      ...lotHandlers(),
      http.post('*/api/bookings', () =>
        HttpResponse.json(
          { title: 'Business rule violated', status: 422, detail: 'The booking cannot start in the past.', code: 'START_IN_PAST' },
          { status: 422 },
        ),
      ),
    );
    renderApp({ route: '/lots/1', user: student });
    const user = await pickSpotAndPlate();
    await user.click(await screen.findByRole('button', { name: 'Confirm and pay $5.00 deposit' }));
    expect(await screen.findByText('That start time has already passed. Pick a time from now on.')).toBeInTheDocument();
  });

  it('asks anonymous visitors to sign in instead of showing a price', async () => {
    server.use(...lotHandlers());
    renderApp({ route: '/lots/1' });
    expect(await screen.findByRole('link', { name: 'Sign in to book' })).toHaveAttribute('href', '/login?next=%2Flots%2F1');
    expect(screen.queryByLabelText('Price quote')).not.toBeInTheDocument();
  });
});
