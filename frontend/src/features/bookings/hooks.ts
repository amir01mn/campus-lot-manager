import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { bookingsApi, type MyBookingsQuery } from '../../api/endpoints';
import { queryKeys } from '../../api/queryKeys';

export function useMyBookings(query: MyBookingsQuery) {
  return useQuery({
    queryKey: queryKeys.bookings.mine(query),
    queryFn: () => bookingsApi.mine(query),
    placeholderData: keepPreviousData,
  });
}

export function useReceipt(id: string) {
  return useQuery({ queryKey: queryKeys.bookings.receipt(id), queryFn: () => bookingsApi.receipt(id) });
}

/** After any booking change, bookings, lot availability, manager views and notifications are stale. */
function useInvalidateAfterBookingChange() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.bookings.all }),
      queryClient.invalidateQueries({ queryKey: queryKeys.lots.all }),
      queryClient.invalidateQueries({ queryKey: queryKeys.manager.all }),
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all }),
    ]);
}

export function useExtendBooking() {
  const invalidate = useInvalidateAfterBookingChange();
  return useMutation({
    mutationFn: ({ id, newEnd }: { id: string; newEnd: string }) => bookingsApi.extend(id, newEnd),
    onSuccess: invalidate,
  });
}

export function useCancelBooking() {
  const invalidate = useInvalidateAfterBookingChange();
  return useMutation({ mutationFn: bookingsApi.cancel, onSuccess: invalidate });
}

export function useCheckoutBooking() {
  const invalidate = useInvalidateAfterBookingChange();
  return useMutation({ mutationFn: bookingsApi.checkout, onSuccess: invalidate });
}
