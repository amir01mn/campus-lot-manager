import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { bookingsApi, lotsApi, pricingApi, type TimeWindow } from '../../api/endpoints';
import { queryKeys } from '../../api/queryKeys';

/** The spot grid refreshes this often so sensor changes show up without a reload. */
export const SPOTS_POLL_MS = 15_000;
export const LOTS_POLL_MS = 30_000;

export function useLots() {
  return useQuery({ queryKey: queryKeys.lots.list(), queryFn: lotsApi.list, refetchInterval: LOTS_POLL_MS });
}

export function useLot(id: number) {
  return useQuery({ queryKey: queryKeys.lots.detail(id), queryFn: () => lotsApi.get(id), refetchInterval: LOTS_POLL_MS });
}

export function useSpots(id: number, poll = true) {
  return useQuery({
    queryKey: queryKeys.lots.spots(id),
    queryFn: () => lotsApi.spots(id),
    refetchInterval: poll ? SPOTS_POLL_MS : false,
  });
}

export function useAvailability(id: number, window: TimeWindow | null) {
  return useQuery({
    queryKey: queryKeys.lots.availability(id, window ?? { start: '', end: '' }),
    queryFn: () => lotsApi.availability(id, window ?? { start: '', end: '' }),
    enabled: window !== null,
    refetchInterval: SPOTS_POLL_MS,
    placeholderData: keepPreviousData,
  });
}

export function useQuote(window: TimeWindow | null, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.quote(window ?? { start: '', end: '' }),
    queryFn: () => pricingApi.quote(window ?? { start: '', end: '' }),
    enabled: enabled && window !== null,
    placeholderData: keepPreviousData,
    staleTime: 5 * 60_000,
  });
}

export function useCreateBooking() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: bookingsApi.create,
    onSettled: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.lots.all }),
        queryClient.invalidateQueries({ queryKey: queryKeys.bookings.all }),
        queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all }),
      ]);
    },
  });
}
