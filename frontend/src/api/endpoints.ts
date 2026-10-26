/** Typed functions for every backend endpoint the UI uses, grouped by resource. */
import { request } from './client';
import type {
  Availability,
  Booking,
  BookingStatus,
  ChangePasswordRequest,
  CreateBookingRequest,
  CreatedManager,
  CreateLotRequest,
  CreateManagerRequest,
  LoginRequest,
  Lot,
  LotUpdateResponse,
  ManagerBooking,
  MarkedRead,
  Notification,
  Page,
  PriceQuote,
  Receipt,
  RegisterRequest,
  Spot,
  SpotUpdateResponse,
  Stats,
  TokenResponse,
  UnreadCount,
  UpdateLotRequest,
  UpdateSpotRequest,
  UserProfile,
} from './types';

export type TimeWindow = { start: string; end: string };

export const authApi = {
  login: (body: LoginRequest) => request<TokenResponse>('/auth/login', { method: 'POST', body }),
  register: (body: RegisterRequest) => request<UserProfile>('/auth/register', { method: 'POST', body }),
};

export const usersApi = {
  me: () => request<UserProfile>('/users/me'),
  changePassword: (body: ChangePasswordRequest) =>
    request<undefined>('/users/me/password', { method: 'PUT', body }),
};

export const lotsApi = {
  list: () => request<Lot[]>('/lots'),
  get: (id: number) => request<Lot>(`/lots/${id}`),
  spots: (id: number) => request<Spot[]>(`/lots/${id}/spots`),
  availability: (id: number, window: TimeWindow) =>
    request<Availability>(`/lots/${id}/availability`, { query: window }),
};

export const pricingApi = {
  quote: (window: TimeWindow) => request<PriceQuote>('/pricing/quote', { query: window }),
};

export type MyBookingsQuery = { status?: BookingStatus; page?: number; size?: number };

export const bookingsApi = {
  create: (body: CreateBookingRequest) => request<Booking>('/bookings', { method: 'POST', body }),
  mine: (query: MyBookingsQuery) => request<Page<Booking>>('/bookings/me', { query }),
  get: (id: string) => request<Booking>(`/bookings/${id}`),
  extend: (id: string, newEnd: string) =>
    request<Booking>(`/bookings/${id}/extend`, { method: 'POST', body: { newEnd } }),
  cancel: (id: string) => request<Booking>(`/bookings/${id}/cancel`, { method: 'POST' }),
  checkout: (id: string) => request<Booking>(`/bookings/${id}/checkout`, { method: 'POST' }),
  receipt: (id: string) => request<Receipt>(`/bookings/${id}/receipt`),
};

export const notificationsApi = {
  list: (query: { page?: number; size?: number; unreadOnly?: boolean }) =>
    request<Page<Notification>>('/notifications', { query }),
  unreadCount: () => request<UnreadCount>('/notifications/unread-count'),
  markRead: (id: string) => request<Notification>(`/notifications/${id}/read`, { method: 'POST' }),
  markAllRead: () => request<MarkedRead>('/notifications/read-all', { method: 'POST' }),
};

export type ManagerBookingsQuery = {
  status?: BookingStatus;
  lotId?: number;
  from?: string;
  to?: string;
  q?: string;
  page?: number;
  size?: number;
};

export const managerApi = {
  stats: (range: { from?: string; to?: string }) => request<Stats>('/manager/stats', { query: range }),
  bookings: (query: ManagerBookingsQuery) =>
    request<Page<ManagerBooking>>('/manager/bookings', { query }),
  createLot: (body: CreateLotRequest) => request<Lot>('/manager/lots', { method: 'POST', body }),
  updateLot: (id: number, body: UpdateLotRequest) =>
    request<LotUpdateResponse>(`/manager/lots/${id}`, { method: 'PATCH', body }),
  updateSpot: (id: number, body: UpdateSpotRequest) =>
    request<SpotUpdateResponse>(`/manager/spots/${id}`, { method: 'PATCH', body }),
};

export const adminApi = {
  managers: (query: { page?: number; size?: number }) =>
    request<Page<UserProfile>>('/admin/managers', { query }),
  createManager: (body: CreateManagerRequest) =>
    request<CreatedManager>('/admin/managers', { method: 'POST', body }),
};
