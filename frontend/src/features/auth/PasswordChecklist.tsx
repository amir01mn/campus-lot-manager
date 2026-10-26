import { Check, Circle, X } from 'lucide-react';
import { cn } from '../../lib/cn';
import { passwordRules } from './passwordPolicy';

/** Live password policy feedback; every rule shows an icon and text, not only colour. */
export function PasswordChecklist({ password, id }: { password: string; id?: string }) {
  const rules = passwordRules(password);
  const metCount = rules.filter((r) => r.met).length;
  return (
    <div id={id} className="rounded-lg border border-border bg-surface-muted px-3 py-2.5">
      <p className="sr-only" aria-live="polite">
        {metCount} of {rules.length} password requirements met
      </p>
      <ul className="grid gap-1 text-xs sm:grid-cols-2" aria-label="Password requirements">
        {rules.map((rule) => {
          const failedHard = rule.id === 'max';
          return (
            <li
              key={rule.id}
              data-met={rule.met}
              className={cn('flex items-center gap-1.5', rule.met ? 'text-success-soft-fg' : failedHard ? 'text-danger' : 'text-fg-muted')}
            >
              {rule.met ? (
                <Check className="size-3.5" aria-hidden="true" />
              ) : failedHard ? (
                <X className="size-3.5" aria-hidden="true" />
              ) : (
                <Circle className="size-3" aria-hidden="true" />
              )}
              <span>
                {rule.label}
                <span className="sr-only">{rule.met ? ' (met)' : ' (not met)'}</span>
              </span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
