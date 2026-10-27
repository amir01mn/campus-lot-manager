import { useCallback, useSyncExternalStore } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { isServerUnavailable } from '../../api/client';
import { Alert } from '../ui/Feedback';

/**
 * Shown while any query is retrying because the server can't be reached: on free hosting the API sleeps when idle and
 * the first visit afterwards waits for it to start.
 */
export function ServerWakeNotice() {
  const queryCache = useQueryClient().getQueryCache();
  const subscribe = useCallback((onChange: () => void) => queryCache.subscribe(onChange), [queryCache]);
  const waking = useSyncExternalStore(subscribe, () =>
    queryCache
      .getAll()
      .some(
        (query) =>
          query.state.fetchStatus === 'fetching' &&
          query.state.fetchFailureCount > 0 &&
          isServerUnavailable(query.state.fetchFailureReason),
      ),
  );

  if (!waking) return null;
  return (
    <Alert tone="info" title="Waking up the server…" className="mb-6">
      This demo runs on free hosting, which sleeps when nobody is using it. The first visit can take up to a minute or
      two, and the page will load by itself.
    </Alert>
  );
}
