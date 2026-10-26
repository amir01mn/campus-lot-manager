import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/server';
import { lotA, manager, student } from '../../test/fixtures';
import { renderApp, TEST_TOKEN } from '../../test/render';
import { loadSession } from './session';

function loginHandler() {
  return http.post('*/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };
    if (body.password !== 'Demo#2026') {
      return HttpResponse.json(
        { type: 'urn:campus-parking:problem:unauthorized', title: 'Unauthorized', status: 401, detail: 'Invalid email or password.' },
        { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
      );
    }
    const user = body.email === manager.email ? manager : student;
    return HttpResponse.json({ accessToken: TEST_TOKEN, tokenType: 'Bearer', expiresIn: 3600, user });
  });
}

const commonHandlers = [
  http.get('*/api/lots', () => HttpResponse.json([lotA])),
  http.get('*/api/notifications/unread-count', () => HttpResponse.json({ count: 0 })),
];

describe('login flow', () => {
  it('shows the server error and stays on the page for wrong credentials', async () => {
    server.use(loginHandler(), ...commonHandlers);
    const user = userEvent.setup();
    const { router } = renderApp({ route: '/login' });

    await user.type(screen.getByLabelText('Email'), student.email);
    await user.type(screen.getByLabelText('Password'), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.');
    expect(router.state.location.pathname).toBe('/login');
    expect(loadSession()).toBeNull();
  });

  it('signs in, stores the session and lands on the lots page', async () => {
    server.use(loginHandler(), ...commonHandlers);
    const user = userEvent.setup();
    const { router } = renderApp({ route: '/login' });

    await user.type(screen.getByLabelText('Email'), student.email);
    await user.type(screen.getByLabelText('Password'), 'Demo#2026');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Find parking, Sam' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
    expect(loadSession()?.token).toBe(TEST_TOKEN);
  });

  it('signs in with one click on a demo account and goes to the manager dashboard for managers', async () => {
    server.use(
      loginHandler(),
      ...commonHandlers,
      http.get('*/api/manager/stats', () => HttpResponse.json({ error: 'not needed' }, { status: 500 })),
    );
    const user = userEvent.setup();
    const { router } = renderApp({ route: '/login' });

    await user.click(screen.getByRole('button', { name: /sign in as demo manager/i }));

    await screen.findByRole('heading', { name: 'Dashboard' });
    expect(router.state.location.pathname).toBe('/manager');
  });

  it('returns to the requested page after signing in', async () => {
    server.use(loginHandler(), ...commonHandlers, http.get('*/api/bookings/me', () => HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })));
    const user = userEvent.setup();
    const { router } = renderApp({ route: '/bookings' });

    // the guard sends anonymous users to the login page with a `next` link
    await screen.findByRole('heading', { name: 'Sign in to CampusPark' });
    expect(router.state.location.search).toContain('next=%2Fbookings');

    await user.type(screen.getByLabelText('Email'), student.email);
    await user.type(screen.getByLabelText('Password'), 'Demo#2026');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    await screen.findByRole('heading', { name: 'My bookings' });
  });

  it('signs out from a protected page without bouncing back to it on the next sign-in', async () => {
    server.use(...commonHandlers, http.get('*/api/bookings/me', () => HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })));
    const user = userEvent.setup();
    const { router } = renderApp({ route: '/bookings', user: student });

    await user.click(await screen.findByRole('button', { name: /account menu/i }));
    await user.click(screen.getByRole('menuitem', { name: 'Sign out' }));

    expect(await screen.findByText('You have been signed out.')).toBeInTheDocument();
    expect(router.state.location.search).toBe('?reason=signed-out');
    expect(loadSession()).toBeNull();
  });

  it('redirects to the login page with a message when the session is rejected (401)', async () => {
    server.use(
      ...commonHandlers,
      http.get('*/api/bookings/me', () =>
        HttpResponse.json({ status: 401, title: 'Unauthorized', detail: 'Authentication is required to access this resource.' }, { status: 401 }),
      ),
    );
    const { router } = renderApp({ route: '/bookings', user: student });

    expect(await screen.findByText('Your session has expired. Please sign in again.')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(loadSession()).toBeNull();
  });
});
