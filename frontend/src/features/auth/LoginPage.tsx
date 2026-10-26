import { useState, type SubmitEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { LogIn, Sparkles } from 'lucide-react';
import { errorMessage } from '../../api/problem';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Feedback';
import { Field, Input } from '../../components/ui/Field';
import { AuthLayout } from './AuthLayout';
import { useAuth } from './authContext';
import { DEMO_ACCOUNTS, SHOW_DEMO_ACCOUNTS, type DemoAccount } from './demoAccounts';
import { homeFor, safeNext } from './session';

const REASONS: Record<string, { tone: 'warning' | 'info' | 'success'; text: string }> = {
  expired: { tone: 'warning', text: 'Your session has expired. Please sign in again.' },
  'signed-out': { tone: 'info', text: 'You have been signed out.' },
  registered: { tone: 'success', text: 'Account created. Sign in to continue.' },
};

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState<string | null>(null);

  const next = safeNext(params.get('next'));
  const reason = REASONS[params.get('reason') ?? ''];

  if (user && !submitting) return <Navigate to={next ?? homeFor(user)} replace />;

  async function signIn(credentials: { email: string; password: string }, source: string) {
    setError(null);
    setSubmitting(source);
    try {
      const signedIn = await login(credentials);
      void navigate(next ?? homeFor(signedIn), { replace: true });
    } catch (err) {
      setError(errorMessage(err));
      setSubmitting(null);
    }
  }

  function onSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    void signIn({ email, password }, 'form');
  }

  function signInAsDemo(account: DemoAccount) {
    setEmail(account.email);
    setPassword(account.password);
    void signIn(account, account.email);
  }

  return (
    <AuthLayout>
      <h1 className="text-2xl font-semibold tracking-tight">Sign in to CampusPark</h1>
      <p className="mt-1.5 text-sm text-fg-muted">
        New here?{' '}
        <Link to="/register" className="font-medium text-accent hover:underline">
          Create an account
        </Link>
      </p>

      {reason && !error && (
        <Alert tone={reason.tone} className="mt-6">
          {reason.text}
        </Alert>
      )}
      {error && (
        <Alert tone="danger" className="mt-6">
          {error}
        </Alert>
      )}

      <form onSubmit={onSubmit} className="mt-6 space-y-4" noValidate>
        <Field label="Email">
          <Input
            type="email"
            name="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </Field>
        <Field label="Password">
          <Input
            type="password"
            name="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        <Button
          type="submit"
          size="lg"
          className="w-full"
          loading={submitting === 'form'}
          disabled={submitting !== null || !email || !password}
          icon={<LogIn className="size-4" />}
        >
          Sign in
        </Button>
      </form>

      {SHOW_DEMO_ACCOUNTS && (
        <section aria-labelledby="demo-heading" className="mt-8 rounded-xl border border-dashed border-border-strong bg-surface p-4">
          <div className="flex items-center gap-2">
            <Sparkles className="size-4 text-accent" aria-hidden="true" />
            <h2 id="demo-heading" className="text-sm font-semibold">
              Try a demo account
            </h2>
          </div>
          <p className="mt-1 text-xs text-fg-muted">One click signs you in with seeded data. Rates differ by tier.</p>
          <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3">
            {DEMO_ACCOUNTS.map((account) => (
              <Button
                key={account.email}
                variant="secondary"
                size="sm"
                className="h-auto flex-col items-start gap-0 py-2 text-left"
                loading={submitting === account.email}
                disabled={submitting !== null}
                onClick={() => signInAsDemo(account)}
                aria-label={`Sign in as demo ${account.label.toLowerCase()} (${account.email})`}
              >
                <span>{account.label}</span>
                <span className="text-xs font-normal text-fg-subtle">{account.description}</span>
              </Button>
            ))}
          </div>
        </section>
      )}
    </AuthLayout>
  );
}
