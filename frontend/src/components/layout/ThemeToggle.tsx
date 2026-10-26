import { Monitor, Moon, Sun } from 'lucide-react';
import { useTheme, type ThemePreference } from '../../lib/useTheme';

const ORDER: ThemePreference[] = ['system', 'light', 'dark'];
const LABEL: Record<ThemePreference, string> = { system: 'System theme', light: 'Light theme', dark: 'Dark theme' };

/** Cycles system → light → dark. The icon shows the current choice. */
export function ThemeToggle() {
  const { preference, setPreference } = useTheme();
  const next = ORDER[(ORDER.indexOf(preference) + 1) % ORDER.length] ?? 'system';
  const Icon = preference === 'dark' ? Moon : preference === 'light' ? Sun : Monitor;
  return (
    <button
      type="button"
      onClick={() => setPreference(next)}
      className="grid size-9 place-items-center rounded-lg text-fg-muted transition-colors hover:bg-surface-muted hover:text-fg"
      aria-label={`${LABEL[preference]} (switch to ${LABEL[next].toLowerCase()})`}
      title={`${LABEL[preference]}: click for ${LABEL[next].toLowerCase()}`}
    >
      <Icon className="size-[18px]" aria-hidden="true" />
    </button>
  );
}
