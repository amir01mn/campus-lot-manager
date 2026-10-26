import { useState, type SubmitEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { GraduationCap, UserPlus } from 'lucide-react';
import { authApi } from '../../api/endpoints';
import { errorMessage, fieldErrors } from '../../api/problem';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Feedback';
import { Field, Input } from '../../components/ui/Field';
import { AuthLayout } from './AuthLayout';
import { useAuth } from './authContext';
import { PasswordChecklist } from './PasswordChecklist';
import { isPasswordAcceptable } from './passwordPolicy';
import { homeFor } from './session';

const TIERS = [
  { label: 'Student', rate: '$5/h', who: '@my.yorku.ca' },
  { label: 'Faculty', rate: '$8/h', who: '@yorku.ca' },
  { label: 'Staff', rate: '$10/h', who: '@yorku.ca' },
  { label: 'Visitor', rate: '$15/h', who: 'everyone else' },
];

export function RegisterPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '' });
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const [touchedPassword, setTouchedPassword] = useState(false);

  if (user && !submitting) return <Navigate to={homeFor(user)} replace />;

  const passwordOk = isPasswordAcceptable(form.password);
  const errors = fieldErrors(error);
  const canSubmit = !!form.firstName.trim() && !!form.lastName.trim() && !!form.email.trim() && passwordOk;

  function update(key: keyof typeof form) {
    return (value: string) => {
      setForm((current) => ({ ...current, [key]: value }));
    };
  }

  async function onSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setTouchedPassword(true);
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      await authApi.register(form);
      const signedIn = await login({ email: form.email, password: form.password });
      void navigate(homeFor(signedIn), { replace: true });
    } catch (err) {
      setError(err);
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout>
      <h1 className="text-2xl font-semibold tracking-tight">Create your account</h1>
      <p className="mt-1.5 text-sm text-fg-muted">
        Already registered?{' '}
        <Link to="/login" className="font-medium text-accent hover:underline">
          Sign in
        </Link>
      </p>

      <div className="mt-6 rounded-xl border border-border bg-surface p-4">
        <div className="flex items-start gap-3">
          <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-accent-soft text-accent-soft-fg" aria-hidden="true">
            <GraduationCap className="size-4" />
          </span>
          <div className="text-sm">
            <p className="font-medium">Your rate comes from the university directory</p>
            <p className="mt-0.5 text-fg-muted">
              You don’t pick a tier. We look up your email in the York University directory; addresses that aren’t
              listed (including personal email) are billed as Visitors.
            </p>
          </div>
        </div>
        <dl className="mt-3 grid grid-cols-2 gap-2 text-xs sm:grid-cols-4">
          {TIERS.map((tier) => (
            <div key={tier.label} className="rounded-lg bg-surface-muted px-2.5 py-2">
              <dt className="font-medium text-fg">{tier.label}</dt>
              <dd className="tabular text-fg-muted">
                {tier.rate} <span className="block text-fg-subtle">{tier.who}</span>
              </dd>
            </div>
          ))}
        </dl>
      </div>

      {error !== null && Object.keys(errors).length === 0 && (
        <Alert tone="danger" className="mt-6">
          {errorMessage(error)}
        </Alert>
      )}

      <form onSubmit={onSubmit} className="mt-6 space-y-4" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="First name" error={errors.firstName}>
            <Input autoComplete="given-name" value={form.firstName} onChange={(e) => update('firstName')(e.target.value)} required />
          </Field>
          <Field label="Last name" error={errors.lastName}>
            <Input autoComplete="family-name" value={form.lastName} onChange={(e) => update('lastName')(e.target.value)} required />
          </Field>
        </div>
        <Field label="Email" hint="Use your @yorku.ca or @my.yorku.ca address for university rates." error={errors.email}>
          <Input type="email" autoComplete="email" value={form.email} onChange={(e) => update('email')(e.target.value)} required />
        </Field>
        <div className="space-y-2">
          <Field
            label="Password"
            error={errors.password ?? (touchedPassword && form.password && !passwordOk ? 'Your password doesn’t meet every rule below yet.' : null)}
          >
            <Input
              type="password"
              autoComplete="new-password"
              value={form.password}
              onChange={(e) => update('password')(e.target.value)}
              onBlur={() => setTouchedPassword(true)}
              required
            />
          </Field>
          <PasswordChecklist password={form.password} />
        </div>
        <Button type="submit" size="lg" className="w-full" loading={submitting} disabled={!canSubmit || submitting} icon={<UserPlus className="size-4" />}>
          Create account
        </Button>
      </form>
    </AuthLayout>
  );
}
