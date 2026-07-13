import { expect, test } from '@playwright/test';

import { installMvpApiMock, mvpFixtureIds } from './support/mvp-api-mock';

test('E2E-03 and E2E-04 run TestRun, review result, update matrix, and export FINAL XLSX', async ({
  page,
}) => {
  await installMvpApiMock(page, { authenticated: true, workspaceRole: 'ADMIN' });

  await page.goto(
    `/workspaces/${mvpFixtureIds.workspaceId}/projects/${mvpFixtureIds.projectId}/test-runs/new`,
  );

  await expect(page.getByRole('heading', { name: 'Tạo TestRun', level: 1 })).toBeVisible();
  await page.getByLabel('Run type').selectOption('COMPARISON');
  await page.getByLabel('Chọn target E2E Chatbot Target B').check();
  await page.getByRole('button', { name: 'Chạy preflight' }).click();
  await expect(page.getByRole('heading', { name: 'Có thể chạy' })).toBeVisible();

  await page.getByRole('button', { name: 'Tạo và mở TestRun' }).click();
  await expect(page).toHaveURL(
    new RegExp(
      `/workspaces/${mvpFixtureIds.workspaceId}/projects/${mvpFixtureIds.projectId}/test-runs/${mvpFixtureIds.testRunId}$`,
    ),
  );
  await expect(page.getByText('Realtime đã đóng')).toBeVisible();
  await expect(page.getByText('1/1')).toBeVisible();

  await page.getByRole('button', { name: 'Machine / Final' }).click();
  await page.getByText('#9001').click();
  await expect(page.getByRole('heading', { name: '#9001' })).toBeVisible();

  await page.getByLabel('Trạng thái review').selectOption('OVERRIDDEN');
  await page.getByLabel('Override status').selectOption('PASSED');
  await page.getByLabel('Override score').fill('0.96');
  await page.getByLabel('Ghi chú').fill('E2E QC review');
  await page.getByRole('button', { name: 'Lưu review' }).click();
  await expect(page.getByText('Đã lưu QC review')).toBeVisible();

  await page.getByRole('button', { name: 'Comparison matrix' }).click();
  await expect(page.getByRole('heading', { name: 'Comparison matrix' })).toBeVisible();
  await page.getByRole('button', { name: 'FINAL', exact: true }).click();
  await expect(page.getByTestId('matrix-reviewed-text').first()).toHaveText('Đã review');

  await page.getByRole('button', { name: 'Tổng quan' }).click();
  await page.getByRole('button', { name: 'Tạo Export job' }).click();
  await expect(page.getByText('e2e-test-run-final.xlsx')).toBeVisible();

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Tải XLSX' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toContain('e2e-test-run-final');
});

test('E2E-06 SSE recovery reconnects with cursor and avoids duplicate UI state', async ({
  page,
}) => {
  await installMvpApiMock(page, {
    authenticated: true,
    workspaceRole: 'ADMIN',
    sseReplay: true,
  });

  await page.goto(
    `/workspaces/${mvpFixtureIds.workspaceId}/projects/${mvpFixtureIds.projectId}/test-runs/${mvpFixtureIds.testRunId}`,
  );

  await expect(page.getByText('Realtime đã đóng')).toBeVisible();
  await expect(page.getByText('SSE #4')).toBeVisible();
  await expect(page.getByText('1/1')).toBeVisible();
});
