import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ExternalLink, LayoutGrid, MousePointerClick, Plus, Radio } from 'lucide-react';
import { errorMessage } from '../../api/problem';
import type { Lot, Spot } from '../../api/types';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card, CardHeader } from '../../components/ui/Card';
import { EmptyState, ErrorState, LoadingRegion, Skeleton } from '../../components/ui/Feedback';
import { PageHeader } from '../../components/ui/PageHeader';
import { Switch } from '../../components/ui/Switch';
import { useToast } from '../../components/ui/toastContext';
import { cn } from '../../lib/cn';
import { formatDateTime, formatRelative } from '../../lib/time';
import { useLots, useSpots } from '../lots/hooks';
import { OccupancyBar } from '../lots/OccupancyBar';
import { SpotGrid, SpotLegend } from '../lots/SpotGrid';
import { managerSpotState, type SpotVisualState } from '../lots/spotState';
import { CreateLotDialog } from './CreateLotDialog';
import { useUpdateLot, useUpdateSpot } from './hooks';

const MANAGER_META: Record<SpotVisualState, { label: string; description: string }> = {
  free: { label: 'Empty', description: 'Enabled, sensor reports no car' },
  occupied: { label: 'Occupied', description: 'Enabled, sensor reports a car' },
  booked: { label: 'Booked', description: 'Booked' },
  maintenance: { label: 'Maintenance', description: 'Flagged for maintenance' },
  disabled: { label: 'Disabled', description: 'Switched off by a manager' },
};
const LEGEND: SpotVisualState[] = ['free', 'occupied', 'maintenance', 'disabled'];

/** "2 upcoming bookings are affected…" — shown after a change that makes a lot or spot unbookable. */
function affectedText(count: number, what: string): string {
  return `${count} upcoming booking${count === 1 ? ' is' : 's are'} on this ${what}. They stay valid, but can’t be extended while it’s unavailable. Consider contacting the drivers.`;
}

function SpotPanel({ spot, lotEnabled }: { spot: Spot; lotEnabled: boolean }) {
  const update = useUpdateSpot();
  const toast = useToast();

  function change(body: { enabled?: boolean; maintenanceRequired?: boolean }, done: string) {
    update.mutate(
      { id: spot.id, body },
      {
        onSuccess: (result) => {
          if (result.affectedUpcomingBookings > 0) {
            toast({ tone: 'warning', title: `${done} spot ${spot.label}`, description: affectedText(result.affectedUpcomingBookings, 'spot') });
          } else {
            toast({ title: `${done} spot ${spot.label}` });
          }
        },
        onError: (error) => toast({ tone: 'danger', title: `Couldn’t update ${spot.label}`, description: errorMessage(error) }),
      },
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-3">
        <span className="grid h-12 min-w-12 place-items-center rounded-lg bg-accent px-2 font-semibold whitespace-nowrap text-accent-fg">{spot.label}</span>
        <div className="text-sm">
          <p className="font-medium">Spot {spot.label}</p>
          <p className="text-fg-muted">{spot.bookable ? 'Bookable' : lotEnabled ? 'Not bookable' : 'Lot closed'}</p>
        </div>
      </div>
      <dl className="grid grid-cols-2 gap-2 text-sm">
        <div className="rounded-lg bg-surface-muted p-2.5">
          <dt className="flex items-center gap-1 text-xs text-fg-muted">
            <Radio className="size-3" aria-hidden="true" /> Sensor
          </dt>
          <dd className="font-medium">{spot.occupied ? 'Car present' : 'Empty'}</dd>
        </div>
        <div className="rounded-lg bg-surface-muted p-2.5">
          <dt className="text-xs text-fg-muted">{spot.occupied ? 'Occupied since' : 'Last reading'}</dt>
          <dd className="font-medium">
            {spot.occupied && spot.occupiedSince
              ? formatDateTime(spot.occupiedSince)
              : spot.lastSensorUpdate
                ? formatRelative(spot.lastSensorUpdate)
                : 'Never'}
          </dd>
        </div>
      </dl>
      <div className="space-y-4 border-t border-border pt-4">
        <Switch
          label="Enabled"
          description="Disabled spots can’t be booked. Sensor readings never change this."
          checked={spot.enabled}
          disabled={update.isPending}
          onChange={(enabled) => change({ enabled }, enabled ? 'Enabled' : 'Disabled')}
        />
        <Switch
          label="Needs maintenance"
          description="Takes the spot out of service and alerts all managers."
          tone="warning"
          checked={spot.maintenanceRequired}
          disabled={update.isPending}
          onChange={(maintenanceRequired) => change({ maintenanceRequired }, maintenanceRequired ? 'Flagged' : 'Cleared maintenance on')}
        />
      </div>
    </div>
  );
}

function LotSpots({ lot }: { lot: Lot }) {
  const spots = useSpots(lot.id);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const selected = spots.data?.find((s) => s.id === selectedId) ?? null;
  const counts: Partial<Record<SpotVisualState, number>> = {};
  for (const spot of spots.data ?? []) {
    const state = managerSpotState(spot);
    counts[state] = (counts[state] ?? 0) + 1;
  }

  return (
    <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_20rem]">
      <Card>
        <CardHeader
          title={`Spots in ${lot.name}`}
          description="Select a spot to switch it on or off or flag it for maintenance. Live sensor state updates every 15 s."
        />
        <div className="p-5">
          <div className="mb-4">
            <SpotLegend states={LEGEND} meta={MANAGER_META} counts={spots.data ? counts : undefined} />
          </div>
          {spots.isPending ? (
            <LoadingRegion label="Loading spots">
              <div className="grid grid-cols-[repeat(auto-fill,minmax(3.5rem,1fr))] gap-2">
                {Array.from({ length: 24 }, (_, i) => (
                  <Skeleton key={i} className="h-14" />
                ))}
              </div>
            </LoadingRegion>
          ) : spots.isError ? (
            <ErrorState error={spots.error} onRetry={() => void spots.refetch()} />
          ) : (
            <SpotGrid
              label={`Spots in ${lot.name}`}
              spots={spots.data}
              getState={managerSpotState}
              isSelectable={() => true}
              selectedId={selectedId}
              onSelect={(spot) => setSelectedId(spot.id)}
              meta={MANAGER_META}
            />
          )}
        </div>
      </Card>
      <Card className="p-5 xl:sticky xl:top-24">
        {selected ? (
          <SpotPanel spot={selected} lotEnabled={lot.enabled} />
        ) : (
          <p className="flex items-center gap-2 text-sm text-fg-muted">
            <MousePointerClick className="size-4 shrink-0" aria-hidden="true" /> Select a spot to manage it.
          </p>
        )}
      </Card>
    </div>
  );
}

