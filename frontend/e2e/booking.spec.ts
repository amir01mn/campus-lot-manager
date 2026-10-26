import { expect, test } from '@playwright/test';
import { campusDate, pad, randomInt } from './support/campus';

/**
 * Smoke test of the main driver journey against the live demo backend:
 * sign in as the demo student → book a spot → see it in My bookings → cancel it (with a refund).
 */
test('student books a spot, sees it in My bookings and cancels it', async ({ page, request }) => {
  const health = await request.get('/api/lots');
  expect(health.ok(), 'the backend must be running with the demo profile').toBeTruthy();

  // 1. Sign in with the one-click demo account
  await page.goto('/login');
  await page.getByRole('button', { name: /sign in as demo student/i }).click();
  await expect(page.getByRole('heading', { name: /^Find parking, / })).toBeVisible();

  // 2. Open a lot and pick a window far enough ahead to be refundable and unlikely to clash with earlier runs
  await page.getByRole('link', { name: 'Lot B — Stong Pond' }).click();
  await expect(page.getByRole('heading', { name: 'Lot B — Stong Pond' })).toBeVisible();
  const start = randomInt(6, 18);
  await page.getByLabel('Date', { exact: true }).fill(campusDate(randomInt(3, 80)));
  await page.getByLabel('Start', { exact: true }).selectOption(`${pad(start)}:00`);
  await page.getByLabel('End', { exact: true }).selectOption(`${pad(start + 2)}:00`);

  // the quote is for the student tier: $5/h × 2 h
  await expect(page.getByTestId('quote-total')).toHaveText('$10.00');

  const freeSpot = page.locator('[role="radio"][data-state="free"]').first();
  await expect(freeSpot).toBeVisible();
  await freeSpot.click();
  await expect(freeSpot).toHaveAttribute('aria-checked', 'true');
  const spotLabel = ((await freeSpot.getAttribute('aria-label')) ?? '').replace(/^Spot (\S+),.*$/, '$1');

  await page.getByLabel('Licence plate').fill('e2e 123');
  await page.getByRole('radio', { name: 'PayPal' }).check({ force: true });
  await page.getByRole('button', { name: 'Confirm and pay $5.00 deposit' }).click();

  await expect(page.getByRole('status').filter({ hasText: 'Booking confirmed' })).toBeVisible();
  const reference = (await page.locator('dd.font-mono').first().textContent())?.trim() ?? '';
  expect(reference).toMatch(/^CP-[A-Z0-9]{6}$/);

  // 3. It's listed under Upcoming in My bookings
  await page.getByRole('link', { name: 'View my bookings' }).click();
  const card = page.getByRole('article', { name: `Booking ${reference}` });
  await expect(card).toBeVisible();
  await expect(card).toContainText(`Spot ${spotLabel}`);
  await expect(card).toContainText('E2E 123');

  // 4. Cancel it; more than an hour ahead, so the dialog promises a full refund
  await card.getByRole('button', { name: 'Cancel' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('Full deposit refund')).toBeVisible();
  await dialog.getByRole('button', { name: 'Cancel booking' }).click();
  await expect(page.getByText(`Booking ${reference} cancelled`)).toBeVisible();
  await expect(card).toBeHidden();

  await page.getByRole('tab', { name: /Cancelled/ }).click();
  await expect(page.getByRole('article', { name: `Booking ${reference}` })).toBeVisible();

  // the receipt shows the deposit and its refund, netting to zero
  await page.getByRole('article', { name: `Booking ${reference}` }).getByRole('link', { name: 'Receipt' }).click();
  await expect(page.getByTestId('net-paid')).toHaveText('$0.00');
});
