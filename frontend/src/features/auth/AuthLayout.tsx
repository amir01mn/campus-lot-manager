import type { ReactNode } from 'react';
import { CalendarClock, Gauge, Radio } from 'lucide-react';
import { Logo } from '../../components/layout/Logo';

const highlights = [
  { icon: Radio, title: 'Live spot sensors', text: 'See which spaces are free right now, updated every few seconds.' },
  { icon: CalendarClock, title: 'Book by the hour', text: 'Reserve a spot for your class or shift with an instant price quote.' },
  { icon: Gauge, title: 'Fair, tiered rates', text: 'Students from $5/h. Your tier comes from the university directory.' },
];

/** Split layout for the sign-in and registration pages: brand panel on wide screens, form on the right. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-[calc(100dvh-4rem)] lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
      <aside className="relative hidden overflow-hidden bg-accent px-10 py-12 text-white lg:flex lg:flex-col lg:justify-between dark:bg-[oklch(0.3_0.12_274)]">
        <div
          aria-hidden="true"
          className="absolute inset-0 opacity-25 [background-image:radial-gradient(circle_at_20%_20%,white_0,transparent_45%),radial-gradient(circle_at_80%_70%,white_0,transparent_40%)]"
        />
        <div
          aria-hidden="true"
          className="absolute inset-0 opacity-[0.08] [background-image:linear-gradient(white_1px,transparent_1px),linear-gradient(90deg,white_1px,transparent_1px)] [background-size:40px_40px]"
        />
        <div className="relative">
          <Logo inverted />
        </div>
        <div className="relative max-w-md">
          <h2 className="text-3xl leading-tight font-semibold tracking-tight">Campus parking without the circling.</h2>
          <ul className="mt-8 space-y-5">
            {highlights.map(({ icon: Icon, title, text }) => (
              <li key={title} className="flex gap-3.5">
                <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-white/15 ring-1 ring-white/25">
                  <Icon className="size-[18px]" aria-hidden="true" />
                </span>
                <div>
                  <p className="font-medium">{title}</p>
                  <p className="text-sm text-white/80">{text}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
        <p className="relative text-sm text-white/70">York University · Keele &amp; Glendon campuses</p>
      </aside>
      <div className="flex items-start justify-center px-4 py-10 sm:items-center sm:px-8">
        <div className="w-full max-w-md">{children}</div>
      </div>
    </div>
  );
}
