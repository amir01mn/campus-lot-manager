import { Link } from 'react-router';
import { ArrowRight, Car, CircleParking, MapPin, Radio } from 'lucide-react';
import type { Lot } from '../../api/types';
import { Badge } from '../../components/ui/Badge';
import { Card } from '../../components/ui/Card';
import { EmptyState, ErrorState, LoadingRegion, Skeleton } from '../../components/ui/Feedback';
import { PageHeader } from '../../components/ui/PageHeader';
import { cn } from '../../lib/cn';
import { useAuth } from '../auth/authContext';
import { useLots } from './hooks';
import { OccupancyBar } from './OccupancyBar';

function freeNow(lot: Lot): number {
  return lot.enabled ? Math.max(lot.bookableSpots - lot.occupiedSpots, 0) : 0;
}

function LotCard({ lot }: { lot: Lot }) {
  const free = freeNow(lot);
  return (
    <Card className={cn('group flex flex-col transition-shadow hover:shadow-pop', !lot.enabled && 'opacity-75')}>
      <div className="flex-1 p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="truncate text-base font-semibold text-fg">
              <Link to={`/lots/${lot.id}`} className="after:absolute after:inset-0 focus-visible:outline-none">
                {lot.name}
              </Link>
            </h2>
            <p className="mt-1 flex items-center gap-1 text-sm text-fg-muted">
              <MapPin className="size-3.5 shrink-0" aria-hidden="true" />
              <span className="truncate">{lot.location}</span>
            </p>
          </div>
          {lot.enabled ? <Badge tone="success">Open</Badge> : <Badge tone="neutral">Closed</Badge>}
        </div>

        <div className="mt-5 flex items-end gap-2">
          <span className="tabular text-4xl font-semibold tracking-tight text-fg">{free}</span>
          <span className="pb-1 text-sm text-fg-muted">spots free now</span>
        </div>

        <OccupancyBar occupied={lot.occupiedSpots} total={lot.totalSpots} className="mt-4" />

        <dl className="mt-4 grid grid-cols-3 gap-2 text-center">
          {[
            { label: 'Total', value: lot.totalSpots },
            { label: 'Bookable', value: lot.bookableSpots },
            { label: 'Occupied', value: lot.occupiedSpots },
          ].map((item) => (
            <div key={item.label} className="rounded-lg bg-surface-muted px-2 py-2">
              <dt className="text-[11px] font-medium tracking-wide text-fg-subtle uppercase">{item.label}</dt>
              <dd className="tabular text-sm font-semibold text-fg">{item.value}</dd>
            </div>
          ))}
        </dl>
      </div>
      <div className="flex items-center justify-between border-t border-border px-5 py-3 text-sm font-medium text-accent">
        {lot.enabled ? 'View spots & book' : 'View lot'}
        <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
      </div>
    </Card>
  );
}

function LotCardSkeleton() {
  return (
    <Card className="p-5">
      <Skeleton className="h-5 w-2/3" />
      <Skeleton className="mt-2 h-4 w-1/2" />
      <Skeleton className="mt-6 h-9 w-24" />
      <Skeleton className="mt-5 h-2 w-full" />
      <div className="mt-4 grid grid-cols-3 gap-2">
        <Skeleton className="h-12" />
        <Skeleton className="h-12" />
        <Skeleton className="h-12" />
      </div>
    </Card>
  );
}

export function LotsPage() {
  const { user } = useAuth();
  const lots = useLots();
  const totals = (lots.data ?? []).reduce(
    (acc, lot) => ({ spots: acc.spots + lot.totalSpots, free: acc.free + freeNow(lot), occupied: acc.occupied + lot.occupiedSpots }),
    { spots: 0, free: 0, occupied: 0 },
  );

  return (
    <>
      <PageHeader
        eyebrow={
          <span className="inline-flex items-center gap-1.5 font-medium text-success">
            <Radio className="size-3.5" aria-hidden="true" /> Live from spot sensors
          </span>
        }
        title={user ? `Find parking, ${user.firstName}` : 'Find parking on campus'}
        description="Pick a lot to see every spot live, choose your time and book in under a minute."
      />

      {lots.data && lots.data.length > 0 && (
        <div className="mb-6 grid grid-cols-3 gap-3 sm:max-w-xl">
          {[
            { icon: CircleParking, label: 'Spots', value: totals.spots },
            { icon: Car, label: 'Occupied', value: totals.occupied },
            { icon: Radio, label: 'Free now', value: totals.free },
          ].map(({ icon: Icon, label, value }) => (
            <div key={label} className="rounded-xl border border-border bg-surface px-3 py-2.5 shadow-card">
              <p className="flex items-center gap-1.5 text-xs text-fg-muted">
                <Icon className="size-3.5" aria-hidden="true" /> {label}
              </p>
              <p className="tabular mt-0.5 text-xl font-semibold">{value}</p>
            </div>
          ))}
        </div>
      )}

      {lots.isPending ? (
        <LoadingRegion label="Loading lots">
          <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
            <LotCardSkeleton />
            <LotCardSkeleton />
            <LotCardSkeleton />
          </div>
        </LoadingRegion>
      ) : lots.isError ? (
        <Card>
          <ErrorState error={lots.error} onRetry={() => void lots.refetch()} title="Couldn’t load parking lots" />
        </Card>
      ) : lots.data.length === 0 ? (
        <Card>
          <EmptyState icon={<CircleParking className="size-6" />} title="No parking lots yet" description="A lot manager hasn’t created any lots." />
        </Card>
      ) : (
        <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
          {lots.data.map((lot) => (
            <div key={lot.id} className="relative">
              <LotCard lot={lot} />
            </div>
          ))}
        </div>
      )}
    </>
  );
}
