import { CircleDollarSign, CircleSlash } from 'lucide-react';
import { errorMessage } from '../../api/problem';
import type { Booking } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { Dialog } from '../../components/ui/Dialog';
import { Alert } from '../../components/ui/Feedback';
import { useToast } from '../../components/ui/toastContext';
import { formatMoney } from '../../lib/format';
import { formatRange } from '../../lib/time';
import { cancellationMessage, cancellationOutcome } from './bookingRules';
import { useCancelBooking } from './hooks';

/**
 * Confirmation before cancelling, explaining the refund rule up front: a full deposit refund when
 * cancelling at least 1 hour before the start, otherwise the deposit is kept.
 */
export function CancelBookingDialog({
  booking,
  onClose,
  byManager = false,
}: {
  booking: Booking | null;
  onClose: () => void;
  byManager?: boolean;
}) {
  const cancel = useCancelBooking();
  const toast = useToast();
  const outcome = booking ? cancellationOutcome(booking) : null;

  function close() {
    cancel.reset();
    onClose();
  }

  return (
    <Dialog
      open={booking !== null}
      onClose={close}
      title={byManager ? `Cancel booking ${booking?.referenceCode ?? ''}?` : 'Cancel this booking?'}
      description={booking ? `Spot ${booking.spot.label} · ${formatRange(booking.startTime, booking.endTime)}` : undefined}
      footer={
        <>
          <Button variant="secondary" onClick={close}>
            Keep booking
          </Button>
          <Button
            variant="danger"
            loading={cancel.isPending}
            onClick={() => {
              if (!booking) return;
              cancel.mutate(booking.id, {
                onSuccess: () => {
                  toast({
                    title: `Booking ${booking.referenceCode} cancelled`,
                    description: outcome?.refundable ? `${formatMoney(outcome.refundAmount)} deposit refunded.` : 'The deposit was kept.',
                  });
                  close();
                },
              });
            }}
          >
            Cancel booking
          </Button>
        </>
      }
    >
      {booking && outcome && (
        <div className="space-y-3">
          <Alert
            tone={outcome.refundable ? 'success' : 'warning'}
            title={outcome.refundable ? 'Full deposit refund' : 'No refund'}
          >
            <span data-testid="refund-message">{cancellationMessage(outcome, booking.depositAmount)}</span>
          </Alert>
          <ul className="space-y-1.5 text-sm text-fg-muted">
            <li className="flex items-center gap-2">
              <CircleDollarSign className="size-4 shrink-0" aria-hidden="true" />
              Cancel at least 1 hour before the start to get your deposit back.
            </li>
            <li className="flex items-center gap-2">
              <CircleSlash className="size-4 shrink-0" aria-hidden="true" />
              Cancelling can’t be undone; the spot is released for others.
            </li>
          </ul>
          {cancel.isError && <Alert tone="danger">{errorMessage(cancel.error)}</Alert>}
        </div>
      )}
    </Dialog>
  );
}
