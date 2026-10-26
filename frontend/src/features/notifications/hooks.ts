import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationsApi } from '../../api/endpoints';
import { queryKeys } from '../../api/queryKeys';

export const UNREAD_POLL_MS = 30_000;

export function useUnreadCount(enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.notifications.unread,
    queryFn: notificationsApi.unreadCount,
    enabled,
    refetchInterval: UNREAD_POLL_MS,
    select: (data) => data.count,
  });
}

export function useNotifications(page: number, size: number, enabled = true) {
  return useQuery({
    queryKey: queryKeys.notifications.list(page, size),
    queryFn: () => notificationsApi.list({ page, size }),
    enabled,
    placeholderData: keepPreviousData,
  });
}

export function useMarkRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: notificationsApi.markRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all }),
  });
}

export function useMarkAllRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: notificationsApi.markAllRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all }),
  });
}
