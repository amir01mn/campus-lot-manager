import { useEffect, useState, type SubmitEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { BadgeCheck, CircleDollarSign, KeyRound, Mail, ShieldCheck } from 'lucide-react';
import { usersApi } from '../../api/endpoints';
import { errorMessage, fieldErrors } from '../../api/problem';
import { queryKeys } from '../../api/queryKeys';
import { Button } from '../../components/ui/Button';
import { Card, CardHeader } from '../../components/ui/Card';
import { Alert, ErrorState, LoadingRegion, Skeleton } from '../../components/ui/Feedback';
import { Field, Input } from '../../components/ui/Field';
import { PageHeader } from '../../components/ui/PageHeader';
import { useToast } from '../../components/ui/toastContext';
import { formatMoney, ROLE_LABEL, USER_TYPE_LABEL } from '../../lib/format';
import { formatDateLong } from '../../lib/time';
import { useAuth } from '../auth/authContext';
import { PasswordChecklist } from '../auth/PasswordChecklist';
import { isPasswordAcceptable } from '../auth/passwordPolicy';

function ChangePasswordForm() {
  const toast = useToast();
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const change = useMutation({ mutationFn: usersApi.changePassword });
  const errors = fieldErrors(change.error);
  const mismatch = form.confirm.length > 0 && form.confirm !== form.newPassword;
  const canSubmit = !!form.currentPassword && isPasswordAcceptable(form.newPassword) && form.confirm === form.newPassword;

  function onSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSubmit) return;
    change.mutate(
      { currentPassword: form.currentPassword, newPassword: form.newPassword },
      {
        onSuccess: () => {
          setForm({ currentPassword: '', newPassword: '', confirm: '' });
          toast({ title: 'Password changed', description: 'Use your new password next time you sign in.' });
        },
      },
    );
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4 p-5" noValidate>
      {change.isError && Object.keys(errors).length === 0 && <Alert tone="danger">{errorMessage(change.error)}</Alert>}
      <Field label="Current password" error={errors.currentPassword}>
        <Input
          type="password"
          autoComplete="current-password"
          value={form.currentPassword}
          onChange={(e) => setForm({ ...form, currentPassword: e.target.value })}
        />
      </Field>
      <div className="space-y-2">
        <Field label="New password" error={errors.newPassword}>
          <Input type="password" autoComplete="new-password" value={form.newPassword} onChange={(e) => setForm({ ...form, newPassword: e.target.value })} />
        </Field>
        <PasswordChecklist password={form.newPassword} />
      </div>
      <Field label="Confirm new password" error={mismatch ? 'The passwords don’t match.' : null}>
        <Input type="password" autoComplete="new-password" value={form.confirm} onChange={(e) => setForm({ ...form, confirm: e.target.value })} />
      </Field>
      <Button type="submit" loading={change.isPending} disabled={!canSubmit} icon={<KeyRound className="size-4" />}>
        Change password
      </Button>
    </form>
  );
}

export function ProfilePage() {
  const { updateUser } = useAuth();
  const me = useQuery({ queryKey: queryKeys.me, queryFn: usersApi.me });

  useEffect(() => {
    if (me.data) updateUser(me.data);
  }, [me.data, updateUser]);

  return (
    <>
      <PageHeader title="Profile" description="Your account, pricing tier and password." />
      <div className="grid items-start gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader title="Account" description="Your tier is set from the university directory and can’t be changed here." />
          {me.isPending ? (
            <LoadingRegion label="Loading profile">
              <div className="space-y-3 p-5">
                <Skeleton className="h-6 w-48" />
                <Skeleton className="h-16 w-full" />
                <Skeleton className="h-16 w-full" />
              </div>
            </LoadingRegion>
          ) : me.isError ? (
            <ErrorState error={me.error} onRetry={() => void me.refetch()} />
          ) : (
            <div className="p-5">
              <div className="flex items-center gap-4">
                <span className="grid size-14 place-items-center rounded-full bg-accent text-lg font-semibold text-accent-fg" aria-hidden="true">
                  {me.data.firstName.charAt(0)}
                  {me.data.lastName.charAt(0)}
                </span>
                <div className="min-w-0">
                  <p className="text-lg font-semibold">
                    {me.data.firstName} {me.data.lastName}
                  </p>
                  <p className="flex items-center gap-1.5 truncate text-sm text-fg-muted">
                    <Mail className="size-3.5" aria-hidden="true" /> {me.data.email}
                  </p>
                </div>
              </div>
              <dl className="mt-6 grid gap-3 sm:grid-cols-3">
                {[
                  { icon: BadgeCheck, label: 'User type', value: USER_TYPE_LABEL[me.data.userType] },
                  { icon: CircleDollarSign, label: 'Hourly rate', value: `${formatMoney(me.data.hourlyRate)}/h` },
                  { icon: ShieldCheck, label: 'Role', value: ROLE_LABEL[me.data.role] },
                ].map(({ icon: Icon, label, value }) => (
                  <div key={label} className="rounded-lg border border-border bg-surface-muted p-3">
                    <dt className="flex items-center gap-1.5 text-xs text-fg-muted">
                      <Icon className="size-3.5" aria-hidden="true" /> {label}
                    </dt>
                    <dd className="mt-1 font-semibold">{value}</dd>
                  </div>
                ))}
              </dl>
              <p className="mt-4 text-xs text-fg-subtle">Member since {formatDateLong(me.data.createdAt)}. Deposit per booking: one hour at your rate.</p>
            </div>
          )}
        </Card>
        <Card>
          <CardHeader title="Change password" description="At least 8 characters with upper- and lower-case letters, a digit and a symbol." />
          <ChangePasswordForm />
        </Card>
      </div>
    </>
  );
}
