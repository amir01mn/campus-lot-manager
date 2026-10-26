import type { PriceQuote } from '../../api/types';
import { Skeleton } from '../../components/ui/Feedback';
import { formatMoney } from '../../lib/format';

/** Quote breakdown: rate × billable hours, deposit now, the rest at checkout. */
export function PriceBreakdown({ quote, loading }: { quote: PriceQuote | undefined; loading?: boolean }) {
  if (!quote) {
    return loading ? (
      <div className="space-y-2" aria-busy="true" aria-label="Loading price">
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-5/6" />
        <Skeleton className="h-6 w-1/2" />
      </div>
    ) : null;
  }
  const hours = quote.billableHours === 1 ? '1 hour' : `${quote.billableHours} hours`;
  return (
    <dl aria-label="Price quote" className="space-y-1.5 text-sm" aria-busy={loading ? true : undefined}>
      <div className="flex justify-between gap-3 text-fg-muted">
        <dt>
          {formatMoney(quote.hourlyRate)}/h × {hours}
        </dt>
        <dd className="tabular">{formatMoney(quote.total)}</dd>
      </div>
      <div className="flex justify-between gap-3 text-fg-muted">
        <dt>Deposit charged now</dt>
        <dd className="tabular">{formatMoney(quote.deposit)}</dd>
      </div>
      <div className="flex justify-between gap-3 text-fg-muted">
        <dt>Charged at checkout</dt>
        <dd className="tabular">{formatMoney(quote.finalCharge)}</dd>
      </div>
      <div className="flex items-baseline justify-between gap-3 border-t border-border pt-2">
        <dt className="font-medium text-fg">Total</dt>
        <dd className="tabular text-xl font-semibold text-fg" data-testid="quote-total">
          {formatMoney(quote.total)}
        </dd>
      </div>
    </dl>
  );
}
