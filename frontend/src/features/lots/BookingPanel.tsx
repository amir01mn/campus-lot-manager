import { useState, type SubmitEvent } from 'react';
import { useLocation } from 'react-router';
import { CalendarCheck, CreditCard, LogIn, MousePointerClick, Wallet } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { TimeWindow } from '../../api/endpoints';
import { errorMessage, isStatus } from '../../api/problem';
import type { Booking, PaymentMethod, Spot } from '../../api/types';
import { Button, ButtonLink } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Alert } from '../../components/ui/Feedback';
import { Field, Input } from '../../components/ui/Field';
import { cn } from '../../lib/cn';
import { formatMoney, PAYMENT_METHOD_LABEL, USER_TYPE_LABEL } from '../../lib/format';
import { formatDuration, formatRange } from '../../lib/time';
import { useAuth } from '../auth/authContext';
import { PriceBreakdown } from '../bookings/PriceBreakdown';
import { useCreateBooking, useQuote } from './hooks';
import { normalizePlate, PLATE_PATTERN } from './plate';

const PAYMENT_ICONS: Record<PaymentMethod, LucideIcon> = { CREDIT_CARD: CreditCard, DEBIT_CARD: CreditCard, PAYPAL: Wallet };
const PAYMENT_METHODS: PaymentMethod[] = ['CREDIT_CARD', 'DEBIT_CARD', 'PAYPAL'];

/**
 * Right-hand booking panel: selected spot, the price quote for the signed-in user, plate and payment method,
 * then confirm. Conflicts (409) and rule violations (422) are shown inline in plain language.
 */
