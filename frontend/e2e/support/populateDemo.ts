import type { APIRequestContext } from '@playwright/test';

/**
 * Final touches for the screenshots. The `demo` profile already seeds the drivers, two months of history and the
 * bookings for now and the coming days (DemoHistorySeeder), so this only makes the live state visible straight away:
 * cars arrive at most spots that are booked right now (the sensor simulator would get there within a few minutes),
 * one car parks without a booking, and the demo manager takes two spots out of service. Safe to re-run.
 */

const DEMO_PASSWORD = 'Demo#2026';
const SENSOR_KEY = process.env.SENSOR_API_KEY ?? 'dev-sensor-key';

type Spot = { id: number; label: string; enabled: boolean; maintenanceRequired: boolean; occupied: boolean; bookable: boolean };
type Lot = { id: number; name: string };

/** Sensor ids follow the spot label: `A-001` → `SNS-A-001`, `L12-001` → `SNS-L12-001`. */
const sensorId = (spot: Spot) => `SNS-${spot.label}`;

async function login(api: APIRequestContext, email: string, password: string): Promise<string | null> {
  const res = await api.post('/api/auth/login', { data: { email, password } });
  if (!res.ok()) return null;
  return ((await res.json()) as { accessToken: string }).accessToken;
}

export async function populateDemo(api: APIRequestContext): Promise<void> {
  const lots = (await (await api.get('/api/lots')).json()) as Lot[];
  const now = new Date();
  const soon = new Date(now.getTime() + 30 * 60_000);
  const timestamp = now.toISOString();
  const readings: { sensorId: string; occupied: boolean; timestamp: string }[] = [];
  const allSpots: Spot[] = [];
  let stray: Spot | undefined;

  for (const lot of lots) {
    const spots = (await (await api.get(`/api/lots/${lot.id}/spots`)).json()) as Spot[];
    allSpots.push(...spots);
    const avail = await api.get(`/api/lots/${lot.id}/availability`, { params: { start: now.toISOString(), end: soon.toISOString() } });
    if (!avail.ok()) continue;
    const free = new Set(((await avail.json()) as { spots: Spot[] }).spots.map((s) => s.id));
    // a bookable spot that isn't free for the next half hour is booked right now: most of those cars have arrived
    const booked = spots.filter((s) => s.bookable && !free.has(s.id));
    booked.forEach((s, i) => {
      if (i % 8 !== 3) readings.push({ sensorId: sensorId(s), occupied: true, timestamp });
    });
    stray = spots.findLast((s) => s.bookable && free.has(s.id) && !s.occupied) ?? stray;
  }
  // one car without a booking (an unauthorized-parking alert for the manager)
  if (stray) readings.push({ sensorId: sensorId(stray), occupied: true, timestamp });
  if (readings.length > 0) {
    await api.post('/api/sensors/readings', { headers: { 'X-Sensor-Key': SENSOR_KEY }, data: { readings: readings.slice(0, 500) } });
  }

  // the demo manager takes two spots out of service
  const managerToken = await login(api, 'manager.demo@yorku.ca', DEMO_PASSWORD);
  if (managerToken) {
    const byLabel = new Map(allSpots.map((s) => [s.label, s]));
    const changes: [string, { enabled?: boolean; maintenanceRequired?: boolean }][] = [
      ['A-012', { maintenanceRequired: true }],
      ['B-059', { enabled: false }],
    ];
    for (const [label, body] of changes) {
      const spot = byLabel.get(label);
      if (!spot) continue;
      if (body.maintenanceRequired && spot.maintenanceRequired) continue;
      if (body.enabled === false && !spot.enabled) continue;
      await api.patch(`/api/manager/spots/${spot.id}`, { headers: { Authorization: `Bearer ${managerToken}` }, data: body });
    }
  }
}
