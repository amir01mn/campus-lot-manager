import { Compass } from 'lucide-react';
import { ButtonLink } from '../ui/Button';
import { EmptyState } from '../ui/Feedback';

export function NotFound() {
  return (
    <EmptyState
      icon={<Compass className="size-6" />}
      title="Page not found"
      description="That page doesn’t exist or has moved."
      action={<ButtonLink to="/">Back to lots</ButtonLink>}
    />
  );
}
