import { Page, expect } from '@playwright/test';

// Seeded admin user (see src/main/resources/config/liquibase/data/user.csv).
export const ADMIN = { username: 'admin', password: 'admin' };

// Console messages that are expected and intentionally ignored:
// - the anonymous GET /api/account auth check returns 401 before login.
const IGNORED_CONSOLE_PATTERNS = [/api\/account/, /401/];

/**
 * Start collecting browser console errors and uncaught page errors.
 * Returns the array, which is populated as the page runs.
 */
export function collectConsoleErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on('console', msg => {
    if (msg.type() === 'error' && !IGNORED_CONSOLE_PATTERNS.some(pattern => pattern.test(msg.text()))) {
      errors.push(msg.text());
    }
  });
  page.on('pageerror', error => errors.push(error.message));
  return errors;
}

/** Sign in through the login form and wait until the authenticated navigation is shown. */
export async function login(page: Page, user = ADMIN): Promise<void> {
  await page.goto('/login');
  await page.getByRole('textbox', { name: 'Username' }).fill(user.username);
  await page.getByRole('textbox', { name: 'Password' }).fill(user.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('link', { name: 'Simulations' })).toBeVisible();
}
