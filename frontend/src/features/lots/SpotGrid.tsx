import { useRef, type KeyboardEvent } from 'react';
import { Ban, CalendarClock, Car, Check, Wrench } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { Spot } from '../../api/types';
import { cn } from '../../lib/cn';
import { SPOT_STATE_META, type SpotVisualState } from './spotState';

const STATE_STYLE: Record<SpotVisualState, { tile: string; icon: LucideIcon | null }> = {
  free: { tile: 'bg-spot-free border-spot-free-border text-spot-free-fg', icon: null },
  occupied: { tile: 'bg-spot-occupied border-spot-occupied-border text-spot-occupied-fg', icon: Car },
  booked: { tile: 'bg-spot-booked border-spot-booked-border text-spot-booked-fg border-dashed', icon: CalendarClock },
  maintenance: { tile: 'bg-spot-maint border-spot-maint-border text-spot-maint-fg', icon: Wrench },
  disabled: { tile: 'stripes bg-spot-disabled border-border text-spot-disabled-fg', icon: Ban },
};

/** Spot number without the lot prefix ("A-012" → "012"), so tiles stay compact. */
function shortLabel(label: string): string {
  const dash = label.lastIndexOf('-');
  return dash >= 0 ? label.slice(dash + 1) : label;
}

export function SpotSwatch({ state, className }: { state: SpotVisualState; className?: string }) {
  const style = STATE_STYLE[state];
  const Icon = style.icon;
  return (
    <span className={cn('grid size-6 shrink-0 place-items-center rounded-md border', style.tile, className)} aria-hidden="true">
      {Icon && <Icon className="size-3.5" strokeWidth={2.25} />}
    </span>
  );
}

/** Legend: every state is shown with its swatch, icon and text, so colour is never the only signal. */
export function SpotLegend({
  states,
  meta = SPOT_STATE_META,
  counts,
}: {
  states: readonly SpotVisualState[];
  meta?: Record<SpotVisualState, { label: string; description: string }>;
  counts?: Partial<Record<SpotVisualState, number>>;
}) {
  return (
    <ul aria-label="Spot legend" className="flex flex-wrap gap-x-4 gap-y-2 text-xs text-fg-muted">
      {states.map((state) => (
        <li key={state} className="flex items-center gap-1.5" title={meta[state].description}>
          <SpotSwatch state={state} className="size-5" />
          <span>
            {meta[state].label}
            {counts?.[state] !== undefined && <span className="tabular ml-1 font-medium text-fg">{counts[state]}</span>}
          </span>
        </li>
      ))}
    </ul>
  );
}

/**
 * The lot map: one tile per spot, as an ARIA radio group. Tab enters the group, arrow keys move between
 * selectable spots, Enter/Space selects. Unselectable spots are announced with their state but skipped.
 */
export function SpotGrid({
  spots,
  getState,
  isSelectable,
  selectedId,
  onSelect,
  label,
  meta = SPOT_STATE_META,
}: {
  spots: readonly Spot[];
  getState: (spot: Spot) => SpotVisualState;
  isSelectable: (spot: Spot, state: SpotVisualState) => boolean;
  selectedId: number | null;
  onSelect: (spot: Spot) => void;
  label: string;
  meta?: Record<SpotVisualState, { label: string; description: string }>;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const items = spots.map((spot) => {
    const state = getState(spot);
    return { spot, state, selectable: isSelectable(spot, state) };
  });
  const selectableIds = items.filter((i) => i.selectable).map((i) => i.spot.id);
  const focusId = selectedId !== null && selectableIds.includes(selectedId) ? selectedId : (selectableIds[0] ?? null);

  function columns(): number {
    const tiles = containerRef.current?.querySelectorAll<HTMLElement>('[data-spot-id]');
    if (!tiles || tiles.length === 0) return 1;
    const firstTop = tiles[0]?.offsetTop ?? 0;
    let count = 0;
    for (const tile of tiles) {
      if (tile.offsetTop !== firstTop) break;
      count++;
    }
    return Math.max(count, 1);
  }

  function focusSpot(id: number) {
    containerRef.current?.querySelector<HTMLElement>(`[data-spot-id="${id}"]`)?.focus();
  }

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const step =
      event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowDown' ? columns() : event.key === 'ArrowUp' ? -columns() : 0;
    if (step === 0) return;
    event.preventDefault();
    // walk in the pressed direction until a selectable spot is found
    for (let i = index + step; i >= 0 && i < items.length; i += step) {
      const candidate = items[i];
      if (candidate?.selectable) {
        onSelect(candidate.spot);
        focusSpot(candidate.spot.id);
        return;
      }
    }
  }

  return (
    <div
      ref={containerRef}
      role="radiogroup"
      aria-label={label}
      className="grid grid-cols-[repeat(auto-fill,minmax(3.5rem,1fr))] gap-1.5 sm:grid-cols-[repeat(auto-fill,minmax(4rem,1fr))] sm:gap-2"
    >
      {items.map(({ spot, state, selectable }, index) => {
        const selected = spot.id === selectedId;
        const style = STATE_STYLE[state];
        const Icon = selected ? Check : style.icon;
        return (
          <button
            key={spot.id}
            type="button"
            role="radio"
            data-spot-id={spot.id}
            data-state={state}
            aria-checked={selected}
            aria-disabled={!selectable || undefined}
            aria-label={`Spot ${spot.label}, ${meta[state].label}`}
            tabIndex={spot.id === focusId ? 0 : -1}
            onClick={() => {
              if (selectable) onSelect(spot);
            }}
            onKeyDown={(event) => onKeyDown(event, index)}
            className={cn(
              'relative flex h-14 flex-col items-center justify-center gap-0.5 rounded-lg border text-xs font-semibold transition-[transform,box-shadow] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
              selected ? 'border-accent bg-accent text-accent-fg shadow-pop ring-2 ring-accent ring-offset-2 ring-offset-surface' : style.tile,
              selectable ? 'cursor-pointer hover:-translate-y-0.5 hover:shadow-card' : 'cursor-not-allowed',
            )}
          >
            <span className="tabular">{shortLabel(spot.label)}</span>
            <span className="grid h-4 place-items-center" aria-hidden="true">
              {Icon ? <Icon className="size-3.5" strokeWidth={2.25} /> : <span className="size-1.5 rounded-full bg-current opacity-60" />}
            </span>
          </button>
        );
      })}
    </div>
  );
}
