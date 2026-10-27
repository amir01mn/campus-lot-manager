import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { ApiError, NetworkError, request } from '../api/client';
import { errorMessage } from '../api/problem';
import { ServerWakeNotice } from '../components/layout/ServerWakeNotice';
import { server } from '../test/server';
import { queryRetryDelay, SERVER_WAKE_RETRIES, shouldRetryQuery } from './queryClient';

describe('query retry policy', () => {
  it('keeps retrying while the server is unreachable, for about two minutes', () => {
    const gateway = new ApiError(502, { status: 502 });
    expect(shouldRetryQuery(5, gateway)).toBe(true);
    expect(shouldRetryQuery(5, new NetworkError(new TypeError('fetch failed')))).toBe(true);
    expect(shouldRetryQuery(SERVER_WAKE_RETRIES, gateway)).toBe(false);

    let total = 0;
    for (let attempt = 0; attempt < SERVER_WAKE_RETRIES; attempt++) total += queryRetryDelay(attempt);
    expect(total).toBeGreaterThanOrEqual(110_000);
    expect(total).toBeLessThanOrEqual(150_000);
  });

  it('does not retry client errors and gives other server errors two more attempts', () => {
    expect(shouldRetryQuery(0, new ApiError(404, { status: 404 }))).toBe(false);
    expect(shouldRetryQuery(1, new ApiError(500, { status: 500 }))).toBe(true);
    expect(shouldRetryQuery(2, new ApiError(500, { status: 500 }))).toBe(false);
  });

  it('explains gateway errors as the server starting up', () => {
    expect(errorMessage(new ApiError(503, { status: 503 }))).toMatch(/starting up/i);
  });
});

function Lots() {
  const { data } = useQuery({ queryKey: ['lots'], queryFn: () => request<{ id: number }[]>('/lots') });
  return <p>{data ? `${data.length} lots` : 'loading'}</p>;
}

describe('ServerWakeNotice', () => {
  it('appears while a request is retrying against a sleeping server and goes away once it answers', async () => {
    let calls = 0;
    server.use(
      http.get('*/api/lots', () => {
        calls += 1;
        return calls < 3 ? new HttpResponse('Bad Gateway', { status: 502 }) : HttpResponse.json([{ id: 1 }, { id: 2 }]);
      }),
    );
    const client = new QueryClient({ defaultOptions: { queries: { retry: shouldRetryQuery, retryDelay: 10 } } });
    render(
      <QueryClientProvider client={client}>
        <ServerWakeNotice />
        <Lots />
      </QueryClientProvider>,
    );

    expect(await screen.findByText(/waking up the server/i)).toBeInTheDocument();
    expect(await screen.findByText('2 lots')).toBeInTheDocument();
    expect(screen.queryByText(/waking up the server/i)).not.toBeInTheDocument();
  });
});
