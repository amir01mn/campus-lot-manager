import type { ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ArrowLeft, Printer } from 'lucide-react';
import type { Receipt } from '../../api/types';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { ErrorState, LoadingRegion, Skeleton } from '../../components/ui/Feedback';
import { Logo } from '../../components/layout/Logo';
import { formatMoney, PAYMENT_METHOD_LABEL, PAYMENT_TYPE_LABEL, STATUS_LABEL } from '../../lib/format';
import { formatDateLong, formatDateTime, formatDuration, formatRange } from '../../lib/time';
import { useReceipt } from './hooks';

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex justify-between gap-4 py-1.5 text-sm">
      <dt className="text-fg-muted">{label}</dt>
      <dd className="text-right font-medium text-fg">{children}</dd>
    </div>
  );
}

function ReceiptDocument({ receipt }: { receipt: Receipt }) {
  const { booking } = receipt;
  const statusTone = booking.status === 'CANCELLED' ? 'danger' : booking.status === 'COMPLETED' ? 'neutral' : 'accent';
  return (
    <Card className="mx-auto max-w-2xl print:border-0 print:shadow-none">
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-border p-6 sm:p-8">
        <div>
          <Logo />
          <p className="mt-3 text-xs text-fg-subtle">CampusPark · York University parking services</p>
        </div>
        <div className="text-right">
          <p className="text-xs font-medium tracking-wide text-fg-subtle uppercase">Receipt</p>
          <p className="font-mono text-lg font-semibold">{booking.referenceCode}</p>
          <p className="text-xs text-fg-muted">Issued {formatDateLong(new Date().toISOString())}</p>
        </div>
      </div>

      <div className="grid gap-6 border-b border-border p-6 sm:grid-cols-2 sm:p-8">
        <div>
          <h2 className="mb-1 text-xs font-semibold tracking-wide text-fg-subtle uppercase">Billed to</h2>
          <p className="font-medium">{receipt.customerName}</p>
          <p className="text-sm text-fg-muted">{receipt.customerEmail}</p>
        </div>
        <div>
          <h2 className="mb-1 text-xs font-semibold tracking-wide text-fg-subtle uppercase">Status</h2>
          <Badge tone={statusTone}>{STATUS_LABEL[booking.status]}</Badge>
        </div>
      </div>

      <dl className="border-b border-border p-6 sm:p-8">
        <Row label="Lot">{booking.spot.lotName}</Row>
        <Row label="Spot">{booking.spot.label}</Row>
        <Row label="When">{formatRange(booking.startTime, booking.endTime)}</Row>
        <Row label="Duration">{formatDuration(Date.parse(booking.endTime) - Date.parse(booking.startTime))}</Row>
        <Row label="Licence plate">
          <span className="font-mono">{booking.licencePlate}</span>
        </Row>
        <Row label="Rate">{formatMoney(booking.hourlyRate)}/hour (billed per started hour)</Row>
        <Row label="Booking total">{formatMoney(booking.totalAmount)}</Row>
        {booking.cancelledAt && <Row label="Cancelled">{formatDateTime(booking.cancelledAt)}</Row>}
        {booking.completedAt && <Row label="Completed">{formatDateTime(booking.completedAt)}</Row>}
      </dl>

      <div className="border-b border-border p-6 sm:p-8">
        <h2 className="mb-3 text-sm font-semibold">Payments</h2>
        {receipt.payments.length === 0 ? (
          <p className="text-sm text-fg-muted">No payments recorded.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <caption className="sr-only">Payments for booking {booking.referenceCode}</caption>
              <thead>
                <tr className="border-b border-border text-left text-xs text-fg-subtle">
                  <th scope="col" className="py-2 pr-3 font-medium">Date</th>
                  <th scope="col" className="py-2 pr-3 font-medium">Type</th>
                  <th scope="col" className="py-2 pr-3 font-medium">Method</th>
                  <th scope="col" className="py-2 text-right font-medium">Amount</th>
                </tr>
              </thead>
              <tbody>
                {receipt.payments.map((p) => (
                  <tr key={p.id} className="border-b border-border last:border-0">
                    <td className="py-2 pr-3 whitespace-nowrap text-fg-muted">{formatDateTime(p.createdAt)}</td>
                    <td className="py-2 pr-3">
                      {PAYMENT_TYPE_LABEL[p.type]}
                      {p.status === 'FAILED' && <span className="ml-1 text-danger">(failed)</span>}
                    </td>
                    <td className="py-2 pr-3 text-fg-muted">{PAYMENT_METHOD_LABEL[p.method]}</td>
                    <td className="tabular py-2 text-right">{p.type === 'REFUND' ? `−${formatMoney(p.amount)}` : formatMoney(p.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <dl className="space-y-1 p-6 sm:p-8">
        <Row label="Total charged">{formatMoney(receipt.totalCharged)}</Row>
        <Row label="Total refunded">{receipt.totalRefunded > 0 ? `−${formatMoney(receipt.totalRefunded)}` : formatMoney(0)}</Row>
        <div className="mt-2 flex items-baseline justify-between border-t border-border pt-3">
          <dt className="font-semibold">Net paid</dt>
          <dd className="tabular text-2xl font-semibold" data-testid="net-paid">
            {formatMoney(receipt.netPaid)}
          </dd>
        </div>
        <p className="pt-2 text-xs text-fg-subtle">All amounts in {receipt.currency}. Times in campus time (Toronto).</p>
      </dl>
    </Card>
  );
}

export function ReceiptPage() {
  const id = useParams().bookingId ?? '';
  const receipt = useReceipt(id);
  const navigate = useNavigate();

  return (
    <>
      <div className="no-print mx-auto mb-5 flex max-w-2xl items-center justify-between gap-3">
        <Link
          to=".."
          onClick={(event) => {
            event.preventDefault();
            void navigate(-1);
          }}
          className="inline-flex items-center gap-1.5 text-sm text-fg-muted hover:text-fg"
        >
          <ArrowLeft className="size-4" aria-hidden="true" /> Back
        </Link>
        <Button variant="secondary" icon={<Printer className="size-4" />} onClick={() => window.print()} disabled={!receipt.data}>
          Print receipt
        </Button>
      </div>
      {receipt.isPending ? (
        <LoadingRegion label="Loading receipt">
          <Card className="mx-auto max-w-2xl space-y-4 p-8">
            <Skeleton className="h-8 w-40" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-5/6" />
            <Skeleton className="h-32 w-full" />
          </Card>
        </LoadingRegion>
      ) : receipt.isError ? (
        <Card className="mx-auto max-w-2xl">
          <ErrorState error={receipt.error} onRetry={() => void receipt.refetch()} title="Couldn’t load the receipt" />
        </Card>
      ) : (
        <ReceiptDocument receipt={receipt.data} />
      )}
    </>
  );
}
