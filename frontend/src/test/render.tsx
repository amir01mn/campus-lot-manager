import type { ReactElement } from 'react';
import { render, type RenderResult } from '@testing-library/react';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { setAuthToken } from '../api/client';
import { routes } from '../app/routes';
import { ToastProvider } from '../components/ui/ToastProvider';
import { AuthProvider } from '../features/auth/AuthProvider';
import { saveSession } from '../features/auth/session';
import type { UserProfile } from '../api/types';

export const TEST_TOKEN = 'test-token';

function testQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } } });
}

/** Signs a user in the way a page reload would see it: a session in sessionStorage. */
export function signIn(user: UserProfile): void {
  saveSession({ token: TEST_TOKEN, expiresAt: Date.now() + 3_600_000, user });
}

type Options = { route?: string; user?: UserProfile | null };

/** Renders the whole app (real routes, guards and providers) at `route`. */
export function renderApp({ route = '/', user = null }: Options = {}): RenderResult & { router: ReturnType<typeof createMemoryRouter> } {
  setAuthToken(null);
  if (user) signIn(user);
  const router = createMemoryRouter(routes, { initialEntries: [route] });
  const result = render(
    <QueryClientProvider client={testQueryClient()}>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </QueryClientProvider>,
  );
  return { ...result, router };
}

/** Renders one component inside the app's providers (router, query client, auth, toasts). */
export function renderWithProviders(ui: ReactElement, { route = '/', user = null }: Options = {}): RenderResult {
  setAuthToken(null);
  if (user) signIn(user);
  const testRoutes: RouteObject[] = [{ path: '*', element: <AuthProvider>{ui}</AuthProvider> }];
  const router = createMemoryRouter(testRoutes, { initialEntries: [route] });
  return render(
    <QueryClientProvider client={testQueryClient()}>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </QueryClientProvider>,
  );
}
