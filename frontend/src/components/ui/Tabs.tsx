import { useRef, type KeyboardEvent, type ReactNode } from 'react';
import { cn } from '../../lib/cn';

export type TabItem<K extends string> = { key: K; label: ReactNode; count?: number };

/** WAI-ARIA tabs: arrow keys move between tabs, the panel is labelled by the selected tab. */
export function Tabs<K extends string>({
  items,
  value,
  onChange,
  idBase,
  label,
}: {
  items: readonly TabItem<K>[];
  value: K;
  onChange: (key: K) => void;
  idBase: string;
  label: string;
}) {
  const refs = useRef(new Map<K, HTMLButtonElement>());

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    let next = -1;
    if (event.key === 'ArrowRight') next = (index + 1) % items.length;
    else if (event.key === 'ArrowLeft') next = (index - 1 + items.length) % items.length;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = items.length - 1;
    if (next < 0) return;
    event.preventDefault();
    const item = items[next];
    if (!item) return;
    onChange(item.key);
    refs.current.get(item.key)?.focus();
  }

  return (
    <div
      role="tablist"
      aria-label={label}
      className="flex gap-1 overflow-x-auto rounded-xl border border-border bg-surface-muted p-1"
    >
      {items.map((item, index) => {
        const selected = item.key === value;
        return (
          <button
            key={item.key}
            ref={(el) => {
              if (el) refs.current.set(item.key, el);
              else refs.current.delete(item.key);
            }}
            type="button"
            role="tab"
            id={`${idBase}-tab-${item.key}`}
            aria-selected={selected}
            aria-controls={`${idBase}-panel`}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(item.key)}
            onKeyDown={(event) => onKeyDown(event, index)}
            className={cn(
              'flex shrink-0 items-center gap-2 rounded-lg px-3.5 py-1.5 text-sm font-medium transition-colors',
              selected ? 'bg-surface text-fg shadow-card' : 'text-fg-muted hover:text-fg',
            )}
          >
            {item.label}
            {item.count !== undefined && (
              <span
                className={cn(
                  'tabular rounded-full px-1.5 text-xs',
                  selected ? 'bg-accent-soft text-accent-soft-fg' : 'bg-surface-sunken text-fg-subtle',
                )}
              >
                {item.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}

export function TabPanel({ idBase, value, children }: { idBase: string; value: string; children: ReactNode }) {
  return (
    <div role="tabpanel" id={`${idBase}-panel`} aria-labelledby={`${idBase}-tab-${value}`} tabIndex={0} className="focus-visible:outline-none">
      {children}
    </div>
  );
}
