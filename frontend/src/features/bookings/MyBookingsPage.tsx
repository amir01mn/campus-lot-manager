import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { CalendarPlus, CalendarX2, Clock, LogOut, ReceiptText, X } from 'lucide-react';
import { errorMessage } from '../../api/problem';
import type { Booking } from '../../api/types';
import { Button, ButtonLink } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { EmptyState, ErrorState, LoadingRegion, Skeleton } from '../../components/ui/Feedback';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { TabPanel, Tabs } from '../../components/ui/Tabs';
import { useToast } from '../../components/ui/toastContext';
import { formatMoney } from '../../lib/format';
import { useNow } from '../../lib/useNow';
import { BookingCard } from './BookingCard';
import { bookingPhase, type BookingPhase } from './bookingRules';
import { CancelBookingDialog } from './CancelBookingDialog';
import { ExtendBookingDialog } from './ExtendBookingDialog';
import { useCheckoutBooking, useMyBookings } from './hooks';

const TABS: readonly BookingPhase[] = ['upcoming', 'active', 'past', 'cancelled'];
const TAB_LABEL: Record<BookingPhase, string> = { upcoming: 'Upcoming', active: 'Active', past: 'Past', cancelled: 'Cancelled' };
const EMPTY: Record<BookingPhase, { title: string; description: string }> = {
  upcoming: { title: 'No upcoming bookings', description: 'Find a lot, pick a spot and your booking will show up here.' },
  active: { title: 'Nothing active right now', description: 'Bookings appear here once their start time arrives.' },
  past: { title: 'No past bookings', description: 'Completed bookings and their receipts will be listed here.' },
  cancelled: { title: 'No cancelled bookings', description: 'Bookings you cancel are kept here with their receipts.' },
};
const PAGE_SIZE = 10;

function isPhase(value: string | null): value is BookingPhase {
  return value !== null && (TABS as readonly string[]).includes(value);
}

function ListSkeleton() {
  return (
    <LoadingRegion label="Loading bookings">
      <div className="space-y-3">
        {[0, 1, 2].map((i) => (
          <div key={i} className="flex gap-4 rounded-xl border border-border bg-surface p-5">
            <Skeleton className="h-14 w-14" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-4 w-3/4" />
            </div>
            <Skeleton className="h-8 w-24" />
          </div>
        ))}
      </div>
    </LoadingRegion>
  );
}

