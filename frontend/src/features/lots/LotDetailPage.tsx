import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, MapPin, RefreshCw } from 'lucide-react';
import { Badge } from '../../components/ui/Badge';
import { Card } from '../../components/ui/Card';
import { Alert, ErrorState, LoadingRegion, Skeleton } from '../../components/ui/Feedback';
import { defaultWindow, formatTime, validateWindow, windowToIso, type CampusWindow } from '../../lib/time';
import { BookingPanel } from './BookingPanel';
import { useAvailability, useLot, useSpots } from './hooks';
import { SpotGrid, SpotLegend } from './SpotGrid';
import { isBookableState, spotState, type SpotVisualState } from './spotState';
import { TimeWindowPicker } from './TimeWindowPicker';

const LEGEND_STATES: SpotVisualState[] = ['free', 'occupied', 'booked', 'maintenance', 'disabled'];

function GridSkeleton() {
  return (
    <LoadingRegion label="Loading spots">
      <div className="grid grid-cols-[repeat(auto-fill,minmax(3.5rem,1fr))] gap-1.5 sm:grid-cols-[repeat(auto-fill,minmax(4rem,1fr))] sm:gap-2">
        {Array.from({ length: 30 }, (_, i) => (
          <Skeleton key={i} className="h-14 rounded-lg" />
        ))}
      </div>
    </LoadingRegion>
  );
}

export function LotDetailPage() {
  const lotId = Number(useParams().lotId);
  const lot = useLot(lotId);
  const spots = useSpots(lotId);
  const [campusWindow, setCampusWindow] = useState<CampusWindow>(() => defaultWindow());
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const windowError = validateWindow(campusWindow);
  const isoWindow = useMemo(() => (windowError ? null : windowToIso(campusWindow)), [campusWindow, windowError]);
  const availability = useAvailability(lotId, isoWindow);
  const availableIds = useMemo(
    () => (availability.data ? new Set(availability.data.spots.map((s) => s.id)) : null),
    [availability.data],
  );

  const states = useMemo(() => {
    const map = new Map<number, SpotVisualState>();
    for (const spot of spots.data ?? []) map.set(spot.id, spotState(spot, availableIds));
    return map;
  }, [spots.data, availableIds]);
  const counts = useMemo(() => {
    const result: Partial<Record<SpotVisualState, number>> = {};
    for (const state of states.values()) result[state] = (result[state] ?? 0) + 1;
    return result;
  }, [states]);

  const selectedSpot = spots.data?.find((s) => s.id === selectedId) ?? null;
  const selectedState = selectedId === null ? null : states.get(selectedId);
  // a selection that became unavailable (window change, someone else booked it) is dropped
  const effectiveSpot = selectedSpot && selectedState && isBookableState(selectedState) ? selectedSpot : null;
  const freeForWindow = (counts.free ?? 0) + (counts.occupied ?? 0);

  if (lot.isError) {
    return (
      <Card>
        <ErrorState error={lot.error} onRetry={() => void lot.refetch()} title="Couldn’t load this lot" />
      </Card>
    );
  }

  return (
    <>
      <Link to="/" className="mb-4 inline-flex items-center gap-1.5 text-sm text-fg-muted hover:text-fg">
        <ArrowLeft className="size-4" aria-hidden="true" /> All lots
      </Link>
      <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0">
          {lot.data ? (
            <>
              <h1 className="text-2xl font-semibold tracking-tight sm:text-[1.75rem]">{lot.data.name}</h1>
              <p className="mt-1 flex items-center gap-1.5 text-sm text-fg-muted">
                <MapPin className="size-4" aria-hidden="true" /> {lot.data.location}
              </p>
            </>
          ) : (
            <>
              <Skeleton className="h-8 w-72" />
              <Skeleton className="mt-2 h-4 w-56" />
            </>
          )}
        </div>
        <div className="flex items-center gap-2 text-xs text-fg-subtle">
          {lot.data && !lot.data.enabled && <Badge tone="neutral">Closed for bookings</Badge>}
          {spots.dataUpdatedAt > 0 && (
            <span className="inline-flex items-center gap-1.5" aria-live="off">
              <span className="relative flex size-2">
                <span className="absolute inline-flex size-full animate-ping rounded-full bg-success opacity-60" />
                <span className="relative inline-flex size-2 rounded-full bg-success" />
              </span>
              Live · updated {formatTime(new Date(spots.dataUpdatedAt).toISOString())}
              {spots.isFetching && <RefreshCw className="size-3 animate-spin" aria-hidden="true" />}
            </span>
          )}
        </div>
      </header>

      {lot.data && !lot.data.enabled && (
        <Alert tone="warning" className="mb-6" title="This lot is closed">
          A lot manager has closed this lot, so no spots can be booked right now. Existing bookings are still valid.
        </Alert>
      )}

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <Card>
          <div className="border-b border-border p-5">
            <TimeWindowPicker
              value={campusWindow}
              onChange={(next) => {
                setCampusWindow(next);
              }}
              error={windowError}
            />
          </div>
          <div className="p-5">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-base font-semibold">
                Spots{' '}
                {spots.data && isoWindow && availability.data && (
                  <span className="text-sm font-normal text-fg-muted">
                    · <span className="tabular font-medium text-fg">{freeForWindow}</span> of {spots.data.length} free for your window
                  </span>
                )}
              </h2>
            </div>
            <div className="mb-5">
              <SpotLegend states={LEGEND_STATES} counts={spots.data ? counts : undefined} />
            </div>
            {spots.isPending ? (
              <GridSkeleton />
            ) : spots.isError ? (
              <ErrorState error={spots.error} onRetry={() => void spots.refetch()} title="Couldn’t load spots" />
            ) : (
              <SpotGrid
                label={`Spots in ${lot.data?.name ?? 'this lot'}`}
                spots={spots.data}
                getState={(spot) => states.get(spot.id) ?? 'disabled'}
                isSelectable={(_, state) => isBookableState(state) && !!isoWindow}
                selectedId={effectiveSpot?.id ?? null}
                onSelect={(spot) => setSelectedId(spot.id)}
              />
            )}
          </div>
        </Card>

        <div className="lg:sticky lg:top-24">
          <BookingPanel
            spot={effectiveSpot}
            window={isoWindow}
            windowValid={!!isoWindow}
            onConflict={() => {
              setSelectedId(null);
              void availability.refetch();
            }}
          />
        </div>
      </div>
    </>
  );
}
