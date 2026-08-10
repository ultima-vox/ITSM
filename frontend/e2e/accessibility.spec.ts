import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

for (const route of ['/', '/queues', '/settings']) {
  test(`has no serious accessibility violations: ${route}`, async ({ page }) => {
    await page.goto(route);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    const serious = results.violations.filter(
      (violation) => violation.impact === 'serious' || violation.impact === 'critical',
    );
    const summary = serious.flatMap((violation) =>
      violation.nodes.map((node) => {
        const contrast = node.any[0]?.data as { fgColor?: string; bgColor?: string } | null;
        return `${violation.id}: ${node.target.join(' ')} ${contrast?.fgColor ?? ''}/${contrast?.bgColor ?? ''}`;
      }),
    );
    expect(summary).toEqual([]);
  });
}