export function MyBookingsPage() {
  const [params, setParams] = useSearchParams();
  const tabParam = params.get('tab');
  const tab: BookingPhase = isPhase(tabParam) ? tabParam : 'upcoming';
  const [pastPage, setPastPage] = useState(0);
  const [cancelledPage, setCancelledPage] = useState(0);
  const [extending, setExtending] = useState<Booking | null>(null);
  const [cancelling, setCancelling] = useState<Booking | null>(null);
  const toast = useToast();
  const checkout = useCheckoutBooking();

  const confirmed = useMyBookings({ status: 'CONFIRMED', size: 100 });
  const past = useMyBookings({ status: 'COMPLETED', page: pastPage, size: PAGE_SIZE });
  const cancelled = useMyBookings({ status: 'CANCELLED', page: cancelledPage, size: PAGE_SIZE });

  const now = useNow();
  const confirmedList = confirmed.data?.content ?? [];
  // the API returns newest start first; upcoming reads better soonest first
  const upcoming = confirmedList.filter((b) => bookingPhase(b, now) === 'upcoming').reverse();
  const active = confirmedList.filter((b) => bookingPhase(b, now) === 'active');

  const counts: Record<BookingPhase, number | undefined> = {
    upcoming: confirmed.data ? upcoming.length : undefined,
    active: confirmed.data ? active.length : undefined,
    past: past.data?.totalElements,
    cancelled: cancelled.data?.totalElements,
  };

  const query = tab === 'past' ? past : tab === 'cancelled' ? cancelled : confirmed;
  const list = tab === 'upcoming' ? upcoming : tab === 'active' ? active : (query.data?.content ?? []);

  function actionsFor(booking: Booking, phase: BookingPhase) {
    const receipt = (
      <ButtonLink to={`/bookings/${booking.id}/receipt`} variant="ghost" size="sm">
        <ReceiptText className="size-4" aria-hidden="true" /> Receipt
      </ButtonLink>
    );
    if (phase === 'upcoming' || phase === 'active') {
      const ended = Date.parse(booking.endTime) <= now.getTime();
      return (
        <>
          {receipt}
          {!ended && (
            <Button variant="secondary" size="sm" icon={<CalendarPlus className="size-4" />} onClick={() => setExtending(booking)}>
              Extend
            </Button>
          )}
          {phase === 'active' && (
            <Button
              variant="primary"
              size="sm"
              icon={<LogOut className="size-4" />}
              loading={checkout.isPending && checkout.variables === booking.id}
              onClick={() =>
                checkout.mutate(booking.id, {
                  onSuccess: (done) =>
                    toast({
                      title: `Checked out of ${done.spot.label}`,
                      description: `Final charge ${formatMoney(Math.max(done.totalAmount - done.depositAmount, 0))}. Your receipt is ready.`,
                    }),
                  onError: (error) => toast({ tone: 'danger', title: 'Checkout failed', description: errorMessage(error) }),
                })
              }
            >
              Check out
            </Button>
          )}
          {!ended && (
            <Button variant="ghost" size="sm" className="text-danger hover:text-danger" icon={<X className="size-4" />} onClick={() => setCancelling(booking)}>
              Cancel
            </Button>
          )}
        </>
      );
    }
    return receipt;
  }

  return (
    <>
      <PageHeader
        title="My bookings"
        description="Extend, cancel or check out, and get a receipt for every booking. Times are campus time."
        actions={<ButtonLink to="/">Book a spot</ButtonLink>}
      />
      <div className="mb-5">
        <Tabs
          idBase="bookings"
          label="Booking status"
          value={tab}
          onChange={(next) => setParams(next === 'upcoming' ? {} : { tab: next }, { replace: true })}
          items={TABS.map((key) => ({ key, label: TAB_LABEL[key], count: counts[key] }))}
        />
      </div>
      <TabPanel idBase="bookings" value={tab}>
        {query.isPending ? (
          <ListSkeleton />
        ) : query.isError ? (
          <Card>
            <ErrorState error={query.error} onRetry={() => void query.refetch()} />
          </Card>
        ) : list.length === 0 ? (
          <Card>
            <EmptyState
              icon={tab === 'cancelled' ? <CalendarX2 className="size-5" /> : <Clock className="size-5" />}
              title={EMPTY[tab].title}
              description={EMPTY[tab].description}
              action={tab === 'upcoming' ? <ButtonLink to="/">Find parking</ButtonLink> : undefined}
            />
          </Card>
        ) : (
          <div className="space-y-3">
            {list.map((booking) => (
              <BookingCard key={booking.id} booking={booking} phase={tab} actions={actionsFor(booking, tab)} />
            ))}
          </div>
        )}
        {tab === 'past' && past.data && (
          <div className="mt-4">
            <Pagination page={past.data.number} totalPages={past.data.totalPages} totalElements={past.data.totalElements} onPageChange={setPastPage} noun="bookings" />
          </div>
        )}
        {tab === 'cancelled' && cancelled.data && (
          <div className="mt-4">
            <Pagination
              page={cancelled.data.number}
              totalPages={cancelled.data.totalPages}
              totalElements={cancelled.data.totalElements}
              onPageChange={setCancelledPage}
              noun="bookings"
            />
          </div>
        )}
      </TabPanel>

      <ExtendBookingDialog booking={extending} onClose={() => setExtending(null)} />
      <CancelBookingDialog booking={cancelling} onClose={() => setCancelling(null)} />
    </>
  );
}
