import { useState } from 'react';
import { BellOff, CheckCheck } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { EmptyState, ErrorState, LoadingRegion, Skeleton } from '../../components/ui/Feedback';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { useMarkAllRead, useMarkRead, useNotifications, useUnreadCount } from './hooks';
import { NotificationItem } from './NotificationItem';

const PAGE_SIZE = 15;

export function NotificationsPage() {
  const [page, setPage] = useState(0);
  const list = useNotifications(page, PAGE_SIZE);
  const unread = useUnreadCount(true);
  const markRead = useMarkRead();
  const markAll = useMarkAllRead();

  return (
    <>
      <PageHeader
        title="Notifications"
        description="Booking updates and, for managers, sensor alerts."
        actions={
          <Button
            variant="secondary"
            icon={<CheckCheck className="size-4" />}
            disabled={!unread.data}
            loading={markAll.isPending}
            onClick={() => markAll.mutate()}
          >
            Mark all read
          </Button>
        }
      />
      <Card className="overflow-hidden">
        {list.isPending ? (
          <LoadingRegion label="Loading notifications">
            <div className="divide-y divide-border">
              {[0, 1, 2, 3].map((i) => (
                <div key={i} className="flex gap-3 px-5 py-4">
                  <Skeleton className="size-8 rounded-full" />
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-3.5 w-40" />
                    <Skeleton className="h-3.5 w-3/4" />
                  </div>
                </div>
              ))}
            </div>
          </LoadingRegion>
        ) : list.isError ? (
          <ErrorState error={list.error} onRetry={() => void list.refetch()} />
        ) : list.data.content.length === 0 ? (
          <EmptyState icon={<BellOff className="size-5" />} title="No notifications yet" description="Booking confirmations and alerts will show up here." />
        ) : (
          <div className="divide-y divide-border">
            {list.data.content.map((n) => (
              <NotificationItem key={n.id} notification={n} onMarkRead={(id) => markRead.mutate(id)} />
            ))}
          </div>
        )}
      </Card>
      {list.data && (
        <div className="mt-4">
          <Pagination
            page={list.data.number}
            totalPages={list.data.totalPages}
            totalElements={list.data.totalElements}
            onPageChange={setPage}
            noun="notifications"
          />
        </div>
      )}
    </>
  );
}
