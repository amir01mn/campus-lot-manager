import type { Spot } from '../../api/types';

/**
 * Visual state of a spot in the grid, in priority order:
 * - `disabled`: switched off by a manager (or its lot is closed);
 * - `maintenance`: flagged for maintenance;
 * - `booked`: someone holds a confirmed booking overlapping the selected window;
 * - `occupied`: the sensor sees a car right now, but the spot is free for the selected window;
 * - `free`: bookable and free for the window.
 */
export type SpotVisualState = 'free' | 'occupied' | 'booked' | 'maintenance' | 'disabled';

export function spotState(spot: Spot, availableIds: ReadonlySet<number> | null): SpotVisualState {
  if (!spot.enabled) return 'disabled';
  if (spot.maintenanceRequired) return 'maintenance';
  if (!spot.bookable) return 'disabled';
  if (availableIds && !availableIds.has(spot.id)) return 'booked';
  if (spot.occupied) return 'occupied';
  return 'free';
}

/** Manager view: only manager flags and the live sensor matter (there is no booking window). */
export function managerSpotState(spot: Spot): SpotVisualState {
  if (!spot.enabled) return 'disabled';
  if (spot.maintenanceRequired) return 'maintenance';
  if (spot.occupied) return 'occupied';
  return 'free';
}

export function isBookableState(state: SpotVisualState): boolean {
  return state === 'free' || state === 'occupied';
}

export type StateMeta = { label: string; description: string };

export const SPOT_STATE_META: Record<SpotVisualState, StateMeta> = {
  free: { label: 'Free', description: 'Available for your time window' },
  occupied: { label: 'Occupied now', description: 'A car is parked there now, but it is free for your window' },
  booked: { label: 'Booked', description: 'Someone has booked it during your window' },
  maintenance: { label: 'Maintenance', description: 'Out of service for maintenance' },
  disabled: { label: 'Unavailable', description: 'Closed by a lot manager' },
};
