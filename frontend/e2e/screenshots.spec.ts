import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';
import { populateDemo } from './support/populateDemo';

const OUT = resolve(dirname(fileURLToPath(import.meta.url)), '../../docs/screenshots');
const DESKTOP = { width: 1440, height: 900 };
const MOBILE = { width: 390, height: 844 };

test.describe.configure({ mode: 'serial' });

async function shot(page: Page, name: string, fullPage = true) {
  // let charts and transitions settle
  await page.waitForTimeout(600);
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage });
}

/** Clicks the n-th bookable spot (free, or occupied now but free for the window), or the last one if fewer. */
async function pickSpot(page: Page, n: number) {
  const bookable = page.locator('[role="radio"]:not([aria-disabled="true"])');
  await expect(bookable.first()).toBeVisible();
  const count = await bookable.count();
  await bookable.nth(Math.min(n, count - 1)).click();
}

async function signIn(page: Page, demoLabel: string) {
  await page.goto('/login');
  await page.getByRole('button', { name: new RegExp(`sign in as demo ${demoLabel}`, 'i') }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'));
}

test.beforeAll(async ({ request }) => {
  mkdirSync(OUT, { recursive: true });
  await populateDemo(request);
});

test('driver screens', async ({ browser }) => {
  const context = await browser.newContext({ viewport: DESKTOP, colorScheme: 'light', timezoneId: 'America/Toronto', locale: 'en-CA' });
  const page = await context.newPage();

  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Sign in to CampusPark' })).toBeVisible();
  await shot(page, '01-login', false);

  await page.goto('/register');
  await page.getByLabel('First name').fill('Aisha');
  await page.getByLabel('Last name').fill('Rahman');
  await page.getByLabel('Email').fill('aisha.r@my.yorku.ca');
  await page.getByLabel('Password', { exact: true }).fill('Parking2');
  await shot(page, '02-register-password-policy', false);

  await signIn(page, 'student');
  await expect(page.getByRole('heading', { name: /Find parking/ })).toBeVisible();
  await expect(page.getByText('spots free now').first()).toBeVisible();
  await shot(page, '03-lots-overview');

  await page.getByRole('link', { name: 'Lot A — Keele Campus North' }).click();
  await page.getByLabel('Date', { exact: true }).fill(await page.evaluate(() => new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Toronto' }).format(new Date(Date.now() + 86_400_000))));
  await page.getByLabel('Start', { exact: true }).selectOption('10:00');
  await page.getByLabel('End', { exact: true }).selectOption('13:30');
  await expect(page.getByTestId('quote-total')).toBeVisible();
  await pickSpot(page, 8);
  await page.getByLabel('Licence plate').fill('CPRK 311');
  await shot(page, '04-lot-detail-spot-grid-quote');

  await page.goto('/bookings');
  await expect(page.getByRole('article').first()).toBeVisible();
  await shot(page, '05-my-bookings');

  await page.getByRole('tab', { name: /Past/ }).click();
  await page.getByRole('article').first().getByRole('link', { name: 'Receipt' }).click();
  await expect(page.getByTestId('net-paid')).toBeVisible();
  await shot(page, '06-receipt');

  await page.goto('/bookings');
  await page.getByRole('article').first().getByRole('button', { name: 'Cancel' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await shot(page, '07-cancel-refund-dialog', false);
  await page.getByRole('dialog').getByRole('button', { name: 'Keep booking' }).click();

  await context.close();
});

test('manager screens', async ({ browser }) => {
  const context = await browser.newContext({ viewport: DESKTOP, colorScheme: 'light', timezoneId: 'America/Toronto', locale: 'en-CA' });
  const page = await context.newPage();
  await signIn(page, 'manager');

  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  await expect(page.getByText('Occupancy now')).toBeVisible();
  await page.waitForTimeout(1000);
  await shot(page, '08-manager-dashboard');

  await page.getByRole('button', { name: /^Notifications/ }).click();
  await expect(page.getByRole('region', { name: 'Latest notifications' })).toBeVisible();
  await shot(page, '09-notifications', false);
  await page.keyboard.press('Escape');

  await page.goto('/manager/bookings');
  await expect(page.getByRole('table')).toBeVisible();
  await shot(page, '10-manager-bookings-table', false);

  await page.goto('/manager/lots?lot=1');
  await page.getByRole('radio', { name: /Spot A-012/ }).click();
  await expect(page.getByRole('switch', { name: 'Needs maintenance' })).toBeVisible();
  await shot(page, '11-lot-management');

  await context.close();
});

test('admin, mobile and dark mode', async ({ browser }) => {
  const admin = await browser.newContext({ viewport: DESKTOP, colorScheme: 'light', timezoneId: 'America/Toronto', locale: 'en-CA' });
  const adminPage = await admin.newPage();
  await signIn(adminPage, 'super manager');
  await adminPage.goto('/admin');
  await adminPage.getByLabel('First name').fill('Riley');
  await adminPage.getByLabel('Last name').fill('Chen');
  // on a fresh demo database this is riley.chen@yorku.ca; re-runs fall back to a numbered address
  for (let n = 0; n < 20; n++) {
    await adminPage.getByLabel('Work email').fill(n === 0 ? 'riley.chen@yorku.ca' : `riley.chen${n + 1}@yorku.ca`);
    await adminPage.getByRole('button', { name: 'Create manager' }).click();
    const created = adminPage.getByTestId('temporary-password');
    const conflict = adminPage.getByText('An account with this email already exists.');
    await expect(created.or(conflict)).toBeVisible();
    if (await created.isVisible()) break;
  }
  await expect(adminPage.getByTestId('temporary-password')).toBeVisible();
  await shot(adminPage, '12-admin-create-manager', false);
  await admin.close();

  const mobile = await browser.newContext({ viewport: MOBILE, deviceScaleFactor: 2, isMobile: true, hasTouch: true, colorScheme: 'light', timezoneId: 'America/Toronto', locale: 'en-CA' });
  const phone = await mobile.newPage();
  await signIn(phone, 'student');
  await phone.goto('/lots/2');
  await pickSpot(phone, 3);
  await shot(phone, '13-mobile-lot-detail', false);
  await phone.goto('/bookings');
  await expect(phone.getByRole('article').first()).toBeVisible();
  await shot(phone, '14-mobile-my-bookings', false);
  await mobile.close();

  const dark = await browser.newContext({ viewport: DESKTOP, colorScheme: 'dark', timezoneId: 'America/Toronto', locale: 'en-CA' });
  const darkPage = await dark.newPage();
  await signIn(darkPage, 'manager');
  await expect(darkPage.getByText('Occupancy now')).toBeVisible();
  await darkPage.waitForTimeout(1000);
  await shot(darkPage, '15-dark-dashboard');
  await darkPage.goto('/lots/1');
  await pickSpot(darkPage, 5);
  await shot(darkPage, '16-dark-lot-detail');
  await dark.close();
});
