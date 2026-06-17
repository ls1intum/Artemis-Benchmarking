import { test, expect, Page } from '@playwright/test';
import { login } from './helpers';

// Full-vertical CRUD against User Management: every action goes through the
// Angular UI -> Spring Boot REST API -> PostgreSQL. The block is serial because the
// steps build on the same user record (create -> read -> update -> delete).
test.describe.configure({ mode: 'serial' });

test.describe('User management CRUD', () => {
  // Unique per run so leftover data from a previous (failed) run never collides.
  const LOGIN = `e2e-user-${Date.now()}`;
  const EMAIL = `${LOGIN}@example.com`;
  const LIST_URL = '/admin/user-management';

  const userRow = (page: Page) => page.getByRole('row', { name: LOGIN });

  // Submit the create/edit form and wait until it closes, i.e. the save request
  // has completed. (Navigating away immediately would abort the in-flight POST.)
  const save = async (page: Page): Promise<void> => {
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.locator('#field_login')).toHaveCount(0);
  };

  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('creates a new user', async ({ page }) => {
    await page.goto(`${LIST_URL}/new`);
    await page.locator('#field_login').fill(LOGIN);
    await page.locator('#field_firstName').fill('E2E');
    await page.locator('#field_lastName').fill('User');
    await page.locator('#field_email').fill(EMAIL);
    await save(page);

    // The new user is now shown in the list.
    await page.goto(LIST_URL);
    await expect(userRow(page)).toBeVisible();
  });

  test('lists the new user with its email', async ({ page }) => {
    await page.goto(LIST_URL);
    await expect(userRow(page)).toContainText(EMAIL);
  });

  test('persists the user after a reload (read from the database)', async ({ page }) => {
    await page.goto(LIST_URL);
    await page.reload();
    await expect(userRow(page)).toBeVisible();
  });

  test('opens the user detail view', async ({ page }) => {
    await page.goto(LIST_URL);
    await userRow(page).getByRole('button', { name: 'View' }).click();
    await expect(page).toHaveURL(new RegExp(`${LOGIN}/view`));
    await expect(page.getByText(LOGIN).first()).toBeVisible();
  });

  test('edits the first and last name', async ({ page }) => {
    await page.goto(LIST_URL);
    await userRow(page).getByRole('button', { name: 'Edit' }).click();
    await expect(page).toHaveURL(new RegExp(`${LOGIN}/edit`));

    await page.locator('#field_firstName').fill('Edited');
    await page.locator('#field_lastName').fill('Person');
    await save(page);

    // Edit saved; the user is still listed (verified in detail in the next test).
    await page.goto(LIST_URL);
    await expect(userRow(page)).toBeVisible();
  });

  test('shows the edited name on the detail view', async ({ page }) => {
    await page.goto(`${LIST_URL}/${LOGIN}/view`);
    await expect(page.getByText('Edited').first()).toBeVisible();
    await expect(page.getByText('Person').first()).toBeVisible();
  });

  test('grants the admin authority to the user', async ({ page }) => {
    await page.goto(`${LIST_URL}/${LOGIN}/edit`);
    await page.locator('#field_authority').selectOption('ROLE_ADMIN');
    await save(page);

    await page.goto(LIST_URL);
    await expect(userRow(page)).toContainText('ROLE_ADMIN');
  });

  test('deactivates the user', async ({ page }) => {
    await page.goto(LIST_URL);
    await userRow(page).getByRole('button', { name: 'Activated' }).click();
    await expect(userRow(page).getByRole('button', { name: 'Deactivated' })).toBeVisible();
  });

  test('reactivates the user', async ({ page }) => {
    await page.goto(LIST_URL);
    await userRow(page).getByRole('button', { name: 'Deactivated' }).click();
    await expect(userRow(page).getByRole('button', { name: 'Activated' })).toBeVisible();
  });

  test('keeps the user when the delete dialog is cancelled', async ({ page }) => {
    await page.goto(LIST_URL);
    await userRow(page).getByRole('button', { name: 'Delete' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(`delete user ${LOGIN}`);
    await dialog.getByRole('button', { name: 'Cancel' }).click();

    await expect(userRow(page)).toBeVisible();
  });

  test('deletes the user', async ({ page }) => {
    await page.goto(LIST_URL);
    await userRow(page).getByRole('button', { name: 'Delete' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();

    await expect(userRow(page)).toHaveCount(0);

    // and it is gone after a reload (removed from the database)
    await page.reload();
    await expect(userRow(page)).toHaveCount(0);
  });
});
