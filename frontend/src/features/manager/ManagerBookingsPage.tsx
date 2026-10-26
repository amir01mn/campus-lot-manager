import { useState } from 'react';
import { Link } from 'react-router';
import { FilterX, ReceiptText, Search, TableProperties, X } from 'lucide-react';
import type { Booking, BookingStatus } from '../../api/types';
import { Badge, type Tone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { EmptyState, ErrorState, LoadingRegion, Skeleton } from '../../components/ui/Feedback';
import { Field, Input, Select } from '../../components/ui/Field';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { formatMoney, STATUS_LABEL, USER_TYPE_LABEL } from '../../lib/format';
import { addDays, campusToIso, formatRange } from '../../lib/time';
import { useDebouncedValue } from '../../lib/useDebouncedValue';
import { useNow } from '../../lib/useNow';
import { CancelBookingDialog } from '../bookings/CancelBookingDialog';
import { useLots } from '../lots/hooks';
import { useManagerBookings } from './hooks';

const STATUS_TONE: Record<BookingStatus, Tone> = { CONFIRMED: 'accent', COMPLETED: 'neutral', CANCELLED: 'danger' };
const PAGE_SIZE = 15;

type Filters = { status: BookingStatus | ''; lotId: string; fromDate: string; toDate: string; q: string };
const NO_FILTERS: Filters = { status: '', lotId: '', fromDate: '', toDate: '', q: '' };

export function ManagerBookingsPage() {
  const [filters, setFilters] = useState<Filters>(NO_FILTERS);
  const [page, setPage] = useState(0);
  const [cancelling, setCancelling] = useState<Booking | null>(null);
  const q = useDebouncedValue(filters.q.trim(), 300);
  const lots = useLots();
  const now = useNow();

  const bookings = useManagerBookings({
    status: filters.status || undefined,
    lotId: filters.lotId ? Number(filters.lotId) : undefined,
    from: filters.fromDate ? campusToIso(filters.fromDate, '00:00') : undefined,
    to: filters.toDate ? campusToIso(addDays(filters.toDate, 1), '00:00') : undefined,
    q: q || undefined,
    page,
    size: PAGE_SIZE,
  });

  function update<K extends keyof Filters>(key: K, value: Filters[K]) {
    setFilters((current) => ({ ...current, [key]: value }));
    setPage(0);
  }

  const hasFilters = JSON.stringify(filters) !== JSON.stringify(NO_FILTERS);

  return (
    <>
      <PageHeader title="All bookings" description="Search every booking across lots. Managers can cancel confirmed bookings for customers." />
      <Card className="mb-5 p-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(0,1.5fr)_repeat(4,minmax(0,1fr))_auto] lg:items-end">
          <Field label="Search">
            <div className="relative">
              <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-fg-subtle" aria-hidden="true" />
              <Input
                type="search"
                className="pl-9"
                placeholder="Reference or plate"
                maxLength={20}
                value={filters.q}
                onChange={(e) => update('q', e.target.value)}
              />
            </div>
          </Field>
          <Field label="Status">
            <Select value={filters.status} onChange={(e) => update('status', e.target.value as Filters['status'])}>
              <option value="">Any status</option>
              {(Object.keys(STATUS_LABEL) as BookingStatus[]).map((s) => (
                <option key={s} value={s}>
                  {STATUS_LABEL[s]}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Lot">
            <Select value={filters.lotId} onChange={(e) => update('lotId', e.target.value)}>
              <option value="">All lots</option>
              {lots.data?.map((lot) => (
                <option key={lot.id} value={lot.id}>
                  {lot.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="From">
            <Input type="date" value={filters.fromDate} max={filters.toDate || undefined} onChange={(e) => update('fromDate', e.target.value)} />
          </Field>
          <Field label="To">
            <Input type="date" value={filters.toDate} min={filters.fromDate || undefined} onChange={(e) => update('toDate', e.target.value)} />
          </Field>
          <Button
            variant="ghost"
            icon={<FilterX className="size-4" />}
            disabled={!hasFilters}
            onClick={() => {
              setFilters(NO_FILTERS);
              setPage(0);
            }}
          >
            Clear
          </Button>
        </div>
      </Card>

      <Card className="overflow-hidden">
        {bookings.isPending ? (
          <LoadingRegion label="Loading bookings">
            <div className="space-y-3 p-5">
              {Array.from({ length: 6 }, (_, i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          </LoadingRegion>
        ) : bookings.isError ? (
          <ErrorState error={bookings.error} onRetry={() => void bookings.refetch()} />
        ) : bookings.data.content.length === 0 ? (
          <EmptyState
            icon={<TableProperties className="size-5" />}
            title="No bookings match"
            description={hasFilters ? 'Try widening the date range or clearing the filters.' : 'Bookings will appear here as drivers make them.'}
          />
        ) : (
          <div className={bookings.isPlaceholderData ? 'overflow-x-auto opacity-70' : 'overflow-x-auto'}>
            <table className="w-full min-w-[56rem] text-sm">
              <caption className="sr-only">Bookings, newest start first</caption>
              <thead className="bg-surface-muted text-left text-xs text-fg-muted">
                <tr>
                  <th scope="col" className="px-4 py-3 font-medium">Reference</th>
                  <th scope="col" className="px-4 py-3 font-medium">Customer</th>
                  <th scope="col" className="px-4 py-3 font-medium">Spot</th>
                  <th scope="col" className="px-4 py-3 font-medium">When (campus time)</th>
                  <th scope="col" className="px-4 py-3 font-medium">Plate</th>
                  <th scope="col" className="px-4 py-3 font-medium">Status</th>
                  <th scope="col" className="px-4 py-3 text-right font-medium">Total</th>
                  <th scope="col" className="px-4 py-3 text-right font-medium">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {bookings.data.content.map(({ booking, customer }) => {
                  const cancellable = booking.status === 'CONFIRMED' && Date.parse(booking.endTime) > now.getTime();
                  return (
                    <tr key={booking.id} className="hover:bg-surface-muted/60">
                      <td className="px-4 py-3 font-mono text-xs font-semibold whitespace-nowrap">{booking.referenceCode}</td>
                      <td className="px-4 py-3">
                        <p className="font-medium">
                          {customer.firstName} {customer.lastName}
                        </p>
                        <p className="text-xs text-fg-subtle">
                          {customer.email} · {USER_TYPE_LABEL[customer.userType]}
                        </p>
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        <p className="font-medium">{booking.spot.label}</p>
                        <p className="text-xs text-fg-subtle">{booking.spot.lotName.split(' — ')[0]}</p>
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap text-fg-muted">{formatRange(booking.startTime, booking.endTime)}</td>
                      <td className="px-4 py-3 font-mono text-xs whitespace-nowrap">{booking.licencePlate}</td>
                      <td className="px-4 py-3">
                        <Badge tone={STATUS_TONE[booking.status]}>{STATUS_LABEL[booking.status]}</Badge>
                      </td>
                      <td className="tabular px-4 py-3 text-right font-medium whitespace-nowrap">{formatMoney(booking.totalAmount)}</td>
                      <td className="px-4 py-3">
                        <div className="flex justify-end gap-1">
                          <Link
                            to={`/bookings/${booking.id}/receipt`}
                            className="grid size-8 place-items-center rounded-lg text-fg-muted hover:bg-surface-sunken hover:text-fg"
                            aria-label={`Receipt for ${booking.referenceCode}`}
                            title="Receipt"
                          >
                            <ReceiptText className="size-4" aria-hidden="true" />
                          </Link>
                          {cancellable && (
                            <button
                              type="button"
                              className="grid size-8 place-items-center rounded-lg text-danger hover:bg-danger-soft"
                              aria-label={`Cancel ${booking.referenceCode}`}
                              title="Cancel booking"
                              onClick={() => setCancelling(booking)}
                            >
                              <X className="size-4" aria-hidden="true" />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      {bookings.data && (
        <div className="mt-4">
          <Pagination
            page={bookings.data.number}
            totalPages={bookings.data.totalPages}
            totalElements={bookings.data.totalElements}
            onPageChange={setPage}
            noun="bookings"
          />
        </div>
      )}
      <CancelBookingDialog booking={cancelling} onClose={() => setCancelling(null)} byManager />
    </>
  );
}
