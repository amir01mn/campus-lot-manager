import { addDays, campusToday, campusToIso } from '../../lib/time';

/** An inclusive range of campus calendar days (`YYYY-MM-DD`). */
export type DayRange = { fromDate: string; toDate: string };

export const PRESETS = [
  { key: '7d', label: '7 days', days: 7 },
  { key: '30d', label: '30 days', days: 30 },
  { key: '90d', label: '90 days', days: 90 },
] as const;

export type PresetKey = (typeof PRESETS)[number]['key'];

/** The last `days` campus days, today included. */
export function presetRange(days: number, now: Date = new Date()): DayRange {
  const today = campusToday(now);
  return { fromDate: addDays(today, -(days - 1)), toDate: today };
}

/** Converts an inclusive day range to the API's half-open instant range `[from, to)`. */
export function toInstantRange(range: DayRange): { from: string; to: string } {
  return { from: campusToIso(range.fromDate, '00:00'), to: campusToIso(addDays(range.toDate, 1), '00:00') };
}
