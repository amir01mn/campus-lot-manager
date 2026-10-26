import type { ReactNode } from 'react';
import { CalendarCheck, Car, CircleDollarSign, Gauge } from 'lucide-react';
import type { Stats } from '../../api/types';
import { Card } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Feedback';
import { formatMoney, formatPercent } from '../../lib/format';

function Tile({ icon, label, value, detail }: { icon: ReactNode; label: string; value: ReactNode; detail: ReactNode }) {
  return (
    <Card className="p-5">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-fg-muted">{label}</p>
        <span className="grid size-8 place-items-center rounded-lg bg-accent-soft text-accent-soft-fg" aria-hidden="true">
          {icon}
        </span>
      </div>
      <p className="tabular mt-3 text-3xl font-semibold tracking-tight text-fg">{value}</p>
      <p className="mt-1 text-xs text-fg-subtle">{detail}</p>
    </Card>
  );
}

/** Headline numbers: live occupancy (now) and bookings / revenue for the selected range. */
export function KpiTiles({ stats }: { stats: Stats }) {
  const confirmed = stats.bookingsByStatus.CONFIRMED ?? 0;
  const completed = stats.bookingsByStatus.COMPLETED ?? 0;
  const cancelled = stats.bookingsByStatus.CANCELLED ?? 0;
  return (
    <section aria-label="Key figures" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Tile icon={<Gauge className="size-4" />} label="Occupancy now" value={formatPercent(stats.occupancyPercent)} detail="Live from spot sensors" />
      <Tile
        icon={<Car className="size-4" />}
        label="Occupied spots"
        value={
          <>
            {stats.occupiedSpots}
            <span className="text-lg font-medium text-fg-subtle"> / {stats.totalSpots}</span>
          </>
        }
        detail="Across all lots"
      />
      <Tile
        icon={<CalendarCheck className="size-4" />}
        label="Bookings"
        value={stats.bookingsCount}
        detail={`${completed} completed · ${confirmed} confirmed · ${cancelled} cancelled`}
      />
      <Tile icon={<CircleDollarSign className="size-4" />} label="Revenue" value={formatMoney(stats.revenue)} detail="Deposits + final charges − refunds" />
    </section>
  );
}

export function KpiTilesSkeleton() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-hidden="true">
      {[0, 1, 2, 3].map((i) => (
        <Card key={i} className="p-5">
          <Skeleton className="h-4 w-24" />
          <Skeleton className="mt-4 h-8 w-28" />
          <Skeleton className="mt-2 h-3 w-36" />
        </Card>
      ))}
    </div>
  );
}
