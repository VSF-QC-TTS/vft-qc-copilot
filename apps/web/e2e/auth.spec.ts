import { expect, test } from '@playwright/test';

import { installMvpApiMock } from './support/mvp-api-mock';

test('E2E-01 authentication supports login, reload session, refresh, and logout', async ({
  page,
}) => {
  await installMvpApiMock(page, { authenticated: false });

  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Đăng nhập vào hệ thống' })).toBeVisible();

  await page.getByLabel('Email').fill('e2e.admin@vinfast.vn');
  await page.getByRole('textbox', { name: 'Mật khẩu' }).fill('ChangeMe@123');
  await page.getByRole('button', { name: 'Đăng nhập' }).click();

  await expect(page).toHaveURL(/\/workspaces$/);
  await expect(page.getByRole('link', { name: /E2E Workspace/ })).toBeVisible();

  await page.reload();
  await expect(page.getByRole('link', { name: /E2E Workspace/ })).toBeVisible();

  await page.getByLabel('Đăng xuất').first().click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('button', { name: 'Đăng nhập' })).toBeVisible();
});
