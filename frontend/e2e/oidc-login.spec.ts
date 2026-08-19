import { expect, test } from '@playwright/test';

const baseUrl = process.env.ITSM_E2E_BASE_URL;
const username = process.env.ITSM_E2E_USER ?? 'anna';
const password = process.env.ITSM_E2E_PASSWORD ?? 'anna';

test.describe('Keycloak login against the deployed stack', () => {
  test.skip(!baseUrl, 'set ITSM_E2E_BASE_URL to the deployed frontend origin');
  test.use({ storageState: { cookies: [], origins: [] } });

  test('signs in and loads live data for the authenticated user', async ({ page }) => {
    // Two identity-provider round trips (login, then silent restore) exceed the default budget.
    test.setTimeout(120_000);
    const apiStatuses: string[] = [];
    page.on('response', (response) => {
      if (response.url().includes('/api/v1/')) {
        apiStatuses.push(`${response.status()} ${new URL(response.url()).pathname}`);
      }
    });

    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    await page.getByRole('button', { name: /,/ }).first().click();
    await page.getByRole('menuitem', { name: /Войти|Sign in/ }).click();

    await page.waitForURL(/\/realms\/itsm\/protocol\/openid-connect\/auth/);
    await page.locator('#username').fill(username);
    await page.locator('#password').fill(password);
    await page.locator('#kc-login').click();

    await page.waitForURL((url) => !url.pathname.startsWith('/realms'));
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    apiStatuses.length = 0;
    await page.reload();
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect
      .poll(() => apiStatuses.filter((entry) => entry.startsWith('200')).length, { timeout: 15_000 })
      .toBeGreaterThan(0);

    expect(apiStatuses.filter((entry) => entry.startsWith('401') || entry.startsWith('403'))).toEqual([]);
    expect(apiStatuses.filter((entry) => entry.startsWith('5'))).toEqual([]);
  });
});
