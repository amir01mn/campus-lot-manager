import { describe, expect, it } from 'vitest';
import { campusToIso, defaultWindow, formatRange, toCampus, validateWindow, windowToIso } from './time';
import { formatMoney } from './format';

describe('campus time', () => {
  it('converts campus wall-clock time to UTC in summer (EDT) and winter (EST)', () => {
    expect(campusToIso('2026-07-01', '09:00')).toBe('2026-07-01T13:00:00.000Z');
    expect(campusToIso('2026-12-01', '09:00')).toBe('2026-12-01T14:00:00.000Z');
  });

  it('round-trips through toCampus', () => {
    expect(toCampus('2026-10-27T13:00:00Z')).toEqual({ date: '2026-10-27', time: '09:00' });
  });

  it('reads an end time at or before the start as the next day', () => {
    expect(windowToIso({ date: '2026-12-01', startTime: '22:00', endTime: '01:00' })).toEqual({
      start: '2026-12-02T03:00:00.000Z',
      end: '2026-12-02T06:00:00.000Z',
    });
  });

  it('validates the window like the backend', () => {
    const now = new Date('2026-12-01T12:00:00Z'); // 7:00 a.m. campus time
    expect(validateWindow({ date: '2026-12-01', startTime: '08:00', endTime: '10:00' }, now)).toBeNull();
    expect(validateWindow({ date: '2026-12-01', startTime: '06:00', endTime: '08:00' }, now)).toMatch(/already passed/);
    expect(validateWindow({ date: '2026-12-01', startTime: '08:00', endTime: '08:00' }, now)).toBeNull(); // 24 h overnight
  });

  it('defaults to the next full hour for two hours', () => {
    expect(defaultWindow(new Date('2026-12-01T12:20:00Z'))).toEqual({ date: '2026-12-01', startTime: '08:00', endTime: '10:00' });
  });

  it('formats ranges and money for Canadian readers', () => {
    expect(formatRange('2026-10-27T13:00:00Z', '2026-10-27T16:00:00Z')).toBe('Tue, Oct 27, 9:00 a.m. – 12:00 p.m.');
    expect(formatMoney(1234.5)).toBe('$1,234.50');
  });
});
