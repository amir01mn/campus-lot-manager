/** Licence plate rule shared with the backend: 2–10 of A–Z, 0–9, space or hyphen after upper-casing. */
export const PLATE_PATTERN = /^[A-Z0-9 -]{2,10}$/;

export function normalizePlate(raw: string): string {
  return raw.trim().toUpperCase().replace(/\s+/g, ' ');
}
