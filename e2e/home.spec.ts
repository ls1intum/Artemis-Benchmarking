import { test, expect } from '@playwright/test';
import { collectConsoleErrors, login } from './helpers';

test.describe('Public pages', () => {
  test('landing page loads without unexpected console errors', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'Welcome to the Artemis Benchmarking Tool' })).toBeVisible();
    await expect(page.getByRole('link', { name: /Artemis Benchmarking/ })).toBeVisible();
    expect(errors, `unexpected console errors:\n${errors.join('\n')}`).toEqual([]);
  });

  test('footer shows the git branch and commit', async ({ page }) => {
    await page.goto('/');

    // Regression guard: the footer reads /management/info asynchronously, and with zoneless change
    // detection it stays empty unless the values reach the template through signals.
    await expect(page.locator('.footer')).toContainText(/Branch: \S+, Commit: [0-9a-f]{7,} \(/);
  });

  test('user can sign in as admin', async ({ page }) => {
    await login(page);

    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByText('logged in as user "admin"')).toBeVisible();
  });
});
