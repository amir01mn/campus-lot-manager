import { useState } from 'react';
import { errorMessage } from '../../api/problem';
import type { Booking } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { Dialog } from '../../components/ui/Dialog';
import { Alert } from '../../components/ui/Feedback';
import { Field, Select } from '../../components/ui/Field';
import { useToast } from '../../components/ui/toastContext';
import { formatMoney } from '../../lib/format';
import { formatDateTime, formatDuration, formatTime } from '../../lib/time';
import { extensionOptions } from './bookingRules';
import { useQuote } from '../lots/hooks';
import { PriceBreakdown } from './PriceBreakdown';
import { useExtendBooking } from './hooks';

function ExtendForm({ booking, onClose }: { booking: Booking; onClose: () => void }) {
  const options = extensionOptions(booking);
  const [newEnd, setNewEnd] = useState(options[1] ?? options[0] ?? '');
  const quote = useQuote(newEnd ? { start: booking.startTime, end: newEnd } : null, !!newEnd);
  const extend = useExtendBooking();
  const toast = useToast();
  const difference = quote.data ? quote.data.total - booking.totalAmount : null;

  if (options.length === 0) {
    return (
      <>
        <Alert tone="info">This booking already lasts the maximum 24 hours, so it can’t be extended.</Alert>
        <div className="mt-4 flex justify-end">
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
        </div>
      </>
    );
  }

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        extend.mutate(
          { id: booking.id, newEnd },
          {
            onSuccess: (updated) => {
              toast({ title: `Extended to ${formatTime(updated.endTime)}`, description: `New total ${formatMoney(updated.totalAmount)}.` });
              onClose();
            },
          },
        );
      }}
      className="space-y-4"
    >
      <p className="text-sm text-fg-muted">
        Currently ends <strong className="text-fg">{formatDateTime(booking.endTime)}</strong> (total {formatMoney(booking.totalAmount)}).
      </p>
      <Field label="New end time" hint="Campus time. The extra time is charged at checkout; your deposit is unchanged.">
        <Select value={newEnd} onChange={(e) => setNewEnd(e.target.value)}>
          {options.map((iso) => (
            <option key={iso} value={iso}>
              {formatDateTime(iso)} (+{formatDuration(Date.parse(iso) - Date.parse(booking.endTime))})
            </option>
          ))}
        </Select>
      </Field>
      <div className="rounded-lg border border-border bg-surface-muted p-4">
        <PriceBreakdown quote={quote.data} loading={quote.isFetching} />
        {difference !== null && (
          <p className="mt-2 text-xs text-fg-muted">
            {difference > 0 ? `${formatMoney(difference)} more than your current total.` : 'Same total: you’re still within the hours already billed.'}
          </p>
        )}
      </div>
      {extend.isError && (
        <Alert tone="danger" title="Couldn’t extend">
          {errorMessage(extend.error)}
        </Alert>
      )}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button type="submit" loading={extend.isPending} disabled={!newEnd}>
          Extend booking
        </Button>
      </div>
    </form>
  );
}

export function ExtendBookingDialog({ booking, onClose }: { booking: Booking | null; onClose: () => void }) {
  return (
    <Dialog open={booking !== null} onClose={onClose} title="Extend booking" description={booking ? `Spot ${booking.spot.label} · ${booking.referenceCode}` : undefined}>
      {booking && <ExtendForm key={booking.id} booking={booking} onClose={onClose} />}
    </Dialog>
  );
}
