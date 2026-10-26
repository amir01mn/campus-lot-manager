import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { managerApi, type ManagerBookingsQuery } from '../../api/endpoints';
import { queryKeys } from '../../api/queryKeys';
import type { CreateLotRequest, UpdateLotRequest, UpdateSpotRequest } from '../../api/types';

export const STATS_POLL_MS = 30_000;

export function useStats(range: { from: string; to: string }) {
  return useQuery({
    queryKey: queryKeys.manager.stats(range),
    queryFn: () => managerApi.stats(range),
    refetchInterval: STATS_POLL_MS,
    placeholderData: keepPreviousData,
  });
}

export function useManagerBookings(query: ManagerBookingsQuery) {
  return useQuery({
    queryKey: queryKeys.manager.bookings(query),
    queryFn: () => managerApi.bookings(query),
    placeholderData: keepPreviousData,
  });
}

function useInvalidateLots() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.lots.all }),
      queryClient.invalidateQueries({ queryKey: queryKeys.manager.all }),
    ]);
}

export function useCreateLot() {
  const invalidate = useInvalidateLots();
  return useMutation({ mutationFn: (body: CreateLotRequest) => managerApi.createLot(body), onSuccess: invalidate });
}

export function useUpdateLot() {
  const invalidate = useInvalidateLots();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateLotRequest }) => managerApi.updateLot(id, body),
    onSuccess: invalidate,
  });
}

export function useUpdateSpot() {
  const invalidate = useInvalidateLots();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateSpotRequest }) => managerApi.updateSpot(id, body),
    onSuccess: invalidate,
  });
}
