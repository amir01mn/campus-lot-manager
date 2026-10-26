import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from './Button';

/** Prev/next pagination for Spring-style pages (zero-based `page`). */
export function Pagination({
  page,
  totalPages,
  totalElements,
  onPageChange,
  noun = 'results',
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
  noun?: string;
}) {
  if (totalElements === 0) return null;
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-3 text-sm text-fg-muted">
      <p className="tabular">
        Page {page + 1} of {Math.max(totalPages, 1)} · {totalElements} {noun}
      </p>
      <div className="flex gap-2">
        <Button
          variant="secondary"
          size="sm"
          icon={<ChevronLeft className="size-4" />}
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
        >
          Previous
        </Button>
        <Button variant="secondary" size="sm" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>
          Next
          <ChevronRight className="size-4" />
        </Button>
      </div>
    </nav>
  );
}