export function LotManagementPage() {
  const lots = useLots();
  const [params, setParams] = useSearchParams();
  const [creating, setCreating] = useState(false);
  const updateLot = useUpdateLot();
  const toast = useToast();
  const selectedId = Number(params.get('lot')) || lots.data?.[0]?.id;
  const selected = lots.data?.find((l) => l.id === selectedId) ?? null;

  function toggleLot(lot: Lot, enabled: boolean) {
    updateLot.mutate(
      { id: lot.id, body: { enabled } },
      {
        onSuccess: (result) => {
          const title = `${lot.name} ${enabled ? 'opened' : 'closed'}`;
          if (result.affectedUpcomingBookings > 0) {
            toast({ tone: 'warning', title, description: affectedText(result.affectedUpcomingBookings, 'lot') });
          } else {
            toast({ title, description: enabled ? 'Its enabled spots can be booked again.' : 'No upcoming bookings are affected.' });
          }
        },
        onError: (error) => toast({ tone: 'danger', title: 'Couldn’t update the lot', description: errorMessage(error) }),
      },
    );
  }

  return (
    <>
      <PageHeader
        title="Lots & spots"
        description="Open or close lots, take spots out of service, and add new lots."
        actions={
          <Button icon={<Plus className="size-4" />} onClick={() => setCreating(true)}>
            New lot
          </Button>
        }
      />

      {lots.isPending ? (
        <LoadingRegion label="Loading lots">
          <div className="grid gap-4 md:grid-cols-3">
            <Skeleton className="h-36 rounded-xl" />
            <Skeleton className="h-36 rounded-xl" />
            <Skeleton className="h-36 rounded-xl" />
          </div>
        </LoadingRegion>
      ) : lots.isError ? (
        <Card>
          <ErrorState error={lots.error} onRetry={() => void lots.refetch()} />
        </Card>
      ) : lots.data.length === 0 ? (
        <Card>
          <EmptyState
            icon={<LayoutGrid className="size-5" />}
            title="No lots yet"
            description="Create your first lot to start taking bookings."
            action={<Button onClick={() => setCreating(true)}>Create a lot</Button>}
          />
        </Card>
      ) : (
        <>
          <div role="list" aria-label="Lots" className="mb-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {lots.data.map((lot) => {
              const isSelected = lot.id === selected?.id;
              return (
                <div
                  role="listitem"
                  key={lot.id}
                  className={cn(
                    'rounded-xl border bg-surface p-4 shadow-card transition-colors',
                    isSelected ? 'border-accent ring-1 ring-accent' : 'border-border',
                  )}
                >
                  <div className="flex items-start justify-between gap-3">
                    <button
                      type="button"
                      className="min-w-0 text-left"
                      aria-pressed={isSelected}
                      onClick={() => setParams({ lot: String(lot.id) }, { replace: true })}
                    >
                      <p className="truncate font-semibold hover:text-accent">{lot.name}</p>
                      <p className="truncate text-xs text-fg-muted">{lot.location}</p>
                    </button>
                    {lot.enabled ? <Badge tone="success">Open</Badge> : <Badge tone="neutral">Closed</Badge>}
                  </div>
                  <OccupancyBar occupied={lot.occupiedSpots} total={lot.totalSpots} className="mt-4" />
                  <p className="tabular mt-2 text-xs text-fg-subtle">
                    {lot.totalSpots} spots · {lot.bookableSpots} bookable · {lot.occupiedSpots} occupied
                  </p>
                  <div className="mt-4 flex items-center justify-between gap-3 border-t border-border pt-3">
                    <Switch
                      label="Accepting bookings"
                      checked={lot.enabled}
                      disabled={updateLot.isPending}
                      onChange={(enabled) => toggleLot(lot, enabled)}
                    />
                  </div>
                  <Link to={`/lots/${lot.id}`} className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-accent hover:underline">
                    Public view <ExternalLink className="size-3" aria-hidden="true" />
                  </Link>
                </div>
              );
            })}
          </div>
          {selected && <LotSpots key={selected.id} lot={selected} />}
        </>
      )}

      <CreateLotDialog
        open={creating}
        onClose={() => setCreating(false)}
        onCreated={(lot) => {
          toast({ title: `${lot.name} created`, description: `${lot.totalSpots} spots are ready to book.` });
          setParams({ lot: String(lot.id) }, { replace: true });
        }}
      />
    </>
  );
}
