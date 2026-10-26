import type { ManagerBookingsQuery, MyBookingsQuery, TimeWindow } from './endpoints';

/** Central TanStack Query keys, so invalidation after a mutation can target whole resources. */
export const queryKeys = {
  me: ['me'] as const,
  lots: {
    all: ['lots'] as const,
    list: () => ['lots', 'list'] as const,
    detail: (id: number) => ['lots', 'detail', id] as const,
    spots: (id: number) => ['lots', 'spots', id] as const,
    availability: (id: number, window: TimeWindow) => ['lots', 'availability', id, window] as const,
  },
  quote: (window: TimeWindow) => ['quote', window] as const,
  bookings: {
    all: ['bookings'] as const,
    mine: (query: MyBookingsQuery) => ['bookings', 'mine', query] as const,
    receipt: (id: string) => ['bookings', 'receipt', id] as const,
  },
  notifications: {
    all: ['notifications'] as const,
    list: (page: number, size: number) => ['notifications', 'list', page, size] as const,
    unread: ['notifications', 'unread'] as const,
  },
  manager: {
    all: ['manager'] as const,
    stats: (range: { from: string; to: string }) => ['manager', 'stats', range] as const,
    bookings: (query: ManagerBookingsQuery) => ['manager', 'bookings', query] as const,
  },
  admin: {
    managers: (page: number) => ['admin', 'managers', page] as const,
  },
};
