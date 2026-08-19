import { defineConfig, devices } from '@playwright/test';

/**
 * Smoke E2E — mock mode by default (no backend).
 * Dev server: Vite on port 5173.
 * Use 127.0.0.1 (not "localhost") so Windows does not resolve to a different
 * IPv6 listener on the same port.
 */
const deployedBaseUrl = process.env.ITSM_E2E_BASE_URL;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: deployedBaseUrl ?? 'http://127.0.0.1:5173',
    trace: 'on-first-retry',
    locale: 'ru-RU',
  },
  // A deployed stack (Compose) serves the app itself; only start Vite for local specs.
  webServer: deployedBaseUrl ? undefined : {
    command: 'npm run dev -- --host 127.0.0.1 --port 5173 --strictPort',
    url: 'http://127.0.0.1:5173',
    // Prefer a fresh Vite for smoke; set PW_REUSE_SERVER=1 to attach to an existing one
    reuseExistingServer: process.env.PW_REUSE_SERVER === '1',
    timeout: 120_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
