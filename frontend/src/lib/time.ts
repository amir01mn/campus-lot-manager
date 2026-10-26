/**
 * Campus-time helpers. The campus is in America/Toronto; every time shown or entered in the UI is campus
 * time, whatever the browser's own zone is. Instants travel to and from the API as ISO-8601 UTC strings.
 */
export const CAMPUS_TZ = 'America/Toronto';
export const LOCALE = 'en-CA';

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;

const partsFormatter = new Intl.DateTimeFormat('en-US', {
  timeZone: CAMPUS_TZ,
  hourCycle: 'h23',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});

type CampusParts = { year: number; month: number; day: number; hour: number; minute: number; second: number };

function campusParts(instant: number): CampusParts {
  const values: Record<string, number> = {};
  for (const part of partsFormatter.formatToParts(new Date(instant))) {
    if (part.type !== 'literal') values[part.type] = Number(part.value);
  }
  return {
    year: values.year ?? 0,
    month: values.month ?? 1,
    day: values.day ?? 1,
    hour: values.hour ?? 0,
    minute: values.minute ?? 0,
    second: values.second ?? 0,
  };
}

/** Campus UTC offset at `instant`, in milliseconds (e.g. -4h in summer, -5h in winter). */
function campusOffset(instant: number): number {
  const p = campusParts(instant);
  const asUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  return asUtc - Math.floor(instant / 1000) * 1000;
}

const pad = (n: number) => String(n).padStart(2, '0');

/** Converts a campus wall-clock date (`YYYY-MM-DD`) and time (`HH:mm`) to an ISO-8601 UTC instant. */
export function campusToIso(date: string, time: string): string {
  const [y = 0, m = 1, d = 1] = date.split('-').map(Number);
  const [hh = 0, mm = 0] = time.split(':').map(Number);
  const wallAsUtc = Date.UTC(y, m - 1, d, hh, mm);
  const firstGuess = wallAsUtc - campusOffset(wallAsUtc);
  // Re-check the offset at the guessed instant so dates on the other side of a DST change are right.
  const secondOffset = campusOffset(firstGuess);
  return new Date(wallAsUtc - secondOffset).toISOString();
}

/** Campus wall-clock date and time of an instant. */
export function toCampus(iso: string | number | Date): { date: string; time: string } {
  const p = campusParts(new Date(iso).getTime());
  return { date: `${p.year}-${pad(p.month)}-${pad(p.day)}`, time: `${pad(p.hour)}:${pad(p.minute)}` };
}

/** Today's date on campus (`YYYY-MM-DD`). */
export function campusToday(now: Date = new Date()): string {
  return toCampus(now).date;
}

/** Adds whole days to a `YYYY-MM-DD` date. */
export function addDays(date: string, days: number): string {
  const [y = 0, m = 1, d = 1] = date.split('-').map(Number);
  const result = new Date(Date.UTC(y, m - 1, d + days));
  return result.toISOString().slice(0, 10);
}

/** Every half hour of a day as `HH:mm`. */
export const HALF_HOURS: readonly string[] = Array.from({ length: 48 }, (_, i) => `${pad(Math.floor(i / 2))}:${i % 2 ? '30' : '00'}`);

/** A booking window in campus wall-clock terms. An end at or before the start means the next day. */
export type CampusWindow = { date: string; startTime: string; endTime: string };

export function windowEndsNextDay(w: CampusWindow): boolean {
  return w.endTime <= w.startTime;
}

export function windowToIso(w: CampusWindow): { start: string; end: string } {
  const endDate = windowEndsNextDay(w) ? addDays(w.date, 1) : w.date;
  return { start: campusToIso(w.date, w.startTime), end: campusToIso(endDate, w.endTime) };
}

/** The default window: the next full campus hour, for two hours. */
export function defaultWindow(now: Date = new Date()): CampusWindow {
  const next = Math.ceil((now.getTime() + MINUTE) / HOUR) * HOUR;
  const start = toCampus(next);
  const end = toCampus(next + 2 * HOUR);
  return { date: start.date, startTime: start.time, endTime: end.time };
}

