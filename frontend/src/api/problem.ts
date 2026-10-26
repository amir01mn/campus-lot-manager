import { ApiError, NetworkError } from './client';

/** Friendlier copy for 422 business-rule codes. Anything not listed falls back to the server's detail. */
const CODE_MESSAGES: Record<string, string> = {
  START_IN_PAST: 'That start time has already passed. Pick a time from now on.',
  BOOKING_TOO_SHORT: 'Bookings must be at least 30 minutes long.',
  BOOKING_TOO_LONG: 'Bookings can be at most 24 hours long.',
  INVALID_TIME_RANGE: 'The end time must be after the start time.',
  INVALID_LICENCE_PLATE: 'Licence plates are 2–10 characters: letters, digits, spaces or hyphens.',
  SPOT_NOT_BOOKABLE: 'This spot is not available for booking right now. Pick another spot.',
  PAYMENT_DECLINED: 'Your payment was declined. Try another payment method.',
  CURRENT_PASSWORD_INCORRECT: 'Your current password is incorrect.',
  PASSWORD_UNCHANGED: 'The new password must be different from your current one.',
  INVALID_NEW_END: 'The new end time must be after the current end time.',
  CHECKOUT_TOO_EARLY: 'You can check out once your booking has started.',
};

export type ErrorContext = {
  /** Message for a 409, which means different things per screen (spot taken, duplicate e-mail, ...). */
  conflict?: string;
  /** Message for a 404. */
  notFound?: string;
};

/** Turns any thrown value into one sentence a user can act on. */
export function errorMessage(error: unknown, context: ErrorContext = {}): string {
  if (error instanceof NetworkError) return error.message;
  if (error instanceof ApiError) {
    const { status, problem } = error;
    if (status === 422 && problem.code && CODE_MESSAGES[problem.code]) return CODE_MESSAGES[problem.code] ?? '';
    if (status === 409) return context.conflict ?? problem.detail ?? 'That conflicts with existing data. Reload and try again.';
    if (status === 404) return context.notFound ?? problem.detail ?? 'We could not find that.';
    if (status === 401) return problem.detail ?? 'Please sign in again.';
    if (status === 403) return 'You don’t have permission to do that.';
    if (status === 400 && problem.errors?.length) {
      return problem.errors.map((e) => `${humanizeField(e.field)} ${e.message}`).join('. ') + '.';
    }
    if (status >= 500) return 'Something went wrong on our side. Please try again in a moment.';
    return problem.detail ?? problem.title ?? 'The request failed.';
  }
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

/** Field name → message, for showing 400 validation errors next to form inputs. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError) || error.status !== 400) return {};
  const result: Record<string, string> = {};
  for (const violation of error.fieldErrors) {
    const existing = result[violation.field];
    result[violation.field] = existing ? `${existing}; ${violation.message}` : violation.message;
  }
  return result;
}

export function isStatus(error: unknown, status: number): boolean {
  return error instanceof ApiError && error.status === status;
}

function humanizeField(field: string): string {
  const spaced = field.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}
