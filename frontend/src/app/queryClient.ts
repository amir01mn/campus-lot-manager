import { QueryClient } from '@tanstack/react-query';
import { ApiError, isServerUnavailable } from '../api/client';

/** About two minutes of retries: long enough for a free-tier host to wake the API from sleep. */
export const SERVER_WAKE_RETRIES = 14;

/**
 * Client errors (4xx) won't succeed on retry. While the server is unreachable (asleep or restarting) keep retrying for
 * a couple of minutes; other errors get two more attempts.
 */
export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (isServerUnavailable(error)) return failureCount < SERVER_WAKE_RETRIES;
  if (error instanceof ApiError && error.status < 500) return false;
  return failureCount < 2;
}

/** Exponential backoff capped at 10 s, so a server that just woke up is noticed quickly. */
export function queryRetryDelay(attempt: number): number {
  return Math.min(1000 * 2 ** attempt, 10_000);
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 10_000,
        refetchOnWindowFocus: true,
        retry: shouldRetryQuery,
        retryDelay: queryRetryDelay,
      },
      mutations: { retry: false },
    },
  });
}
