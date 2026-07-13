import type { Cookie, Page, Route } from '@playwright/test';

export type E2eWorkspaceRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER';
export type E2eSystemRole = 'USER' | 'PLATFORM_ADMIN';

interface InstallMvpApiMockOptions {
  authenticated?: boolean;
  workspaceRole?: E2eWorkspaceRole;
  systemRole?: E2eSystemRole;
  sseReplay?: boolean;
}

interface UserFixture {
  id: string;
  email: string;
  displayName: string;
  name: string;
  avatarUrl: string | null;
  systemRole: E2eSystemRole;
  status: 'ACTIVE';
}

interface MvpMockState {
  authenticated: boolean;
  workspaceRole: E2eWorkspaceRole;
  user: UserFixture;
  secretCreated: boolean;
  providerCreated: boolean;
  testRunStatus: 'QUEUED' | 'RUNNING' | 'COMPLETED';
  reviewCreated: boolean;
  exportCreated: boolean;
  sseConnections: number;
  sseReplay: boolean;
}

const NOW = '2026-07-14T09:00:00+07:00';
const WORKSPACE_ID = '22222222-2222-4222-8222-222222222222';
const PROJECT_ID = '33333333-3333-4333-8333-333333333333';
const DATASET_ID = '44444444-4444-4444-8444-444444444444';
const DATASET_VERSION_ID = '55555555-5555-4555-8555-555555555555';
const DATASET_SCHEMA_ID = '66666666-6666-4666-8666-666666666666';
const SECRET_ID = '77777777-7777-4777-8777-777777777777';
const PROVIDER_ID = '88888888-8888-4888-8888-888888888888';
const TARGET_A_ID = '99999999-9999-4999-8999-999999999999';
const TARGET_B_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const EVALUATOR_ID = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const EVALUATOR_VERSION_ID = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
const TEST_RUN_ID = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
const TEST_RUN_TARGET_A_ID = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee';
const TEST_RUN_TARGET_B_ID = 'ffffffff-ffff-4fff-8fff-ffffffffffff';
const EXPORT_JOB_ID = '12121212-1212-4212-8212-121212121212';

export const mvpFixtureIds = {
  workspaceId: WORKSPACE_ID,
  projectId: PROJECT_ID,
  testRunId: TEST_RUN_ID,
};

export async function installMvpApiMock(
  page: Page,
  options: InstallMvpApiMockOptions = {},
): Promise<MvpMockState> {
  await seedCsrfCookie(page);

  const state: MvpMockState = {
    authenticated: options.authenticated ?? true,
    workspaceRole: options.workspaceRole ?? 'ADMIN',
    user: userFixture(options.systemRole ?? 'USER'),
    secretCreated: false,
    providerCreated: false,
    testRunStatus: options.sseReplay ? 'RUNNING' : 'COMPLETED',
    reviewCreated: false,
    exportCreated: false,
    sseConnections: 0,
    sseReplay: options.sseReplay ?? false,
  };

  await page.route('**/api/v1/**', async (route) => {
    await handleApiRoute(route, state);
  });

  return state;
}

async function seedCsrfCookie(page: Page): Promise<void> {
  const cookies: Cookie[] = [
    {
      name: 'XSRF-TOKEN',
      value: 'e2e-csrf-token',
      domain: '127.0.0.1',
      path: '/',
      httpOnly: false,
      secure: false,
      sameSite: 'Lax',
    },
  ];
  await page.context().addCookies(cookies);
}

