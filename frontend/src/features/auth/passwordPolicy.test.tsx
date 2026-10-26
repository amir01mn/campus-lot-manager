import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { isPasswordAcceptable, passwordRules } from './passwordPolicy';
import { renderApp } from '../../test/render';

describe('passwordRules', () => {
  it('mirrors the backend policy', () => {
    expect(isPasswordAcceptable('Demo#2026')).toBe(true);
    expect(isPasswordAcceptable('short1!A')).toBe(true);
    expect(isPasswordAcceptable('Sh0rt!')).toBe(false); // too short
    expect(isPasswordAcceptable('alllower1!')).toBe(false); // no upper-case
    expect(isPasswordAcceptable('ALLUPPER1!')).toBe(false); // no lower-case
    expect(isPasswordAcceptable('NoDigits!!')).toBe(false);
    expect(isPasswordAcceptable('NoSpecial12')).toBe(false);
    expect(isPasswordAcceptable('With Space12')).toBe(false); // whitespace isn't a special character
  });

  it('adds a byte-length rule only when the password is over 72 UTF-8 bytes', () => {
    expect(passwordRules('Aa1!'.repeat(18)).map((r) => r.id)).not.toContain('max');
    const tooLong = passwordRules('Aa1!é'.repeat(15));
    expect(tooLong.find((r) => r.id === 'max')?.met).toBe(false);
  });
});

describe('register page password policy', () => {
  it('ticks rules off live as the user types, with text (not only colour)', async () => {
    const user = userEvent.setup();
    renderApp({ route: '/register' });

    const list = screen.getByRole('list', { name: 'Password requirements' });
    const item = (name: RegExp) => within(list).getByText(name).closest('li');
    expect(item(/at least 8 characters/i)).toHaveAttribute('data-met', 'false');

    const password = screen.getByLabelText('Password');
    await user.type(password, 'abc');
    expect(item(/a lower-case letter/i)).toHaveAttribute('data-met', 'true');
    expect(item(/an upper-case letter/i)).toHaveAttribute('data-met', 'false');
    expect(within(list).getAllByText('(not met)').length).toBe(4);

    await user.type(password, 'DEF12!');
    for (const rule of list.querySelectorAll('li')) expect(rule).toHaveAttribute('data-met', 'true');
    expect(screen.getByText('5 of 5 password requirements met')).toBeInTheDocument();
  });

  it('explains that the rate tier comes from the directory', () => {
    renderApp({ route: '/register' });
    expect(screen.getByText(/your rate comes from the university directory/i)).toBeInTheDocument();
    expect(screen.getByText(/billed as Visitors/i)).toBeInTheDocument();
  });

  it('keeps the submit button disabled until the password satisfies the policy', async () => {
    const user = userEvent.setup();
    renderApp({ route: '/register' });
    await user.type(screen.getByLabelText('First name'), 'Ada');
    await user.type(screen.getByLabelText('Last name'), 'Lovelace');
    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'weakpass');
    expect(screen.getByRole('button', { name: 'Create account' })).toBeDisabled();
    await user.type(screen.getByLabelText('Password'), 'A1!');
    expect(screen.getByRole('button', { name: 'Create account' })).toBeEnabled();
  });
});