export function BookingPanel({
  spot,
  window,
  windowValid,
  onBooked,
  onConflict,
}: {
  spot: Spot | null;
  window: TimeWindow | null;
  windowValid: boolean;
  onBooked?: (booking: Booking) => void;
  onConflict?: () => void;
}) {
  const { user } = useAuth();
  const location = useLocation();
  const [plate, setPlate] = useState('');
  const [plateTouched, setPlateTouched] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CREDIT_CARD');
  const [booked, setBooked] = useState<Booking | null>(null);
  const quote = useQuote(window, !!user && windowValid);
  const create = useCreateBooking();

  if (!user) {
    return (
      <Card className="p-5">
        <h2 className="text-base font-semibold">Book a spot</h2>
        <p className="mt-1 text-sm text-fg-muted">Sign in to see your price and reserve a spot. It takes a few seconds with a demo account.</p>
        <ButtonLink
          to={`/login?next=${encodeURIComponent(location.pathname)}`}
          className="mt-4 w-full"
        >
          <LogIn className="size-4" aria-hidden="true" /> Sign in to book
        </ButtonLink>
      </Card>
    );
  }

  if (booked) {
    return (
      <Card className="p-5">
        <div className="grid size-11 place-items-center rounded-full bg-success-soft text-success-soft-fg" aria-hidden="true">
          <CalendarCheck className="size-5" />
        </div>
        <h2 className="mt-3 text-lg font-semibold" role="status">
          Booking confirmed
        </h2>
        <p className="mt-1 text-sm text-fg-muted">
          Spot <strong className="text-fg">{booked.spot.label}</strong>, {formatRange(booked.startTime, booked.endTime)}.
        </p>
        <dl className="mt-4 space-y-1.5 rounded-lg bg-surface-muted p-3 text-sm">
          <div className="flex justify-between">
            <dt className="text-fg-muted">Reference</dt>
            <dd className="font-mono font-semibold">{booked.referenceCode}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-fg-muted">Deposit charged</dt>
            <dd className="tabular">{formatMoney(booked.depositAmount)}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-fg-muted">Total</dt>
            <dd className="tabular font-medium">{formatMoney(booked.totalAmount)}</dd>
          </div>
        </dl>
        <div className="mt-4 grid gap-2">
          <ButtonLink to="/bookings">View my bookings</ButtonLink>
          <Button
            variant="secondary"
            onClick={() => {
              setBooked(null);
              create.reset();
            }}
          >
            Book another spot
          </Button>
        </div>
      </Card>
    );
  }

  const normalizedPlate = normalizePlate(plate);
  const plateError = plateTouched && !PLATE_PATTERN.test(normalizedPlate) ? 'Use 2–10 letters, digits, spaces or hyphens.' : null;
  const canSubmit = !!spot && windowValid && !!window && PLATE_PATTERN.test(normalizedPlate) && !create.isPending;
  const conflict = isStatus(create.error, 409);

  function onSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setPlateTouched(true);
    if (!spot || !window || !canSubmit) return;
    create.mutate(
      { spotId: spot.id, plate: normalizedPlate, start: window.start, end: window.end, paymentMethod },
      {
        onSuccess: (booking) => {
          setBooked(booking);
          onBooked?.(booking);
        },
        onError: (error) => {
          if (isStatus(error, 409)) onConflict?.();
        },
      },
    );
  }

  return (
    <Card>
      <form onSubmit={onSubmit} noValidate aria-label="Booking details">
        <div className="border-b border-border p-5">
          <h2 className="text-base font-semibold">Your booking</h2>
          {spot ? (
            <div className="mt-3 flex items-center gap-3">
              {/* shrink-0: the badge keeps its natural width (e.g. "L12-104"); the text column wraps instead */}
              <span className="grid h-12 min-w-12 shrink-0 place-items-center rounded-lg bg-accent px-2.5 text-sm font-semibold whitespace-nowrap tabular-nums text-accent-fg">{spot.label}</span>
              <div className="min-w-0 text-sm">
                <p className="font-medium">Spot {spot.label}</p>
                {window && (
                  <p className="text-fg-muted">
                    {formatRange(window.start, window.end)} · {formatDuration(Date.parse(window.end) - Date.parse(window.start))}
                  </p>
                )}
              </div>
            </div>
          ) : (
            <p className="mt-3 flex items-center gap-2 rounded-lg border border-dashed border-border-strong px-3 py-3 text-sm text-fg-muted">
              <MousePointerClick className="size-4 shrink-0" aria-hidden="true" /> Select a free spot on the map.
            </p>
          )}
        </div>

        <div className="border-b border-border p-5">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-sm font-semibold">Price</h3>
            <span className="text-xs text-fg-subtle">{USER_TYPE_LABEL[user.userType]} rate</span>
          </div>
          {windowValid ? (
            quote.isError ? (
              <p className="text-sm text-danger">{errorMessage(quote.error)}</p>
            ) : (
              <PriceBreakdown quote={quote.data} loading={quote.isFetching} />
            )
          ) : (
            <p className="text-sm text-fg-muted">Choose a valid time window to see your price.</p>
          )}
        </div>

        <div className="space-y-4 p-5">
          <Field label="Licence plate" error={plateError} hint="As shown on your plate, e.g. ABCD 123.">
            <Input
              value={plate}
              onChange={(e) => setPlate(e.target.value.toUpperCase())}
              onBlur={() => setPlateTouched(true)}
              maxLength={12}
              autoComplete="off"
              spellCheck={false}
              className="font-mono uppercase"
              placeholder="ABCD 123"
            />
          </Field>

          <fieldset>
            <legend className="mb-1.5 text-sm font-medium">Payment method</legend>
            <div className="grid grid-cols-3 gap-2">
              {PAYMENT_METHODS.map((method) => {
                const Icon = PAYMENT_ICONS[method];
                const checked = method === paymentMethod;
                return (
                  <label
                    key={method}
                    className={cn(
                      'flex cursor-pointer flex-col items-center gap-1 rounded-lg border px-2 py-2.5 text-center text-xs font-medium transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-ring',
                      checked ? 'border-accent bg-accent-soft text-accent-soft-fg' : 'border-border-strong text-fg-muted hover:bg-surface-muted',
                    )}
                  >
                    <input
                      type="radio"
                      name="paymentMethod"
                      value={method}
                      checked={checked}
                      onChange={() => setPaymentMethod(method)}
                      className="sr-only"
                    />
                    <Icon className="size-4" aria-hidden="true" />
                    {PAYMENT_METHOD_LABEL[method]}
                  </label>
                );
              })}
            </div>
          </fieldset>

          {create.isError && (
            <Alert tone={conflict ? 'warning' : 'danger'} title={conflict ? 'That spot was just taken' : 'Booking failed'}>
              {conflict
                ? `${errorMessage(create.error)} Pick another spot or change your time.`
                : errorMessage(create.error)}
            </Alert>
          )}

          <Button type="submit" size="lg" className="w-full" disabled={!canSubmit} loading={create.isPending}>
            {quote.data && windowValid ? `Confirm and pay ${formatMoney(quote.data.deposit)} deposit` : 'Confirm booking'}
          </Button>
          <p className="text-center text-xs text-fg-subtle">Cancel at least 1 hour before the start for a full deposit refund.</p>
        </div>
      </form>
    </Card>
  );
}
