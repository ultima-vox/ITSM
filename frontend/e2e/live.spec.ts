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

  test('uploads a clean file and blocks EICAR download', async ({ request }) => {
    const clean = await request.post('/api/v1/attachments', {
      multipart: {
        file: {
          name: 'note.txt',
          mimeType: 'text/plain',
          buffer: Buffer.from('live e2e clean attachment'),
        },
      },
    });
    expect(clean.status()).toBe(201);
    const cleanMeta = (await clean.json()) as { id: string; scanStatus: string };
    expect(cleanMeta.scanStatus).toBe('CLEAN');

    const eicarBody =
      'X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*';
    const infected = await request.post('/api/v1/attachments', {
      multipart: {
        file: {
          name: 'readme.txt',
          mimeType: 'text/plain',
          buffer: Buffer.from(eicarBody),
        },
      },
    });
    expect(infected.status()).toBe(201);
    const infectedMeta = (await infected.json()) as { id: string; scanStatus: string };
    expect(infectedMeta.scanStatus).toBe('INFECTED');

    const blocked = await request.get(`/api/v1/attachments/${infectedMeta.id}/content`);
    expect(blocked.status()).toBe(403);

    // The clean file has to come back too: blocking the infected one proves nothing if the
    // download path is broken for everything. A metadata-only storage backend answers 501,
    // which is a deliberate answer; a 500 is the bug this guards against.
    const download = await request.get(`/api/v1/attachments/${cleanMeta.id}/content`);
    expect([200, 501]).toContain(download.status());
    if (download.status() === 200) {
      expect(await download.text()).toBe('live e2e clean attachment');
      expect(download.headers()['content-disposition']).toContain('filename="note.txt"');
    }
  });

  test('serves a download whose filename cannot inject headers', async ({ request }) => {
    const hostile = await request.post('/api/v1/attachments', {
      multipart: {
        file: {
          name: 'in\r\nSet-Cookie: stolen=1.txt',
          mimeType: 'text/plain',
          buffer: Buffer.from('header injection probe'),
        },
      },
    });
    expect(hostile.status()).toBe(201);
    const meta = (await hostile.json()) as { id: string };

    const download = await request.get(`/api/v1/attachments/${meta.id}/content`);
    expect([200, 501]).toContain(download.status());
    const disposition = download.headers()['content-disposition'] ?? '';
    expect(disposition).not.toContain('Set-Cookie');
    expect(download.headers()['set-cookie']).toBeUndefined();
  });
});
