import { test, expect } from '@playwright/test';
import { collectConsoleErrors, login } from './helpers';

test.describe('Authenticated pages', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('admin navigation is available after login', async ({ page }) => {
    await expect(page.getByRole('link', { name: 'Simulations' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Administration' })).toBeVisible();
  });

  test('simulations page loads without unexpected console errors', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await page.goto('/simulations');

    await expect(page.getByRole('button', { name: 'Create simulation' })).toBeVisible();
    expect(errors, `unexpected console errors:\n${errors.join('\n')}`).toEqual([]);
  });

  test('metrics page renders live JVM metrics', async ({ page }) => {
    await page.goto('/admin/metrics');

    await expect(page.getByRole('heading', { name: 'Application Metrics' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'JVM Metrics' })).toBeVisible();
  });
});
