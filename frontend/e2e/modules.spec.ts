import { test, expect } from '@playwright/test';

/**
 * The screens added for releases, on-call and announcements: they must route, render their
 * heading and reach their empty/error state without throwing. Default locale is Russian.
 */
test.describe('module screens', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      try {
        const keys: string[] = [];
        for (let i = 0; i < localStorage.length; i += 1) {
          const k = localStorage.key(i);
          if (k && k.startsWith('vox')) keys.push(k);
        }
        keys.forEach((k) => localStorage.removeItem(k));
        sessionStorage.clear();
      } catch {
        /* ignore */
      }
    });
  });

  test('operator sidebar links to releases', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('link', { name: /Релизы|Releases/i })).toBeVisible();
  });

  test('admin sidebar links to on-call and announcements', async ({ page }) => {
    await page.goto('/admin/oncall');
    await expect(
      page.getByRole('link', { name: /Дежурства|On-call|Rufbereitschaft/i }),
    ).toBeVisible();
    await expect(
      page.getByRole('link', { name: /Объявления|Announcements|Ankündigungen/i }),
    ).toBeVisible();
  });

  test('releases page renders its heading and create action', async ({ page }) => {
    await page.goto('/releases');
    await expect(
      page.getByRole('heading', { name: /Релизы|Releases/i, level: 1 }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: /Новый релиз|New release|Neues Release/i }),
    ).toBeVisible();
  });

  test('on-call page renders both sections', async ({ page }) => {
    await page.goto('/admin/oncall');
    await expect(
      page.getByRole('heading', { name: /Дежурства|On-call|Rufbereitschaft/i, level: 1 }),
    ).toBeVisible();
    await expect(
      page.getByRole('heading', { name: /Графики дежурств|Rotations|Rotationen/i }),
    ).toBeVisible();
    await expect(
      page.getByRole('heading', {
        name: /Политики эскалации|Escalation policies|Eskalationsrichtlinien/i,
      }),
    ).toBeVisible();
  });

  test('announcements page renders its heading and create action', async ({ page }) => {
    await page.goto('/admin/announcements');
    await expect(
      page.getByRole('heading', { name: /Объявления|Announcements|Ankündigungen/i, level: 1 }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', {
        name: /Новое объявление|New announcement|Neue Ankündigung/i,
      }),
    ).toBeVisible();
  });

  test('a work item detail view offers the time tab', async ({ page }) => {
    await page.goto('/queues');
    const firstItem = page.getByRole('link', { name: /INC-|REQ-/ }).first();
    if ((await firstItem.count()) === 0) {
      test.skip(true, 'no work item available in this mode');
    }
    await firstItem.click();
    await expect(page.getByRole('tab', { name: /Время|Time|Zeit/i })).toBeVisible();
  });
});
