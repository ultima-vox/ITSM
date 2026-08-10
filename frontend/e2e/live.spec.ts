import { expect, test } from '@playwright/test';

test.describe('live backend', () => {
  test.skip(process.env.VITE_USE_MOCK !== 'false', 'requires VITE_USE_MOCK=false');

  test('loads persisted work items without server errors', async ({ page }) => {
    const serverErrors: string[] = [];
    page.on('response', (response) => {
      if (response.status() >= 500) {
        serverErrors.push(`${response.status()} ${response.url()}`);
      }
    });

    await page.goto('/');
    await expect(page.locator('.brand b')).toHaveText('vox');
    await expect(page.getByRole('link', { name: /Overview|Обзор/ })).toBeVisible();
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByText('INC-001002')).toBeVisible();
    expect(serverErrors).toEqual([]);
  });

  test('serves live API through the frontend origin', async ({ request }) => {
    const response = await request.get('/api/v1/work-items');
    expect(response.ok()).toBeTruthy();

    const body = (await response.json()) as { items: unknown[]; total: number };
    expect(body.total).toBeGreaterThan(0);
    expect(body.items).toHaveLength(body.total);
  });
});
