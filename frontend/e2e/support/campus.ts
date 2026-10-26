/** Campus (America/Toronto) calendar date `days` from today, as `YYYY-MM-DD`. */
export function campusDate(days: number): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Toronto',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(Date.now() + days * 86_400_000));
  return parts; // en-CA formats as YYYY-MM-DD
}

export function randomInt(min: number, max: number): number {
  return min + Math.floor(Math.random() * (max - min + 1));
}

export const pad = (n: number) => String(n).padStart(2, '0');
