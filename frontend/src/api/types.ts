/**
 * Application-facing API types, derived from the generated OpenAPI schema (`npm run gen:api`).
 *
 * The backend's Java records don't mark properties as required, so the generator runs with
 * `--properties-required-by-default`. The few properties that really can be `null` are widened
 * here with {@link Nullable}, so a schema change shows up as a type error after regenerating.
 */
import type { components } from './schema.gen';

type Schemas = components['schemas'];

/** Marks the listed keys of `T` as `T[K] | null`. */
type Nullable<T, K extends keyof T> = Omit<T, K> & { [P in K]: T[P] | null };

export type UserType = Schemas['UserProfileResponse']['userType'];
export type Role = Schemas['UserProfileResponse']['role'];
export type BookingStatus = Schemas['BookingResponse']['status'];
export type PaymentMethod = Schemas['BookingResponse']['paymentMethod'];
export type PaymentType = Schemas['PaymentResponse']['type'];
export type NotificationType = Schemas['NotificationResponse']['type'];

export type UserProfile = Schemas['UserProfileResponse'];
export type TokenResponse = Omit<Schemas['TokenResponse'], 'user'> & { user: UserProfile };
export type LoginRequest = Schemas['LoginRequest'];
export type RegisterRequest = Schemas['RegisterRequest'];
export type ChangePasswordRequest = Schemas['ChangePasswordRequest'];

export type Lot = Schemas['LotResponse'];
export type Spot = Nullable<Schemas['SpotResponse'], 'lastSensorUpdate' | 'occupiedSince'>;
export type Availability = Omit<Schemas['AvailabilityResponse'], 'spots'> & { spots: Spot[] };
export type CreateLotRequest = Schemas['CreateLotRequest'];
export type UpdateLotRequest = Partial<Schemas['UpdateLotRequest']>;
export type UpdateSpotRequest = Partial<Schemas['UpdateSpotRequest']>;
export type LotUpdateResponse = Schemas['LotUpdateResponse'];
export type SpotUpdateResponse = Omit<Schemas['SpotUpdateResponse'], 'spot'> & { spot: Spot };

export type Booking = Nullable<Schemas['BookingResponse'], 'cancelledAt' | 'completedAt'>;
export type CreateBookingRequest = Schemas['CreateBookingRequest'];
export type PriceQuote = Schemas['PriceQuote'];
export type Payment = Nullable<Schemas['PaymentResponse'], 'providerReference'>;
export type Receipt = Omit<Schemas['ReceiptResponse'], 'booking' | 'payments'> & {
  booking: Booking;
  payments: Payment[];
};

export type Customer = Schemas['Customer'];
export type ManagerBooking = { booking: Booking; customer: Customer };

export type Notification = Nullable<Schemas['NotificationResponse'], 'bookingId' | 'spotId'>;
export type UnreadCount = Schemas['UnreadCount'];
export type MarkedRead = Schemas['MarkedRead'];

export type Stats = Omit<Schemas['StatsResponse'], 'bookingsByStatus'> & {
  bookingsByStatus: Partial<Record<BookingStatus, number>>;
};
export type DailyBookings = Schemas['DailyBookings'];
export type UserTypeRevenue = Schemas['UserTypeRevenue'];
export type LotUtilisation = Schemas['LotUtilisation'];

export type CreateManagerRequest = Schemas['CreateManagerRequest'];
export type CreatedManager = Schemas['CreatedManagerResponse'];

/** Spring-style page wrapper returned by every paginated endpoint. */
export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

/** One invalid field in a 400 validation problem. */
export type FieldViolation = { field: string; message: string };

/** RFC 7807 problem body as produced by the backend's GlobalExceptionHandler. */
export type ProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Machine-readable business rule id on 422 responses, e.g. `START_IN_PAST`. */
  code?: string;
  /** Field violations on 400 validation responses. */
  errors?: FieldViolation[];
};