/** Validates a window against the booking rules the backend enforces (30 min – 24 h, not in the past). */
export function validateWindow(w: CampusWindow, now: Date = new Date()): string | null {
  const { start, end } = windowToIso(w);
  const startMs = Date.parse(start);
  const duration = Date.parse(end) - startMs;
  if (startMs < now.getTime() - 5 * MINUTE) return 'The start time has already passed.';
  if (duration < 30 * MINUTE) return 'Bookings must be at least 30 minutes long.';
  if (duration > 24 * HOUR) return 'Bookings can be at most 24 hours long.';
  return null;
}

const timeFmt = new Intl.DateTimeFormat(LOCALE, { timeZone: CAMPUS_TZ, hour: 'numeric', minute: '2-digit' });
const dateFmt = new Intl.DateTimeFormat(LOCALE, { timeZone: CAMPUS_TZ, weekday: 'short', month: 'short', day: 'numeric' });
const dateLongFmt = new Intl.DateTimeFormat(LOCALE, {
  timeZone: CAMPUS_TZ,
  weekday: 'long',
  year: 'numeric',
  month: 'long',
  day: 'numeric',
});
const dateShortFmt = new Intl.DateTimeFormat(LOCALE, { timeZone: 'UTC', month: 'short', day: 'numeric' });

export function formatTime(iso: string): string {
  return timeFmt.format(new Date(iso));
}

export function formatDate(iso: string): string {
  return dateFmt.format(new Date(iso));
}

export function formatDateLong(iso: string): string {
  return dateLongFmt.format(new Date(iso));
}

export function formatDateTime(iso: string): string {
  return `${formatDate(iso)}, ${formatTime(iso)}`;
}

/** "Tue, Oct 27, 9:00 a.m. – 12:00 p.m." (end date repeated only when it differs). */
export function formatRange(startIso: string, endIso: string): string {
  const sameDay = toCampus(startIso).date === toCampus(endIso).date;
  return `${formatDateTime(startIso)} – ${sameDay ? formatTime(endIso) : formatDateTime(endIso)}`;
}

/** Formats a plain calendar date (`YYYY-MM-DD`, as in stats) without shifting it through a time zone. */
export function formatCalendarDay(date: string): string {
  return dateShortFmt.format(new Date(`${date}T00:00:00Z`));
}

/** Formats `HH:mm` as campus clock text, e.g. "9:30 a.m.". */
export function formatClock(time: string): string {
  const [h = 0, m = 0] = time.split(':').map(Number);
  return new Intl.DateTimeFormat(LOCALE, { timeZone: 'UTC', hour: 'numeric', minute: '2-digit' }).format(
    new Date(Date.UTC(2000, 0, 1, h, m)),
  );
}

export function formatDuration(ms: number): string {
  const totalMinutes = Math.round(ms / MINUTE);
  const h = Math.floor(totalMinutes / 60);
  const m = totalMinutes % 60;
  if (h === 0) return `${m} min`;
  return m === 0 ? `${h} h` : `${h} h ${m} min`;
}

const relativeFmt = new Intl.RelativeTimeFormat(LOCALE, { numeric: 'auto' });

/** "5 minutes ago", "yesterday", ... falling back to a date after a week. */
export function formatRelative(iso: string, now: Date = new Date()): string {
  const diff = Date.parse(iso) - now.getTime();
  const abs = Math.abs(diff);
  if (abs < MINUTE) return 'just now';
  if (abs < HOUR) return relativeFmt.format(Math.round(diff / MINUTE), 'minute');
  if (abs < 24 * HOUR) return relativeFmt.format(Math.round(diff / HOUR), 'hour');
  if (abs < 7 * 24 * HOUR) return relativeFmt.format(Math.round(diff / (24 * HOUR)), 'day');
  return formatDate(iso);
}

export const HOUR_MS = HOUR;
export const MINUTE_MS = MINUTE;
