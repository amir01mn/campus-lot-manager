import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/server';
import { manager, stats, student } from '../../test/fixtures';
import { renderApp } from '../../test/render';
import { KpiTiles } from './KpiTiles';
import { toInstantRange } from './dateRange';

function tile(label: string) {
  const heading = screen.getByText(label);
  const card = heading.closest('div.rounded-xl');
  if (!(card instanceof HTMLElement)) throw new Error(`tile ${label} not found`);
  return within(card);
}

describe('KpiTiles', () => {
  it('renders the headline numbers from a stats fixture', () => {
    render(<KpiTiles stats={stats} />);
    expect(tile('Occupancy now').getByText('40%')).toBeInTheDocument();
    expect(tile('Occupied spots').getByText(/52/)).toBeInTheDocument();
    expect(tile('Occupied spots').getByText('/ 130')).toBeInTheDocument();
    expect(tile('Bookings').getByText('87')).toBeInTheDocument();
    expect(tile('Bookings').getByText('58 completed · 21 confirmed · 8 cancelled')).toBeInTheDocument();
    expect(tile('Revenue').getByText('$1,234.50')).toBeInTheDocument();
  });

  it('treats a missing status as zero', () => {
    render(<KpiTiles stats={{ ...stats, bookingsByStatus: { COMPLETED: 3 } }} />);
    expect(screen.getByText('3 completed · 0 confirmed · 0 cancelled')).toBeInTheDocument();
  });
});

describe('dashboard page', () => {
  it('loads stats for the last 30 campus days and renders tiles and accessible chart tables', async () => {
    let query: URLSearchParams | null = null;
    server.use(
      http.get('*/api/manager/stats', ({ request }) => {
        query = new URL(request.url).searchParams;
        return HttpResponse.json(stats);
      }),
      http.get('*/api/notifications/unread-count', () => HttpResponse.json({ count: 3 })),
    );
    renderApp({ route: '/manager', user: manager });

    expect(await screen.findByText('$1,234.50')).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Revenue by user type' })).toHaveTextContent('Faculty$250.50');
    expect(screen.getByRole('table', { name: 'Bookings per day' })).toHaveTextContent('Oct 2657');
    expect(screen.getByRole('button', { name: '30 days' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'Notifications, 3 unread' })).toBeInTheDocument();
    const from = query!.get('from')!;
    const to = query!.get('to')!;
    expect((Date.parse(to) - Date.parse(from)) / 86_400_000).toBeCloseTo(30, 0);
  });

  it('is hidden from drivers', async () => {
    server.use(http.get('*/api/notifications/unread-count', () => HttpResponse.json({ count: 0 })));
    renderApp({ route: '/manager', user: student });
    expect(await screen.findByText('You don’t have access to this page')).toBeInTheDocument();
  });
});

describe('toInstantRange', () => {
  it('turns inclusive campus days into a half-open UTC range across a DST change', () => {
    // DST ends in Toronto on 2026-11-01
    expect(toInstantRange({ fromDate: '2026-10-31', toDate: '2026-11-01' })).toEqual({
      from: '2026-10-31T04:00:00.000Z',
      to: '2026-11-02T05:00:00.000Z',
    });
  });
});
