import type { APIRequestContext } from '@playwright/test';
import { campusDate, randomInt } from './campus';

/**
 * Makes the demo look busy for screenshots, using only the public API (nothing the app couldn't do itself):
 * registers extra drivers, books spots for right now and the coming days, reports sensor readings for the
 * booked spots (cars arriving), and has the demo manager take a few spots out of service.
 * Safe to re-run: existing accounts are reused and conflicting bookings are skipped.
 */

const DEMO_PASSWORD = 'Demo#2026';
const DRIVER_PASSWORD = 'Driver#2026';
const SENSOR_KEY = process.env.SENSOR_API_KEY ?? 'dev-sensor-key';

const DIRECTORY = [
  'aisha.rahman@my.yorku.ca',
  'daniel.chen@my.yorku.ca',
  'priya.patel@my.yorku.ca',
  'lucas.martin@my.yorku.ca',
  'sofia.rossi@my.yorku.ca',
  'omar.haddad@my.yorku.ca',
  'emily.nguyen@my.yorku.ca',
  'margaret.wong@yorku.ca',
  'james.okafor@yorku.ca',
  'elena.petrova@yorku.ca',
  'robert.singh@yorku.ca',
  'hannah.kim@yorku.ca',
  'carlos.mendes@yorku.ca',
  'fatima.ali@yorku.ca',
  'george.brown@yorku.ca',
  'linda.tremblay@yorku.ca',
  'kevin.osei@yorku.ca',
];
const FIRST = ['noah', 'olivia', 'liam', 'emma', 'ethan', 'ava', 'mason', 'mia', 'logan', 'zoe', 'jack', 'chloe', 'owen', 'leah', 'ryan', 'nora'];
const LAST = ['williams', 'tremblay', 'gagnon', 'roy', 'macdonald', 'campbell', 'kowalski', 'ahmed', 'silva', 'ng', 'obi', 'larsen'];
const VISITORS = Array.from({ length: 48 }, (_, i) => `${FIRST[i % FIRST.length] ?? 'sam'}.${LAST[(i * 5) % LAST.length] ?? 'lee'}@example.com`);

type Spot = { id: number; label: string; sensorId?: string; enabled: boolean; maintenanceRequired: boolean };
type Lot = { id: number; name: string };

function titleCase(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function namesFor(email: string): { firstName: string; lastName: string } {
  const local = email.split('@')[0] ?? 'driver';
  const [first = 'Driver', last = 'Guest'] = local.split('.');
  return { firstName: titleCase(first.replace(/\d+/g, '')) || 'Driver', lastName: titleCase(last) };
}

function plate(): string {
  const letters = 'ABCDEFGHJKLMNPRSTVWXYZ';
  const pick = () => letters[randomInt(0, letters.length - 1)] ?? 'A';
  return `${pick()}${pick()}${pick()}${pick()} ${randomInt(100, 999)}`;
}

async function login(api: APIRequestContext, email: string, password: string): Promise<string | null> {
  const res = await api.post('/api/auth/login', { data: { email, password } });
  if (!res.ok()) return null;
  return ((await res.json()) as { accessToken: string }).accessToken;
}

async function ensureUser(api: APIRequestContext, email: string): Promise<string | null> {
  await api.post('/api/auth/register', { data: { ...namesFor(email), email, password: DRIVER_PASSWORD } });
  return login(api, email, DRIVER_PASSWORD);
}

async function book(api: APIRequestContext, token: string, lots: Lot[], start: Date, hours: number): Promise<number | null> {
  const end = new Date(start.getTime() + hours * 3_600_000);
  for (let attempt = 0; attempt < 3; attempt++) {
    const lot = lots[randomInt(0, lots.length - 1)];
    if (!lot) return null;
    const avail = await api.get(`/api/lots/${lot.id}/availability`, { params: { start: start.toISOString(), end: end.toISOString() } });
    if (!avail.ok()) continue;
    const free = ((await avail.json()) as { spots: Spot[] }).spots;
    const spot = free[randomInt(0, free.length - 1)];
    if (!spot) continue;
    const res = await api.post('/api/bookings', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        spotId: spot.id,
        plate: plate(),
        start: start.toISOString(),
        end: end.toISOString(),
        paymentMethod: (['CREDIT_CARD', 'DEBIT_CARD', 'PAYPAL'] as const)[randomInt(0, 2)],
      },
    });
    if (res.ok()) return spot.id;
    if (res.status() === 409 && attempt > 0) return null; // probably the user already has a booking then
  }
  return null;
}

