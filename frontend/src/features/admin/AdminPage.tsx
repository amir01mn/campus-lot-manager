import { useState, type SubmitEvent } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, Copy, KeyRound, ShieldCheck, UserPlus, Users } from 'lucide-react';
import { adminApi } from '../../api/endpoints';
import { errorMessage, fieldErrors } from '../../api/problem';
import { queryKeys } from '../../api/queryKeys';
import type { CreatedManager } from '../../api/types';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card, CardHeader } from '../../components/ui/Card';
import { Alert, EmptyState, ErrorState, LoadingRegion, Skeleton } from '../../components/ui/Feedback';
import { Field, Input } from '../../components/ui/Field';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { ROLE_LABEL, USER_TYPE_LABEL } from '../../lib/format';
import { formatDateLong } from '../../lib/time';

/** The generated password is shown exactly once, with a copy button and a warning. */
function OneTimePassword({ created, onDone }: { created: CreatedManager; onDone: () => void }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    try {
      await navigator.clipboard.writeText(created.temporaryPassword);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }
  return (
    <div className="space-y-4 p-5">
      <Alert tone="success" title={`${created.manager.firstName} ${created.manager.lastName} is now a manager`}>
        Share the temporary password below with them over a secure channel.
      </Alert>
      <div>
        <p className="mb-1.5 text-sm font-medium" id="temp-password-label">
          Temporary password
        </p>
        <div className="flex gap-2">
          <code
            aria-labelledby="temp-password-label"
            data-testid="temporary-password"
            className="flex-1 truncate rounded-lg border border-border-strong bg-surface-muted px-3 py-2.5 font-mono text-sm tracking-wide select-all"
          >
            {created.temporaryPassword}
          </code>
          <Button variant="secondary" icon={copied ? <Check className="size-4" /> : <Copy className="size-4" />} onClick={() => void copy()}>
            {copied ? 'Copied' : 'Copy'}
          </Button>
        </div>
      </div>
      <Alert tone="warning" title="This is the only time it will be shown">
        The password isn’t stored in readable form and can’t be shown again. Ask {created.manager.firstName} to change it after their first sign-in.
      </Alert>
      <Button variant="secondary" onClick={onDone}>
        Done, I’ve saved it
      </Button>
    </div>
  );
}

function CreateManagerForm() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '' });
  const create = useMutation({
    mutationFn: adminApi.createManager,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin'] }),
  });
  const errors = fieldErrors(create.error);

  if (create.data) {
    return (
      <OneTimePassword
        created={create.data}
        onDone={() => {
          create.reset();
          setForm({ firstName: '', lastName: '', email: '' });
        }}
      />
    );
  }

  function onSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    create.mutate({ firstName: form.firstName.trim(), lastName: form.lastName.trim(), email: form.email.trim() });
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4 p-5" noValidate>
      {create.isError && Object.keys(errors).length === 0 && <Alert tone="danger">{errorMessage(create.error)}</Alert>}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="First name" error={errors.firstName}>
          <Input value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} />
        </Field>
        <Field label="Last name" error={errors.lastName}>
          <Input value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} />
        </Field>
      </div>
      <Field label="Work email" error={errors.email} hint="Their pricing tier comes from the university directory (Staff if not listed).">
        <Input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
      </Field>
      <Button
        type="submit"
        icon={<UserPlus className="size-4" />}
        loading={create.isPending}
        disabled={!form.firstName.trim() || !form.lastName.trim() || !form.email.trim()}
      >
        Create manager
      </Button>
      <p className="flex items-center gap-1.5 text-xs text-fg-subtle">
        <KeyRound className="size-3.5" aria-hidden="true" /> A secure 16-character password is generated for them.
      </p>
    </form>
  );
}

export function AdminPage() {
  const [page, setPage] = useState(0);
  const managers = useQuery({
    queryKey: queryKeys.admin.managers(page),
    queryFn: () => adminApi.managers({ page, size: 10 }),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <PageHeader title="Managers" description="Only the super manager can add lot managers." />
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <Card className="overflow-hidden">
          <CardHeader title="Lot managers" description="Accounts with the manager role, sorted by name." />
          {managers.isPending ? (
            <LoadingRegion label="Loading managers">
              <div className="space-y-3 p-5">
                {[0, 1, 2].map((i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            </LoadingRegion>
          ) : managers.isError ? (
            <ErrorState error={managers.error} onRetry={() => void managers.refetch()} />
          ) : managers.data.content.length === 0 ? (
            <EmptyState icon={<Users className="size-5" />} title="No managers yet" description="Create the first one with the form." />
          ) : (
            <ul className="divide-y divide-border">
              {managers.data.content.map((m) => (
                <li key={m.id} className="flex flex-wrap items-center gap-3 px-5 py-3.5">
                  <span className="grid size-9 place-items-center rounded-full bg-accent-soft text-sm font-semibold text-accent-soft-fg" aria-hidden="true">
                    {m.firstName.charAt(0)}
                    {m.lastName.charAt(0)}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="font-medium">
                      {m.firstName} {m.lastName}
                    </p>
                    <p className="truncate text-xs text-fg-muted">{m.email}</p>
                  </div>
                  <div className="text-right text-xs text-fg-subtle">
                    <Badge tone="accent" icon={<ShieldCheck className="size-3" aria-hidden="true" />}>
                      {ROLE_LABEL[m.role]}
                    </Badge>
                    <p className="mt-1">
                      {USER_TYPE_LABEL[m.userType]} · since {formatDateLong(m.createdAt).replace(/^\w+, /, '')}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}
          {managers.data && managers.data.totalPages > 1 && (
            <div className="border-t border-border px-5 py-3">
              <Pagination
                page={managers.data.number}
                totalPages={managers.data.totalPages}
                totalElements={managers.data.totalElements}
                onPageChange={setPage}
                noun="managers"
              />
            </div>
          )}
        </Card>
        <Card>
          <CardHeader title="Add a manager" description="They can manage lots, see every booking and the dashboard." />
          <CreateManagerForm />
        </Card>
      </div>
    </>
  );
}
