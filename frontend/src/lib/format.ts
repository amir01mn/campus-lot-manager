import type { BookingStatus, NotificationType, PaymentMethod, PaymentType, Role, UserType } from '../api/types';
import { LOCALE } from './time';

const cad = new Intl.NumberFormat(LOCALE, { style: 'currency', currency: 'CAD' });
const cadCompact = new Intl.NumberFormat(LOCALE, { style: 'currency', currency: 'CAD', maximumFractionDigits: 0 });

/** Money is always CAD in this app. */
export function formatMoney(amount: number): string {
  return cad.format(amount);
}

export function formatMoneyCompact(amount: number): string {
  return cadCompact.format(amount);
}

export function formatPercent(value: number): string {
  return `${value.toLocaleString(LOCALE, { maximumFractionDigits: 1 })}%`;
}

export const USER_TYPE_LABEL: Record<UserType, string> = {
  STUDENT: 'Student',
  FACULTY: 'Faculty',
  STAFF: 'Staff',
  VISITOR: 'Visitor',
};

export const ROLE_LABEL: Record<Role, string> = {
  USER: 'Driver',
  MANAGER: 'Lot manager',
  SUPER_MANAGER: 'Super manager',
};

export const STATUS_LABEL: Record<BookingStatus, string> = {
  CONFIRMED: 'Confirmed',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

export const PAYMENT_METHOD_LABEL: Record<PaymentMethod, string> = {
  CREDIT_CARD: 'Credit card',
  DEBIT_CARD: 'Debit card',
  PAYPAL: 'PayPal',
};

export const PAYMENT_TYPE_LABEL: Record<PaymentType, string> = {
  DEPOSIT: 'Deposit',
  FINAL: 'Final charge',
  REFUND: 'Refund',
};

export const NOTIFICATION_LABEL: Record<NotificationType, string> = {
  BOOKING_CONFIRMED: 'Booking confirmed',
  BOOKING_CANCELLED: 'Booking cancelled',
  BOOKING_EXTENDED: 'Booking extended',
  BOOKING_COMPLETED: 'Booking completed',
  OVERSTAY_DETECTED: 'Overstay alert',
  UNAUTHORIZED_PARKING: 'Unauthorized parking',
  MAINTENANCE_FLAGGED: 'Maintenance flagged',
};

export function fullName(user: { firstName: string; lastName: string }): string {
  return `${user.firstName} ${user.lastName}`;
}