/** Campus wall-clock hour on `daysAhead` as a Date (approximate offset is fine for demo data). */
function campusHour(daysAhead: number, hour: number): Date {
  const date = campusDate(daysAhead);
  // Toronto is UTC-4 (EDT) or UTC-5 (EST); try -4 and correct with Intl
  const guess = new Date(`${date}T${String(hour).padStart(2, '0')}:00:00-04:00`);
  const tzHour = Number(new Intl.DateTimeFormat('en-US', { timeZone: 'America/Toronto', hour: '2-digit', hourCycle: 'h23' }).format(guess));
  return new Date(guess.getTime() + (hour - tzHour) * 3_600_000);
}

export async function populateDemo(api: APIRequestContext): Promise<void> {
  const lots = (await (await api.get('/api/lots')).json()) as Lot[];
  const now = new Date(Math.floor(Date.now() / 60_000) * 60_000);
  const occupiedSpotIds = new Set<number>();

  // drivers parked right now, plus bookings later today and over the next few days
  for (const email of [...DIRECTORY, ...VISITORS]) {
    const token = await ensureUser(api, email);
    if (!token) continue;
    const spotId = await book(api, token, lots, now, randomInt(2, 6) + 0.5 * randomInt(0, 1));
    if (spotId !== null) occupiedSpotIds.add(spotId);
    await book(api, token, lots, campusHour(1, randomInt(7, 16)), randomInt(1, 4));
    if (Math.random() < 0.5) await book(api, token, lots, campusHour(2, randomInt(7, 16)), randomInt(1, 3));
  }

  // the demo student: one booking active now and one later this week (one tomorrow is seeded)
  const studentToken = await login(api, 'student.demo@my.yorku.ca', DEMO_PASSWORD);
  if (studentToken) {
    const spotId = await book(api, studentToken, lots, now, 2);
    if (spotId !== null) occupiedSpotIds.add(spotId);
    await book(api, studentToken, lots, campusHour(3, 14), 3);
  }

  // cars arrive at most booked spots; a couple park without a booking (unauthorized alerts)
  const spotsByLot = await Promise.all(lots.map(async (lot) => (await (await api.get(`/api/lots/${lot.id}/spots`)).json()) as Spot[]));
  const allSpots = spotsByLot.flat();
  const sensorIds = new Map(allSpots.map((s) => [s.id, `SNS-${s.label}`]));
  const readings = [...occupiedSpotIds]
    .filter(() => Math.random() < 0.9)
    .map((id) => ({ sensorId: sensorIds.get(id) ?? '', occupied: true, timestamp: new Date().toISOString() }));
  const strays = allSpots.filter((s) => !occupiedSpotIds.has(s.id)).slice(5, 8);
  for (const s of strays) readings.push({ sensorId: `SNS-${s.label}`, occupied: true, timestamp: new Date().toISOString() });
  if (readings.length > 0) {
    await api.post('/api/sensors/readings', { headers: { 'X-Sensor-Key': SENSOR_KEY }, data: { readings } });
  }

  // the demo manager takes a few spots out of service
  const managerToken = await login(api, 'manager.demo@yorku.ca', DEMO_PASSWORD);
  if (managerToken) {
    const byLabel = new Map(allSpots.map((s) => [s.label, s]));
    const changes: [string, { enabled?: boolean; maintenanceRequired?: boolean }][] = [
      ['A-012', { maintenanceRequired: true }],
      ['A-027', { maintenanceRequired: true }],
      ['A-039', { enabled: false }],
      ['A-040', { enabled: false }],
      ['B-033', { maintenanceRequired: true }],
      ['B-059', { enabled: false }],
      ['B-060', { enabled: false }],
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
