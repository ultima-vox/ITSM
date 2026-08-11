import { expect, test } from '@playwright/test';

test.describe('live backend', () => {
  test.describe.configure({ mode: 'serial' });
  test.skip(process.env.VITE_USE_MOCK === 'true', 'requires live API mode');
  const fixtureTitle = 'CI live persisted incident';

  test.beforeAll(async ({ request }) => {
    const existing = await request.get('/api/v1/work-items?size=100');
    expect(existing.ok()).toBeTruthy();
    const body = (await existing.json()) as { items: Array<{ title: string }> };
    if (!body.items.some((item) => item.title === fixtureTitle)) {
      const created = await request.post('/api/v1/work-items', {
        data: {
          type: 'INCIDENT',
          title: fixtureTitle,
          description: 'Created by mandatory full-stack Playwright verification',
          service: 'CI',
          impact: 'MEDIUM',
          urgency: 'MEDIUM',
        },
      });
      expect(created.status()).toBe(201);
    }
  });

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
    await expect(page.getByText(fixtureTitle).first()).toBeVisible();
    expect(serverErrors).toEqual([]);
  });

  test('serves live API through the frontend origin', async ({ request }) => {
    const response = await request.get('/api/v1/work-items');
    expect(response.ok()).toBeTruthy();

    const body = (await response.json()) as { items: unknown[]; total: number };
    expect(body.total).toBeGreaterThan(0);
    expect(body.items).toHaveLength(body.total);
  });

  test('creates and publishes a knowledge article through live API', async ({ request }) => {
    const marker = `Live KB ${Date.now()}`;
    const created = await request.post('/api/v1/knowledge/articles', {
      data: {
        title: marker,
        body: 'Verified live knowledge authoring body',
        summary: 'Live E2E',
        locale: 'ru',
      },
    });
    expect(created.status()).toBe(201);
    const draft = (await created.json()) as { id: string; status: string };
    expect(draft.status).toBe('DRAFT');

    const published = await request.post(
      `/api/v1/knowledge/articles/${draft.id}/publish`,
    );
    expect(published.ok()).toBeTruthy();
    const article = (await published.json()) as { status: string; title: string };
    expect(article.status).toBe('PUBLISHED');
    expect(article.title).toBe(marker);
  });
});
