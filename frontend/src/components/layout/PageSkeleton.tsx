import { LoadingRegion, Skeleton } from '../ui/Feedback';

/** Placeholder while a lazily loaded page's code arrives. */
export function PageSkeleton() {
  return (
    <LoadingRegion label="Loading page">
      <Skeleton className="h-8 w-56" />
      <Skeleton className="mt-3 h-4 w-96 max-w-full" />
      <div className="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Skeleton className="h-28 rounded-xl" />
        <Skeleton className="h-28 rounded-xl" />
        <Skeleton className="h-28 rounded-xl" />
        <Skeleton className="h-28 rounded-xl" />
      </div>
      <Skeleton className="mt-6 h-72 rounded-xl" />
    </LoadingRegion>
  );
}
