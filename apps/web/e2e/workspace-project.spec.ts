import { expect, test } from '@playwright/test';

import { installMvpApiMock, mvpFixtureIds } from './support/mvp-api-mock';

test('E2E-02 opens seeded Workspace and Project navigation context', async ({ page }) => {
  await installMvpApiMock(page, { authenticated: true, workspaceRole: 'ADMIN' });

  await page.goto('/workspaces');
  await expect(page.getByRole('heading', { name: 'Workspace' })).toBeVisible();

  await page.getByRole('link', { name: /E2E Workspace/ }).click();
  await expect(page).toHaveURL(new RegExp(`/workspaces/${mvpFixtureIds.workspaceId}$`));
  await expect(page.getByRole('heading', { name: 'E2E Workspace' })).toBeVisible();

  await page.getByRole('link', { name: 'Tất cả Project' }).click();
  await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible();

  await page
    .getByRole('link', { name: /E2E Project/ })
    .first()
    .click();
  await expect(page).toHaveURL(
    new RegExp(`/workspaces/${mvpFixtureIds.workspaceId}/projects/${mvpFixtureIds.projectId}$`),
  );
  await expect(page.getByRole('heading', { name: 'E2E Project' })).toBeVisible();
  await expect(page.getByText('Sẵn sàng chạy')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Tạo TestRun' })).toBeVisible();
});

test('E2E-05 permission roles expose read-only UI for VIEWER', async ({ page }) => {
  await installMvpApiMock(page, { authenticated: true, workspaceRole: 'VIEWER' });

  await page.goto(
    `/workspaces/${mvpFixtureIds.workspaceId}/projects/${mvpFixtureIds.projectId}/test-runs/new`,
  );

  await expect(page.getByRole('heading', { name: 'Tạo TestRun' })).toBeVisible();
  await expect(
    page.getByText('VIEWER có thể chạy preflight nhưng không thể tạo TestRun.'),
  ).toBeVisible();
  await expect(page.getByRole('button', { name: 'Tạo và mở TestRun' })).toHaveCount(0);

  await page.goto(`/workspaces/${mvpFixtureIds.workspaceId}/settings/members`);
  await expect(page.getByRole('heading', { name: 'Thành viên' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Thêm thành viên' })).toHaveCount(0);
});

test('E2E-05 platform admin can access admin user invite route', async ({ page }) => {
  await installMvpApiMock(page, {
    authenticated: true,
    workspaceRole: 'ADMIN',
    systemRole: 'PLATFORM_ADMIN',
  });

  await page.goto('/admin/users');
  await expect(page.getByRole('heading', { name: 'Quản lý user invite' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Gửi invite' })).toBeVisible();
});
