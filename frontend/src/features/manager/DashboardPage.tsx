import { useMemo, useState, type ReactNode } from 'react';
import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router';
import { Card, CardHeader } from '../../components/ui/Card';
import { Alert, ErrorState, LoadingRegion, Skeleton } from '../../components/ui/Feedback';
import { PageHeader } from '../../components/ui/PageHeader';
import { formatMoney } from '../../lib/format';
import { formatCalendarDay } from '../../lib/time';
import { BookingsPerDayChart, LotUtilisationChart, RevenueByUserTypeChart } from './Charts';
import { DateRangePicker } from './DateRangePicker';
import { presetRange, toInstantRange, type DayRange, type PresetKey } from './dateRange';
import { useStats } from './hooks';
import { KpiTiles, KpiTilesSkeleton } from './KpiTiles';

function ChartCard({ title, description, children, action }: { title: string; description?: ReactNode; children: ReactNode; action?: ReactNode }) {
  return (
    <Card>
      <CardHeader title={title} description={description} actions={action} />
      <div className="p-4 sm:p-5">{children}</div>
    </Card>
  );
}

export function DashboardPage() {
  const [range, setRange] = useState<DayRange>(() => presetRange(30));
  const [preset, setPreset] = useState<PresetKey | 'custom'>('30d');
  const instants = useMemo(() => toInstantRange(range), [range]);
  const stats = useStats(instants);
  const rangeLabel = `${formatCalendarDay(range.fromDate)} – ${formatCalendarDay(range.toDate)}`;
  const tooLong = (Date.parse(instants.to) - Date.parse(instants.from)) / 86_400_000 > 366;

  return (
    <>
      <PageHeader
        title="Dashboard"
        description="Live occupancy across campus, plus bookings and revenue for the selected range."
        actions={
          <DateRangePicker
            value={range}
            preset={preset}
            onChange={(next, p) => {
              setRange(next);
              setPreset(p);
            }}
          />
        }
      />

      {tooLong && <Alert tone="warning" className="mb-6">Pick a range of at most 366 days.</Alert>}

      {stats.isPending ? (
        <LoadingRegion label="Loading statistics">
          <KpiTilesSkeleton />
          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <Skeleton className="h-80 rounded-xl lg:col-span-2" />
            <Skeleton className="h-72 rounded-xl" />
            <Skeleton className="h-72 rounded-xl" />
          </div>
        </LoadingRegion>
      ) : stats.isError ? (
        <Card>
          <ErrorState error={stats.error} onRetry={() => void stats.refetch()} title="Couldn’t load statistics" />
        </Card>
      ) : (
        <div className={stats.isPlaceholderData ? 'opacity-70 transition-opacity' : 'transition-opacity'}>
          <KpiTiles stats={stats.data} />
          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <div className="lg:col-span-2">
              <ChartCard title="Bookings per day" description={`By start date, campus time · ${rangeLabel}`}>
                <BookingsPerDayChart data={stats.data.bookingsPerDay} />
              </ChartCard>
            </div>
            <ChartCard title="Revenue by user type" description={`${formatMoney(stats.data.revenue)} net in range`}>
              <RevenueByUserTypeChart data={stats.data.revenueByUserType} />
            </ChartCard>
            <ChartCard
              title="Utilisation per lot"
              description="Booked hours ÷ available spot-hours in range"
              action={
                <Link to="/manager/lots" className="inline-flex items-center gap-1 text-sm font-medium text-accent hover:underline">
                  Manage lots <ArrowRight className="size-3.5" aria-hidden="true" />
                </Link>
              }
            >
              <LotUtilisationChart data={stats.data.lotUtilisation} />
            </ChartCard>
          </div>
        </div>
      )}
    </>
  );
}
