import { test, expect } from '@playwright/test';

/**
 * Frontend smoke E2E (mock mode — no backend required).
 * Default locale is Russian (ru).
 */
test.describe('smoke', () => {
  test.beforeEach(async ({ page }) => {
    // Clean demo persistence so smoke is deterministic (locale + durable store + notifs)
    await page.addInitScript(() => {
      try {
        const keys: string[] = [];
        for (let i = 0; i < localStorage.length; i += 1) {
          const k = localStorage.key(i);
          if (k && (k.startsWith('vox-') || k.startsWith('vox'))) keys.push(k);
        }
        keys.forEach((k) => localStorage.removeItem(k));
        sessionStorage.clear();
      } catch {
        /* ignore */
      }
    });
  });

  test('loads overview with Russian default / brand', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.brand b')).toHaveText('vox');
    // Russian nav label for overview
    await expect(page.getByRole('link', { name: 'Обзор' })).toBeVisible();
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });

  test('language switch to EN shows English nav', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('link', { name: 'Обзор' })).toBeVisible();

    await page.getByRole('button', { name: /язык|language/i }).click();
    await page.getByRole('option', { name: /English/i }).click();

    await expect(page.getByRole('link', { name: 'Overview' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Queues' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'My work' })).toBeVisible();
  });

  test('open command palette with Control+K or Meta+K', async ({ page }) => {
    await page.goto('/');
    // The hotkey listener is registered by the shell, so wait for it to render before
    // pressing — otherwise the key press lands before React mounts and is lost.
    await expect(
      page.getByRole('link', { name: /Обзор|Overview|Übersicht/i }),
    ).toBeVisible();
    // Windows/Linux: Control+K; macOS: Meta+K (both handled by the app)
    const modifier = process.platform === 'darwin' ? 'Meta' : 'Control';
    await page.keyboard.press(`${modifier}+KeyK`);

    const dialog = page.getByRole('dialog', {
      name: /Командная палитра|Command palette|Befehlspalette/i,
    });
    await expect(dialog).toBeVisible();
    await expect(
      dialog.getByPlaceholder(
        /Найти обращение|Find a work item|Ticket|Seite/i,
      ),
    ).toBeVisible();
  });

  test('navigate to queues', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'Очереди' }).click();
    await expect(page).toHaveURL(/\/queues/);
    await expect(page.getByRole('heading', { name: 'Очереди' })).toBeVisible();
  });

  test('open create incident modal and close', async ({ page }) => {
    await page.goto('/');

    await page.getByRole('button', { name: 'Создать' }).click();
    await page.getByRole('menuitem', { name: /Инцидент/i }).click();

    const modal = page.getByRole('dialog');
    await expect(modal).toBeVisible();
    await expect(
      modal.getByRole('heading', { name: /Сообщить о неполадке|Report an issue/i }),
    ).toBeVisible();

    await modal.getByRole('button', { name: /Закрыть|Close/i }).click();
    await expect(modal).toHaveCount(0);
  });

  test('settings page shows language options', async ({ page }) => {
    await page.goto('/settings');
    await expect(
      page.getByRole('heading', { name: /Настройки|Settings/i }),
    ).toBeVisible();

    // Settings is sectioned; open Language tab before asserting radios
    await page.getByRole('tab', { name: /Язык|Language/i }).click();

    const langGroup = page.getByRole('radiogroup', {
      name: /Язык|Language/i,
    });
    await expect(langGroup).toBeVisible({ timeout: 10_000 });
    await expect(langGroup.getByRole('radio', { name: /Русский/i })).toBeVisible();
    await expect(langGroup.getByRole('radio', { name: /English/i })).toBeVisible();
    await expect(langGroup.getByRole('radio', { name: /Deutsch/i })).toBeVisible();
  });
});

