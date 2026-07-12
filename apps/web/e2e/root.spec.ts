import { expect, test } from '@playwright/test';

test('opens the frontend root', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'VinFast QC Copilot' })).toBeVisible();
});
