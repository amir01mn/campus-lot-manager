import type { Booking, Lot, PriceQuote, Spot, Stats, UserProfile } from '../api/types';

export const student: UserProfile = {
  id: '1c5e4cfc-25ee-42e7-94a0-024158a5f16f',
  email: 'student.demo@my.yorku.ca',
  firstName: 'Sam',
  lastName: 'Student',
  userType: 'STUDENT',
  role: 'USER',
  hourlyRate: 5,
  currency: 'CAD',
  createdAt: '2026-10-01T12:00:00Z',
};

export const manager: UserProfile = {
  ...student,
  id: '9a0b5f0e-0000-4000-8000-000000000002',
  email: 'manager.demo@yorku.ca',
  firstName: 'Morgan',
  lastName: 'Manager',
  userType: 'STAFF',
  role: 'MANAGER',
  hourlyRate: 10,
};

export const lotA: Lot = {
  id: 1,
  name: 'Lot A — Keele Campus North',
  location: '4700 Keele St, Toronto, ON',
  enabled: true,
  totalSpots: 6,
  bookableSpots: 4,
  occupiedSpots: 1,
  createdAt: '2026-10-01T12:00:00Z',
};

function spot(id: number, overrides: Partial<Spot> = {}): Spot {
  return {
    id,
    lotId: 1,
    label: `A-00${id}`,
    enabled: true,
    maintenanceRequired: false,
    occupied: false,
    bookable: true,
    lastSensorUpdate: '2026-10-26T12:00:00Z',
    occupiedSince: null,
    ...overrides,
  };
}

/** One spot per visual state: 1 free, 2 occupied now, 3 booked in the window, 4 maintenance, 5 disabled, 6 free. */
export const spots: Spot[] = [
  spot(1),
  spot(2, { occupied: true, occupiedSince: '2026-10-26T11:00:00Z' }),
  spot(3),
  spot(4, { maintenanceRequired: true, bookable: false }),
  spot(5, { enabled: false, bookable: false }),
  spot(6),
];

/** Spot 3 is booked during the window, so availability lists 1, 2 and 6. */
export const availableSpotIds = [1, 2, 6];

export const quote: PriceQuote = {
  hourlyRate: 5,
  billableHours: 2,
  total: 10,
  deposit: 5,
  finalCharge: 5,
  currency: 'CAD',
};

export function booking(overrides: Partial<Booking> = {}): Booking {
  return {
    id: '7d3f583d-bbfe-49e8-9800-460a08a7a170',
    referenceCode: 'CP-YKUH23',
    status: 'CONFIRMED',
    userId: student.id,
    spot: { id: 30, label: 'A-030', lotId: 1, lotName: lotA.name },
    licencePlate: 'DEMO 942',
    startTime: '2026-10-27T13:00:00Z',
    endTime: '2026-10-27T16:00:00Z',
    paymentMethod: 'CREDIT_CARD',
    hourlyRate: 5,
    depositAmount: 5,
    totalAmount: 15,
    currency: 'CAD',
    createdAt: '2026-10-26T06:49:39Z',
    cancelledAt: null,
    completedAt: null,
    ...overrides,
  };
}

export const stats: Stats = {
  from: '2026-09-27T04:00:00Z',
  to: '2026-10-27T04:00:00Z',
  timeZone: 'America/Toronto',
  currency: 'CAD',
  totalSpots: 130,
  occupiedSpots: 52,
  occupancyPercent: 40,
  bookingsCount: 87,
  bookingsByStatus: { CONFIRMED: 21, COMPLETED: 58, CANCELLED: 8 },
  revenue: 1234.5,
  bookingsPerDay: [
    { date: '2026-10-25', count: 30 },
    { date: '2026-10-26', count: 57 },
  ],
  revenueByUserType: [
    { userType: 'STUDENT', revenue: 300 },
    { userType: 'FACULTY', revenue: 250.5 },
    { userType: 'STAFF', revenue: 284 },
    { userType: 'VISITOR', revenue: 400 },
  ],
  lotUtilisation: [
    { lotId: 1, lotName: 'Lot A — Keele Campus North', totalSpots: 40, bookedHours: 320, availableSpotHours: 28800, utilisationPercent: 1.1 },
  ],
};
