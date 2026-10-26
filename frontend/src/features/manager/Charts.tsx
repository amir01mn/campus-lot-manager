import type { ReactNode } from 'react';
import { Bar, BarChart, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { DailyBookings, LotUtilisation, UserTypeRevenue } from '../../api/types';
import { formatMoney, formatMoneyCompact, formatPercent, USER_TYPE_LABEL } from '../../lib/format';
import { formatCalendarDay } from '../../lib/time';

/*
 * Every chart here has a single series, so one accent hue, no legend (the card title names it), recessive
 * grid and axes, a hover tooltip, and a visually hidden table with the same numbers for screen readers.
 */
const AXIS = { fontSize: 12, fill: 'var(--chart-axis)' };
const SERIES = 'var(--chart-1)';

function ChartTooltip({ active, label, value }: { active?: boolean; label?: ReactNode; value?: ReactNode }) {
  if (!active) return null;
  return (
    <div className="rounded-lg border border-border bg-surface px-3 py-2 text-xs shadow-pop">
      <p className="text-fg-muted">{label}</p>
      <p className="tabular mt-0.5 text-sm font-semibold text-fg">{value}</p>
    </div>
  );
}

function HiddenTable({ caption, headers, rows }: { caption: string; headers: [string, string]; rows: [string, string][] }) {
  return (
    <table className="sr-only">
      <caption>{caption}</caption>
      <thead>
        <tr>
          <th scope="col">{headers[0]}</th>
          <th scope="col">{headers[1]}</th>
        </tr>
      </thead>
      <tbody>
        {rows.map(([a, b]) => (
          <tr key={a}>
            <th scope="row">{a}</th>
            <td>{b}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function BookingsPerDayChart({ data }: { data: DailyBookings[] }) {
  const rows = data.map((d) => ({ day: formatCalendarDay(d.date), count: d.count }));
  const tickEvery = Math.max(Math.ceil(rows.length / 10), 1);
  return (
    <>
      <div className="h-64" aria-hidden="true">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barCategoryGap={rows.length > 45 ? 1 : '20%'}>
            <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
            <XAxis dataKey="day" tick={AXIS} tickLine={false} axisLine={false} interval={tickEvery - 1} minTickGap={8} />
            <YAxis allowDecimals={false} tick={AXIS} tickLine={false} axisLine={false} width={40} />
            <Tooltip
              cursor={{ fill: 'var(--surface-sunken)' }}
              content={({ active, label, payload }) => (
                <ChartTooltip active={active} label={label as ReactNode} value={`${String(payload[0]?.value ?? 0)} bookings`} />
              )}
            />
            <Bar dataKey="count" fill={SERIES} radius={[4, 4, 0, 0]} maxBarSize={28} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <HiddenTable caption="Bookings per day" headers={['Day', 'Bookings']} rows={rows.map((r) => [r.day, String(r.count)])} />
    </>
  );
}

export function RevenueByUserTypeChart({ data }: { data: UserTypeRevenue[] }) {
  const rows = data.map((d) => ({ type: USER_TYPE_LABEL[d.userType], revenue: d.revenue }));
  return (
    <>
      <div className="h-56" aria-hidden="true">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 64, bottom: 0, left: 8 }} barCategoryGap="28%">
            <CartesianGrid horizontal={false} stroke="var(--chart-grid)" />
            <XAxis type="number" tick={AXIS} tickLine={false} axisLine={false} tickFormatter={(v: number) => formatMoneyCompact(v)} />
            <YAxis type="category" dataKey="type" tick={AXIS} tickLine={false} axisLine={false} width={64} />
            <Tooltip
              cursor={{ fill: 'var(--surface-sunken)' }}
              content={({ active, label, payload }) => (
                <ChartTooltip active={active} label={label as ReactNode} value={formatMoney(Number(payload[0]?.value ?? 0))} />
              )}
            />
            <Bar dataKey="revenue" fill={SERIES} radius={[0, 4, 4, 0]} isAnimationActive={false}>
              <LabelList
                dataKey="revenue"
                position="right"
                className="tabular"
                style={{ fontSize: 12, fill: 'var(--fg-muted)' }}
                formatter={(v: unknown) => formatMoney(Number(v))}
              />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <HiddenTable caption="Revenue by user type" headers={['User type', 'Revenue']} rows={rows.map((r) => [r.type, formatMoney(r.revenue)])} />
    </>
  );
}

export function LotUtilisationChart({ data }: { data: LotUtilisation[] }) {
  const rows = data.map((d) => ({ lot: d.lotName.split(' — ')[0] ?? d.lotName, full: d.lotName, percent: d.utilisationPercent, hours: d.bookedHours }));
  // a "nice" axis: 0 to the next multiple of 5 (at least 10%), in five steps
  const top = Math.max(10, Math.ceil(Math.max(0, ...rows.map((r) => r.percent)) / 5) * 5);
  const ticks = [0, 1, 2, 3, 4, 5].map((i) => Math.round(((top * i) / 5) * 10) / 10);
  return (
    <>
      <div style={{ height: Math.max(rows.length * 44 + 32, 140) }} aria-hidden="true">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 56, bottom: 0, left: 8 }} barCategoryGap="30%">
            <CartesianGrid horizontal={false} stroke="var(--chart-grid)" />
            <XAxis type="number" domain={[0, top]} ticks={ticks} tick={AXIS} tickLine={false} axisLine={false} tickFormatter={(v: number) => `${v}%`} />
            <YAxis type="category" dataKey="lot" tick={AXIS} tickLine={false} axisLine={false} width={64} />
            <Tooltip
              cursor={{ fill: 'var(--surface-sunken)' }}
              content={({ active, payload }) => {
                const row = payload[0]?.payload as (typeof rows)[number] | undefined;
                return (
                  <ChartTooltip
                    active={active}
                    label={row?.full}
                    value={row ? `${formatPercent(row.percent)} · ${row.hours.toLocaleString('en-CA')} booked hours` : ''}
                  />
                );
              }}
            />
            <Bar dataKey="percent" fill={SERIES} radius={[0, 4, 4, 0]} isAnimationActive={false}>
              <LabelList dataKey="percent" position="right" style={{ fontSize: 12, fill: 'var(--fg-muted)' }} formatter={(v: unknown) => formatPercent(Number(v))} />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <HiddenTable caption="Utilisation per lot" headers={['Lot', 'Utilisation']} rows={rows.map((r) => [r.full, formatPercent(r.percent)])} />
    </>
  );
}