async function handleApiRoute(route: Route, state: MvpMockState): Promise<void> {
  const request = route.request();
  const method = request.method();
  const url = new URL(request.url());
  const path = url.pathname;

  if (method === 'OPTIONS') {
    await route.fulfill({ status: 204, headers: corsHeaders() });
    return;
  }

  if (path === '/api/v1/auth/csrf') {
    await json(route, { ok: true });
    return;
  }

  if (path === '/api/v1/auth/login' && method === 'POST') {
    state.authenticated = true;
    await json(route, {
      accessToken: 'e2e-access-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: currentUser(state),
    });
    return;
  }

  if (path === '/api/v1/auth/logout' && method === 'POST') {
    state.authenticated = false;
    await empty(route);
    return;
  }

  if (path === '/api/v1/auth/refresh' && method === 'POST') {
    await json(route, { accessToken: 'e2e-refreshed-token', tokenType: 'Bearer', expiresIn: 3600 });
    return;
  }

  if (path === '/api/v1/auth/me' && method === 'GET') {
    if (!state.authenticated) {
      await problem(route, 401, 'UNAUTHENTICATED', 'Authentication required');
      return;
    }
    await json(route, currentUser(state));
    return;
  }

  if (!state.authenticated) {
    await problem(route, 401, 'UNAUTHENTICATED', 'Authentication required');
    return;
  }

  if (path === '/api/v1/workspaces' && method === 'GET') {
    await json(route, collection([workspace(state)]));
    return;
  }

  if (path === `/api/v1/workspaces/${WORKSPACE_ID}` && method === 'GET') {
    await json(route, workspace(state));
    return;
  }

  if (path === `/api/v1/workspaces/${WORKSPACE_ID}/projects` && method === 'GET') {
    await json(route, collection([project()]));
    return;
  }

  if (path === `/api/v1/workspaces/${WORKSPACE_ID}/members` && method === 'GET') {
    await json(route, collection(workspaceMembers(state)));
    return;
  }

  if (path === `/api/v1/workspaces/${WORKSPACE_ID}/members` && method === 'POST') {
    if (!canManage(state.workspaceRole, state.user.systemRole)) {
      await problem(route, 403, 'FORBIDDEN', 'Forbidden');
      return;
    }
    await json(route, invitedMember());
    return;
  }

  if (path === `/api/v1/projects/${PROJECT_ID}` && method === 'GET') {
    await json(route, project());
    return;
  }

  if (path === `/api/v1/projects/${PROJECT_ID}/setup-status` && method === 'GET') {
    await json(route, projectSetupStatus());
    return;
  }

  if (path === `/api/v1/workspaces/${WORKSPACE_ID}/secrets` && method === 'GET') {
    await json(route, collection(state.secretCreated ? [secret()] : [secret()]));
    return;
  }

  if (path === `/api/v1/workspaces/${WORKSPACE_ID}/secrets` && method === 'POST') {
    state.secretCreated = true;
    await json(route, secret());
    return;
  }

  if (path === `/api/v1/workspaces/${WORKSPACE_ID}/llm-providers` && method === 'GET') {
    await json(route, collection([provider()]));
    return;
  }

  if (path === `/api/v1/workspaces/${WORKSPACE_ID}/llm-providers` && method === 'POST') {
    state.providerCreated = true;
    await json(route, provider());
    return;
  }

  if (path === `/api/v1/llm-providers/${PROVIDER_ID}/test-logs` && method === 'GET') {
    await json(route, collection([]));
    return;
  }

  if (path === `/api/v1/projects/${PROJECT_ID}/chatbot-targets` && method === 'GET') {
    await json(route, { items: [targetA(), targetB()] });
    return;
  }

  if (path === `/api/v1/projects/${PROJECT_ID}/datasets` && method === 'GET') {
    await json(route, { items: [dataset()] });
    return;
  }

  if (path === `/api/v1/projects/${PROJECT_ID}/evaluators` && method === 'GET') {
    await json(route, { items: [evaluator()] });
    return;
  }

  if (path === `/api/v1/projects/${PROJECT_ID}/test-runs/preflight` && method === 'POST') {
    await json(route, {
      runnable: true,
      blockers: [],
      warnings: [],
      counts: { rows: 1, targets: 2, evaluators: 1 },
    });
    return;
  }

  if (path === `/api/v1/projects/${PROJECT_ID}/test-runs` && method === 'POST') {
    state.testRunStatus = 'QUEUED';
    await json(route, testRun(state));
    return;
  }

  if (path === `/api/v1/projects/${PROJECT_ID}/test-runs` && method === 'GET') {
    await json(route, { items: [testRunSummary(state)] });
    return;
  }

  if (path === `/api/v1/test-runs/${TEST_RUN_ID}` && method === 'GET') {
    await json(route, testRun(state));
    return;
  }

  if (path === `/api/v1/test-runs/${TEST_RUN_ID}/events` && method === 'GET') {
    await sse(route, state);
    return;
  }

  if (path === `/api/v1/test-runs/${TEST_RUN_ID}/results` && method === 'GET') {
    await json(route, resultPage(state));
    return;
  }

  if (path === `/api/v1/test-runs/${TEST_RUN_ID}/comparison-matrix` && method === 'GET') {
    await json(route, comparisonMatrix(state));
    return;
  }

  if (path === '/api/v1/test-case-results/9001' && method === 'GET') {
    await json(route, resultA(state));
    return;
  }

  if (path === '/api/v1/test-case-results/9001/review' && method === 'PUT') {
    state.reviewCreated = true;
    await json(route, review());
    return;
  }

  if (path === `/api/v1/test-runs/${TEST_RUN_ID}/export-jobs` && method === 'POST') {
    state.exportCreated = true;
    await json(route, exportJob(state));
    return;
  }

  if (path === `/api/v1/export-jobs/${EXPORT_JOB_ID}` && method === 'GET') {
    await json(route, exportJob(state));
    return;
  }

  if (path === `/api/v1/export-jobs/${EXPORT_JOB_ID}/download` && method === 'GET') {
    await route.fulfill({
      status: 200,
      headers: {
        ...corsHeaders(),
        'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      },
      body: 'fixture-xlsx-bytes',
    });
    return;
  }

  if (path === '/api/v1/admin/users' && method === 'POST') {
    await json(route, {
      id: '31313131-3131-4313-8313-313131313131',
      email: 'invited.e2e@vinfast.vn',
      displayName: 'Invited E2E',
      status: 'INVITED',
      systemRole: 'USER',
      message: 'Invitation created.',
    });
    return;
  }

  await problem(route, 404, 'NOT_FOUND', `No E2E mock for ${method} ${path}`);
}

async function sse(route: Route, state: MvpMockState): Promise<void> {
  state.sseConnections += 1;

  if (state.sseReplay && state.sseConnections === 1) {
    state.testRunStatus = 'RUNNING';
    await route.fulfill({
      status: 200,
      headers: { ...corsHeaders(), 'Content-Type': 'text/event-stream' },
      body: sseFrame(1, 'RUN_STARTED', { status: 'RUNNING', completed: 0, total: 1 }),
    });
    return;
  }

  state.testRunStatus = 'COMPLETED';
  await route.fulfill({
    status: 200,
    headers: { ...corsHeaders(), 'Content-Type': 'text/event-stream' },
    body: [
      sseFrame(1, 'RUN_STARTED', { status: 'RUNNING', completed: 0, total: 1 }),
      sseFrame(2, 'RUN_PROGRESS', { status: 'RUNNING', completed: 1, total: 1 }),
      sseFrame(3, 'TESTCASE_COMPLETED', { resultId: 9001 }),
      sseFrame(4, 'RUN_COMPLETED', { status: 'COMPLETED', completed: 1, total: 1 }),
    ].join(''),
  });
}

function sseFrame(sequenceNo: number, eventType: string, payload: Record<string, unknown>): string {
  return [
    `id:${String(sequenceNo)}`,
    `event:${eventType}`,
    `data:${JSON.stringify({
      eventId: `e2e-event-${String(sequenceNo)}`,
      sequenceNo,
      testRunId: TEST_RUN_ID,
      eventType,
      createdAt: NOW,
      payload,
    })}`,
    '',
    '',
  ].join('\n');
}

async function json(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({
    status,
    headers: { ...corsHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

async function empty(route: Route): Promise<void> {
  await route.fulfill({ status: 204, headers: corsHeaders() });
}

async function problem(route: Route, status: number, code: string, detail: string): Promise<void> {
  await route.fulfill({
    status,
    headers: { ...corsHeaders(), 'Content-Type': 'application/problem+json' },
    body: JSON.stringify({
      type: 'about:blank',
      title: code,
      status,
      detail,
      code,
    }),
  });
}

function corsHeaders(): Record<string, string> {
  return {
    'Access-Control-Allow-Origin': 'http://127.0.0.1:3000',
    'Access-Control-Allow-Credentials': 'true',
    'Access-Control-Allow-Headers':
      'Accept, Authorization, Content-Type, Last-Event-ID, X-XSRF-TOKEN',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
  };
}

function collection<T>(items: T[]): {
  items: T[];
  page: { limit: number; nextCursor: null; hasMore: false };
} {
  return { items, page: { limit: 50, nextCursor: null, hasMore: false } };
}

function userFixture(systemRole: E2eSystemRole): UserFixture {
  return {
    id:
      systemRole === 'PLATFORM_ADMIN'
        ? '10101010-1010-4010-8010-101010101010'
        : '11111111-1111-4111-8111-111111111111',
    email: systemRole === 'PLATFORM_ADMIN' ? 'platform.admin@vinfast.vn' : 'e2e.admin@vinfast.vn',
    displayName: systemRole === 'PLATFORM_ADMIN' ? 'Platform Admin' : 'E2E Admin',
    name: systemRole === 'PLATFORM_ADMIN' ? 'Platform Admin' : 'E2E Admin',
    avatarUrl: null,
    systemRole,
    status: 'ACTIVE',
  };
}

function currentUser(state: MvpMockState): Record<string, unknown> {
  return {
    id: state.user.id,
    email: state.user.email,
    displayName: state.user.displayName,
    avatarUrl: state.user.avatarUrl,
    systemRole: state.user.systemRole,
    status: state.user.status,
  };
}

function userSummary(state: MvpMockState): Record<string, unknown> {
  return {
    id: state.user.id,
    email: state.user.email,
    name: state.user.name,
    avatarUrl: null,
  };
}

function workspace(state: MvpMockState): Record<string, unknown> {
  return {
    id: WORKSPACE_ID,
    name: 'E2E Workspace',
    type: 'TEAM',
    currentUserRole: state.workspaceRole,
    createdBy: state.user.id,
    createdByUser: userSummary(state),
    createdAt: NOW,
    updatedAt: NOW,
  };
}

function project(): Record<string, unknown> {
  return {
    id: PROJECT_ID,
    workspaceId: WORKSPACE_ID,
    name: 'E2E Project',
    description: 'Deterministic MVP E2E project',
    createdBy: '11111111-1111-4111-8111-111111111111',
    createdByUser: {
      id: '11111111-1111-4111-8111-111111111111',
      email: 'e2e.admin@vinfast.vn',
      name: 'E2E Admin',
    },
    createdAt: NOW,
    updatedAt: NOW,
    archivedAt: null,
  };
}

function projectSetupStatus(): Record<string, unknown> {
  return {
    projectId: PROJECT_ID,
    workspaceId: WORKSPACE_ID,
    steps: {
      hasDataset: true,
      hasActiveDatasetVersion: true,
      hasActiveChatbotTarget: true,
      hasResponseExtraction: true,
      hasEvaluator: true,
      hasUsableJudgeProvider: true,
    },
    counts: {
      datasets: 1,
      activeDatasetVersions: 1,
      activeChatbotTargets: 2,
      activeChatbotTargetsWithExtraction: 2,
      activeEvaluators: 1,
      usableJudgeProviders: 1,
      usableBaselineProviders: 1,
      comparisonCandidates: 2,
    },
    canRunSingle: true,
    canRunComparison: true,
    blockers: [],
  };
}

function workspaceMembers(state: MvpMockState): Array<Record<string, unknown>> {
  return [
    {
      id: 1,
      workspaceId: WORKSPACE_ID,
      userId: state.user.id,
      user: userSummary(state),
      role: state.workspaceRole,
      status: 'ACTIVE',
      invitedBy: null,
      invitedByUser: null,
      joinedAt: NOW,
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: 2,
      workspaceId: WORKSPACE_ID,
      userId: '20202020-2020-4020-8020-202020202020',
      user: {
        id: '20202020-2020-4020-8020-202020202020',
        email: 'member.e2e@vinfast.vn',
        name: 'Member E2E',
        avatarUrl: null,
      },
      role: 'MEMBER',
      status: 'ACTIVE',
      invitedBy: state.user.id,
      invitedByUser: userSummary(state),
      joinedAt: NOW,
      createdAt: NOW,
      updatedAt: NOW,
    },
  ];
}

function invitedMember(): Record<string, unknown> {
  return {
    id: 3,
    workspaceId: WORKSPACE_ID,
    userId: '30303030-3030-4030-8030-303030303030',
    user: {
      id: '30303030-3030-4030-8030-303030303030',
      email: 'viewer.e2e@vinfast.vn',
      name: 'Viewer E2E',
      avatarUrl: null,
    },
    role: 'VIEWER',
    status: 'INVITED',
    invitedBy: '11111111-1111-4111-8111-111111111111',
    invitedByUser: null,
    joinedAt: null,
    createdAt: NOW,
    updatedAt: NOW,
  };
}

function canManage(role: E2eWorkspaceRole, systemRole: E2eSystemRole): boolean {
  return systemRole === 'PLATFORM_ADMIN' || role === 'OWNER' || role === 'ADMIN';
}

function secret(): Record<string, unknown> {
  return {
    id: SECRET_ID,
    scope: 'WORKSPACE',
    ownerUserId: '11111111-1111-4111-8111-111111111111',
    ownerUser: {
      id: '11111111-1111-4111-8111-111111111111',
      email: 'e2e.admin@vinfast.vn',
      name: 'E2E Admin',
      avatarUrl: null,
    },
    workspaceId: WORKSPACE_ID,
    name: 'E2E Provider Secret',
    maskedPreview: '••••••',
    status: 'ACTIVE',
    createdAt: NOW,
    updatedAt: NOW,
  };
}

function provider(): Record<string, unknown> {
  return {
    id: PROVIDER_ID,
    scope: 'WORKSPACE',
    ownerUserId: '11111111-1111-4111-8111-111111111111',
    ownerUser: {
      id: '11111111-1111-4111-8111-111111111111',
      email: 'e2e.admin@vinfast.vn',
      name: 'E2E Admin',
      avatarUrl: null,
    },
    workspaceId: WORKSPACE_ID,
    name: 'E2E LLM Provider',
    providerType: 'OPENAI_COMPATIBLE',
    secret: {
      id: SECRET_ID,
      scope: 'WORKSPACE',
      ownerUserId: '11111111-1111-4111-8111-111111111111',
      ownerUser: {
        id: '11111111-1111-4111-8111-111111111111',
        email: 'e2e.admin@vinfast.vn',
        name: 'E2E Admin',
        avatarUrl: null,
      },
      workspaceId: WORKSPACE_ID,
      name: 'E2E Provider Secret',
      status: 'ACTIVE',
    },
    baseUrl: 'https://llm.example.test/v1',
    defaultModel: 'e2e-model',
    availableModels: {},
    config: {},
    status: 'ACTIVE',
    createdAt: NOW,
    updatedAt: NOW,
  };
}

function targetA(): Record<string, unknown> {
  return target(TARGET_A_ID, 'E2E Chatbot Target A');
}

function targetB(): Record<string, unknown> {
  return target(TARGET_B_ID, 'E2E Chatbot Target B');
}

function target(id: string, name: string): Record<string, unknown> {
  return {
    id,
    projectId: PROJECT_ID,
    name,
    description: 'E2E target fixture',
    rawCurlSanitized: 'curl https://chatbot.example.test',
    requestConfig: {
      method: 'POST',
      url: 'https://chatbot.example.test',
      headers: {},
      queryParams: {},
      bodyType: 'JSON',
      body: '{"question":"{{question}}"}',
      secretBindings: [],
      datasetMappings: [{ location: 'BODY_JSON', keyOrPath: '$.question', columnName: 'question' }],
    },
    responseExtractionConfig: {
      fields: [{ fieldName: 'answer', source: 'JSON_PATH', path: '$.answer', required: true }],
    },
    latestExampleId: 1,
    status: 'ACTIVE',
    createdBy: '11111111-1111-4111-8111-111111111111',
    createdByUser: null,
    createdAt: NOW,
    updatedAt: NOW,
  };
}

function dataset(): Record<string, unknown> {
  return {
    id: DATASET_ID,
    scope: 'PROJECT',
    projectId: PROJECT_ID,
    currentVersionId: DATASET_VERSION_ID,
    name: 'E2E Dataset',
    description: 'Dataset for MVP E2E',
    createdBy: '11111111-1111-4111-8111-111111111111',
    createdByUser: null,
    createdAt: NOW,
    updatedAt: NOW,
  };
}

function evaluator(): Record<string, unknown> {
  return {
    id: EVALUATOR_ID,
    projectId: PROJECT_ID,
    name: 'E2E Rule Evaluator',
    type: 'RULE_BASED',
    latestVersion: {
      id: EVALUATOR_VERSION_ID,
      version: 1,
      createdAt: NOW,
      createdByUser: null,
    },
    createdBy: '11111111-1111-4111-8111-111111111111',
    createdByUser: null,
    createdAt: NOW,
    updatedAt: NOW,
  };
}

function testRun(state: MvpMockState): Record<string, unknown> {
  return {
    id: TEST_RUN_ID,
    projectId: PROJECT_ID,
    runType: 'COMPARISON',
    datasetId: DATASET_ID,
    datasetVersionId: DATASET_VERSION_ID,
    datasetSchemaId: DATASET_SCHEMA_ID,
    datasetSchemaVersion: 1,
    judgeProviderId: PROVIDER_ID,
    judgeModel: 'e2e-model',
    status: state.testRunStatus,
    progressCompleted: state.testRunStatus === 'COMPLETED' ? 1 : 0,
    progressTotal: 1,
    summaryMetrics: {
      totalTestcases: 1,
      completedTestcases: state.testRunStatus === 'COMPLETED' ? 1 : 0,
      passedCount: state.reviewCreated ? 1 : 0,
      failedCount: state.reviewCreated ? 0 : 1,
      errorCount: 0,
      passRate: state.reviewCreated ? 1 : 0,
      averageScore: state.reviewCreated ? 1 : 0.4,
      averageLatencyMs: 120,
    },
    domainConfigSnapshot: { datasetId: DATASET_ID, targetIds: [TARGET_A_ID, TARGET_B_ID] },
    promptfooConfigSnapshot: { providers: ['e2e'] },
    domainConfigHash: 'domain-e2e-hash',
    promptfooConfigHash: 'promptfoo-e2e-hash',
    targets: [
      {
        id: TEST_RUN_TARGET_A_ID,
        targetType: 'CHATBOT_TARGET',
        chatbotTargetId: TARGET_A_ID,
        providerId: null,
        model: null,
        name: 'E2E Chatbot Target A',
        orderIndex: 0,
        config: {},
      },
      {
        id: TEST_RUN_TARGET_B_ID,
        targetType: 'CHATBOT_TARGET',
        chatbotTargetId: TARGET_B_ID,
        providerId: null,
        model: null,
        name: 'E2E Chatbot Target B',
        orderIndex: 1,
        config: {},
      },
    ],
    evaluators: [
      {
        id: 1,
        evaluatorId: EVALUATOR_ID,
        evaluatorVersionId: EVALUATOR_VERSION_ID,
        evaluatorType: 'RULE_BASED',
        version: 1,
        orderIndex: 0,
        config: {},
        rules: [{ fieldSource: 'response.answer', operator: 'CONTAINS' }],
      },
    ],
    startedBy: '11111111-1111-4111-8111-111111111111',
    startedAt: state.testRunStatus === 'QUEUED' ? null : NOW,
    finishedAt: state.testRunStatus === 'COMPLETED' ? NOW : null,
    cancelRequestedAt: null,
    createdAt: NOW,
    updatedAt: NOW,
  };
}

function testRunSummary(state: MvpMockState): Record<string, unknown> {
  const detail = testRun(state);
  return {
    id: detail.id,
    projectId: detail.projectId,
    runType: detail.runType,
    datasetId: detail.datasetId,
    datasetVersionId: detail.datasetVersionId,
    status: detail.status,
    progressCompleted: detail.progressCompleted,
    progressTotal: detail.progressTotal,
    summaryMetrics: detail.summaryMetrics,
    startedBy: detail.startedBy,
    startedAt: detail.startedAt,
    finishedAt: detail.finishedAt,
    createdAt: detail.createdAt,
    updatedAt: detail.updatedAt,
  };
}

function resultPage(state: MvpMockState): Record<string, unknown> {
  return collection([resultA(state), resultB(state)]);
}

function resultA(state: MvpMockState): Record<string, unknown> {
  return {
    id: 9001,
    testRunId: TEST_RUN_ID,
    testRunTargetId: TEST_RUN_TARGET_A_ID,
    datasetRowId: 501,
    machineStatus: 'FAILED',
    machineScore: 0.4,
    machinePassed: false,
    finalStatus: state.reviewCreated ? 'PASSED' : 'FAILED',
    finalScore: state.reviewCreated ? 0.96 : 0.4,
    finalPassed: state.reviewCreated,
    errorCategory: null,
    request: { question: 'E2E question' },
    response: { answer: 'E2E answer' },
    extractedOutput: { answer: 'E2E answer' },
    gradingResult: { reason: 'Rule matched after QC review' },
    componentResults: {},
    tokenUsage: {},
    latencyMs: 120,
    machineSummary: 'Machine marked the answer as failed.',
    review: state.reviewCreated ? review() : null,
    createdAt: NOW,
    updatedAt: NOW,
  };
}

function resultB(state: MvpMockState): Record<string, unknown> {
  return {
    ...resultA(state),
    id: 9002,
    testRunTargetId: TEST_RUN_TARGET_B_ID,
    machineStatus: 'PASSED',
    machineScore: 0.92,
    machinePassed: true,
    finalStatus: 'PASSED',
    finalScore: 0.92,
    finalPassed: true,
    review: null,
  };
}

function review(): Record<string, unknown> {
  return {
    id: 7001,
    testCaseResultId: 9001,
    reviewerId: '11111111-1111-4111-8111-111111111111',
    reviewStatus: 'OVERRIDDEN',
    overrideStatus: 'PASSED',
    overrideScore: 0.96,
    note: 'E2E QC review',
    createdAt: NOW,
    updatedAt: NOW,
  };
}

function comparisonMatrix(state: MvpMockState): Record<string, unknown> {
  return {
    columns: [
      { testRunTargetId: TEST_RUN_TARGET_A_ID, name: 'E2E Chatbot Target A', orderIndex: 0 },
      { testRunTargetId: TEST_RUN_TARGET_B_ID, name: 'E2E Chatbot Target B', orderIndex: 1 },
    ],
    rows: [
      {
        datasetRowId: 501,
        rowIndex: 0,
        cells: [
          {
            testRunTargetId: TEST_RUN_TARGET_A_ID,
            resultId: 9001,
            finalStatus: state.reviewCreated ? 'PASSED' : 'FAILED',
            finalScore: state.reviewCreated ? 0.96 : 0.4,
            finalPassed: state.reviewCreated,
            errorCategory: null,
            reviewed: state.reviewCreated,
          },
          {
            testRunTargetId: TEST_RUN_TARGET_B_ID,
            resultId: 9002,
            finalStatus: 'PASSED',
            finalScore: 0.92,
            finalPassed: true,
            errorCategory: null,
            reviewed: false,
          },
        ],
      },
    ],
    page: { limit: 20, nextCursor: null, hasMore: false },
  };
}

function exportJob(state: MvpMockState): Record<string, unknown> {
  return {
    id: EXPORT_JOB_ID,
    jobType: 'TEST_RUN_RESULTS',
    format: 'XLSX',
    status: state.exportCreated ? 'COMPLETED' : 'PENDING',
    datasetVersionId: null,
    testRunId: TEST_RUN_ID,
    resultMode: 'FINAL',
    fileName: 'e2e-test-run-final.xlsx',
    fileSizeBytes: 128,
    errorCode: null,
    errorMessage: null,
    createdBy: '11111111-1111-4111-8111-111111111111',
    createdAt: NOW,
    startedAt: NOW,
    finishedAt: NOW,
    expiresAt: '2026-07-15T09:00:00+07:00',
  };
}
